package net.pieroxy.imf.logging;

import net.jpountz.lz4.LZ4FrameOutputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Décale les archives existantes (name.N.lz4 -> name.(N+1).lz4), supprime celles qui
 * dépassent keepLogFiles, puis compresse le fichier de log courant en name.1.lz4 et le
 * supprime. N'a aucune dépendance sur java.util.logging, ce qui la rend testable sur de
 * simples fichiers ; LoggingBootstrap se charge de fermer/rouvrir le FileHandler autour
 * de cet appel.
 */
public final class LogRotator {
  private LogRotator() {}

  public static void rotate(String logFile, int keepLogFiles) throws IOException {
    if (keepLogFiles <= 0) return;
    File current = new File(logFile);
    if (!current.isFile()) return;

    for (int i = keepLogFiles - 1; i >= 1; i--) {
      File src = archive(logFile, i);
      if (src.isFile()) {
        Files.move(src.toPath(), archive(logFile, i + 1).toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
    }
    for (int i = keepLogFiles + 1; archive(logFile, i).isFile(); i++) {
      Files.delete(archive(logFile, i).toPath());
    }

    compress(current, archive(logFile, 1));
    Files.delete(current.toPath());
  }

  private static File archive(String logFile, int index) {
    return new File(logFile + "." + index + ".lz4");
  }

  private static void compress(File source, File destination) throws IOException {
    // Compresse vers un fichier temporaire puis renomme atomiquement : une interruption
    // brutale (JVM tuée, thread daemon coupé net) pendant l'écriture laisse au pire un
    // .tmp orphelin, jamais une archive .lz4 tronquée sous son nom définitif.
    File tmp = new File(destination.getParentFile(), destination.getName() + ".tmp");
    try (InputStream in = new BufferedInputStream(new FileInputStream(source));
        OutputStream out =
            new LZ4FrameOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))) {
      in.transferTo(out);
    }
    Files.move(
        tmp.toPath(),
        destination.toPath(),
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE);
  }
}
