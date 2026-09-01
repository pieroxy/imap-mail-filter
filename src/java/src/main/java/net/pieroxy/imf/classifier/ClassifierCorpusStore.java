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
import java.util.List;
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
   * pruneOlderThan a été appelé avant), tous fichiers journaliers confondus. Utilisé par
   * {@link SubjectClassifierTrainer} pour (ré)entraîner sur tout ce qui est retenu.
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
    return all;
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
