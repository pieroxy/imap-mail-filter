package net.pieroxy.imf.classifier;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import net.jpountz.lz4.LZ4FrameInputStream;
import net.jpountz.lz4.LZ4FrameOutputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stocke le corpus classifieur dans un fichier JSON compressé lz4 par jour
 * (classifier-YYYY-MM-DD.json.lz4), un dossier par compte. Fusionne avec le fichier du jour
 * s'il existe déjà (reprise après interruption en cours de journée) et écrit atomiquement
 * (fichier temporaire puis renommage), comme {@code LogRotator}, pour ne jamais laisser une
 * archive tronquée en cas de coupure brutale. keepDays&lt;=0 désactive la purge des archives
 * trop anciennes.
 */
public class ClassifierCorpusStore {
  private final static Logger LOGGER = Logger.getLogger(ClassifierCorpusStore.class.getName());
  private final static Gson GSON = new Gson();
  private final static Type LIST_TYPE = new TypeToken<List<ClassifierExample>>() {}.getType();
  private final static Pattern FILE_DATE_PATTERN = Pattern.compile("classifier-(\\d{4}-\\d{2}-\\d{2})\\.json\\.lz4");

  private final File accountFolder;
  private final int keepDays;
  private final String logPrefix;

  public ClassifierCorpusStore(String dataFolder, String accountKey, int keepDays) {
    this.accountFolder = new File(new File(dataFolder, "classifier-corpus"), accountKey);
    this.keepDays = keepDays;
    this.logPrefix = "Classifier corpus [" + accountKey + "] ";
  }

  public void append(LocalDate day, List<ClassifierExample> newExamples) throws IOException {
    if (newExamples.isEmpty()) return;
    accountFolder.mkdirs();
    File file = fileFor(day);
    List<ClassifierExample> all = new ArrayList<>(read(file));
    all.addAll(newExamples);
    write(file, all);
  }

  /**
   * Tous les exemples actuellement conservés (donc déjà dans la fenêtre de rétention si
   * pruneOlderThan a été appelé avant), tous fichiers journaliers confondus, dédupliqués par
   * Message-ID (voir {@link #dedupeByMessageId}). Utilisé par {@link SubjectClassifierTrainer}
   * pour (ré)entraîner sur tout ce qui est retenu.
   */
  public List<ClassifierExample> readAll() {
    File[] files = accountFolder.listFiles();
    if (files == null) return List.of();
    List<ClassifierExample> all = new ArrayList<>();
    for (File f : files) {
      if (FILE_DATE_PATTERN.matcher(f.getName()).matches()) {
        all.addAll(read(f));
      }
    }
    return dedupeByMessageId(all);
  }

  /**
   * Un même message peut être capturé deux fois avec des labels contradictoires : auto-classé
   * SPAM au scan (rapide, chaque cycle) du dossier Spam, puis déplacé à la main par
   * l'utilisateur qui le juge légitime — vu à nouveau, cette fois HAM, par le scan quotidien
   * complet qui suit. Un déplacement IMAP donne un nouvel UID au message dans le dossier de
   * destination (le suivi par UID par dossier ne voit donc pas "le même message"), donc rien
   * n'empêche cette double capture en amont — la corriger ici, à la lecture, est plus simple
   * que de la prévenir au scan (pas besoin de relire tout le corpus existant à chaque append).
   * Pour un Message-ID vu plusieurs fois, seul l'exemple au fetchDate le plus récent (donc le
   * verdict le plus à jour, celui qui reflète où l'utilisateur a fini par ranger le message) est
   * gardé — les deux se neutraliseraient sinon au lieu d'enseigner quoi que ce soit. Les
   * exemples sans Message-ID (rare, mail malformé) ne peuvent pas être rapprochés de façon
   * fiable : gardés tels quels, jamais dédupliqués entre eux.
   */
  private static List<ClassifierExample> dedupeByMessageId(List<ClassifierExample> examples) {
    Map<String, ClassifierExample> latestById = new LinkedHashMap<>();
    List<ClassifierExample> withoutId = new ArrayList<>();
    for (ClassifierExample example : examples) {
      String id = example.getMessageId();
      if (id == null) {
        withoutId.add(example);
        continue;
      }
      ClassifierExample existing = latestById.get(id);
      if (existing == null || isAfter(example.getFetchDate(), existing.getFetchDate())) {
        latestById.put(id, example);
      }
    }
    List<ClassifierExample> result = new ArrayList<>(latestById.values());
    result.addAll(withoutId);
    return result;
  }

  /** fetchDate est formaté en ISO-8601 UTC (DateTimeFormatter.ISO_INSTANT) : comparable lexicographiquement. */
  private static boolean isAfter(String a, String b) {
    if (a == null) return false;
    if (b == null) return true;
    return a.compareTo(b) > 0;
  }

  /** Chemin du modèle de classification de sujet entraîné pour ce compte. */
  public File getModelFile() {
    return new File(accountFolder, "subject-model.bin");
  }

  /** Supprime les fichiers datés de plus de keepDays jours avant today. Sans effet si keepDays<=0. */
  public void pruneOlderThan(LocalDate today) {
    if (keepDays <= 0) return;
    File[] files = accountFolder.listFiles();
    if (files == null) return;
    LocalDate cutoff = today.minusDays(keepDays);
    for (File f : files) {
      Matcher m = FILE_DATE_PATTERN.matcher(f.getName());
      if (!m.matches()) continue;
      LocalDate fileDate = LocalDate.parse(m.group(1));
      if (fileDate.isBefore(cutoff) && !f.delete()) {
        LOGGER.warning(logPrefix + "Could not delete stale corpus file " + f);
      }
    }
  }

  private File fileFor(LocalDate day) {
    return new File(accountFolder, "classifier-" + day + ".json.lz4");
  }

  private List<ClassifierExample> read(File file) {
    if (!file.isFile()) return List.of();
    try (Reader r = new InputStreamReader(new LZ4FrameInputStream(new FileInputStream(file)))) {
      List<ClassifierExample> examples = GSON.fromJson(r, LIST_TYPE);
      return examples != null ? examples : List.of();
    } catch (IOException | JsonParseException e) {
      LOGGER.log(Level.WARNING, logPrefix + "Could not read corpus file " + file, e);
      return List.of();
    }
  }

  private void write(File file, List<ClassifierExample> examples) throws IOException {
    File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
    try (Writer w = new OutputStreamWriter(new LZ4FrameOutputStream(new FileOutputStream(tmp)))) {
      GSON.toJson(examples, LIST_TYPE, w);
    }
    Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  }
}
