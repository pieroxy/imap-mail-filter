package net.pieroxy.imf.reputation;

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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persiste le contenu brut (texte) de chaque liste, compressé lz4, un fichier par id — même
 * pattern que {@code ClassifierCorpusStore}/{@code LogRotator} : écriture atomique (fichier
 * temporaire puis renommage) pour ne jamais laisser un cache tronqué si le process est
 * interrompu en pleine sauvegarde. Sert de repli quand un refresh échoue : la dernière version
 * qui a marché reste utilisable indéfiniment plutôt que de perdre le signal.
 */
final class ReputationListStore {
  private static final Logger LOGGER = Logger.getLogger(ReputationListStore.class.getName());

  private final File folder;

  ReputationListStore(String dataFolder) {
    this.folder = new File(dataFolder, "reputation");
  }

  /** @return le contenu mis en cache, ou null si aucun cache n'existe ou n'a pu être lu. */
  String load(String id) {
    File file = fileFor(id);
    if (!file.isFile()) return null;
    try (Reader r = new InputStreamReader(new LZ4FrameInputStream(new FileInputStream(file)), StandardCharsets.UTF_8)) {
      StringBuilder sb = new StringBuilder();
      char[] buf = new char[8192];
      int n;
      while ((n = r.read(buf)) != -1) {
        sb.append(buf, 0, n);
      }
      return sb.toString();
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Reputation list [" + id + "]: could not read cached copy " + file, e);
      return null;
    }
  }

  void save(String id, String content) throws IOException {
    folder.mkdirs();
    File file = fileFor(id);
    File tmp = new File(folder, file.getName() + ".tmp");
    try (Writer w = new OutputStreamWriter(new LZ4FrameOutputStream(new FileOutputStream(tmp)), StandardCharsets.UTF_8)) {
      w.write(content);
    }
    Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  }

  private File fileFor(String id) {
    return new File(folder, id + ".txt.lz4");
  }
}
