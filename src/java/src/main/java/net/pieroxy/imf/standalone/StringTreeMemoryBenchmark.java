package net.pieroxy.imf.standalone;

import net.jpountz.lz4.LZ4FrameInputStream;
import net.pieroxy.imf.reputation.StringTree;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Banc d'essai mémoire hors process principal : charge un fichier lz4-compressé (comme
 * {@code dataFolder/reputation/<id>.txt.lz4}) dans un {@code HashSet<String>} classique, mesure
 * l'empreinte mémoire, puis fait pareil avec {@link StringTree} et compare — pour juger si un
 * arbre de caractères vaut le coup sur une liste de réputation donnée avant de l'adopter en
 * production. Usage :
 * <pre>java -cp imf-core-*.jar net.pieroxy.imf.standalone.StringTreeMemoryBenchmark &lt;fichier.txt.lz4&gt;</pre>
 */
public final class StringTreeMemoryBenchmark {
  private StringTreeMemoryBenchmark() {}

  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      System.err.println("Usage: StringTreeMemoryBenchmark <file.txt.lz4>");
      System.exit(1);
    }

    List<String> lines = readLines(new File(args[0]));
    System.out.println(lines.size() + " line(s) read from " + args[0]);

    long baseline = usedMemory();
    System.out.println("Baseline (before loading anything): " + format(baseline));

    Set<String> flatSet = new HashSet<>(lines);
    lines = null; // ne doit pas fausser la mesure de flatSet lui-même
    long afterFlatSet = usedMemory();
    System.out.println("HashSet<String> (" + flatSet.size() + " entries): "
        + format(afterFlatSet - baseline) + " above baseline (" + format(afterFlatSet) + " total)");

    StringTree tree = new StringTree();
    tree.addAll(flatSet);
    flatSet = null; // discarde le Set<String> d'origine avant de re-mesurer
    long afterTree = usedMemory();
    System.out.println("StringTree (" + tree.size() + " entries), HashSet discarded: "
        + format(afterTree - baseline) + " above baseline (" + format(afterTree) + " total)");
  }

  private static List<String> readLines(File file) throws IOException {
    List<String> lines = new ArrayList<>();
    try (Reader r = new InputStreamReader(new LZ4FrameInputStream(new FileInputStream(file)), StandardCharsets.UTF_8)) {
      StringBuilder sb = new StringBuilder();
      char[] buf = new char[8192];
      int n;
      while ((n = r.read(buf)) != -1) {
        sb.append(buf, 0, n);
      }
      for (String line : sb.toString().split("\\r?\\n")) {
        String trimmed = line.trim();
        if (!trimmed.isEmpty()) {
          lines.add(trimmed);
        }
      }
    }
    return lines;
  }

  /** Force le GC (deux passes, avec une courte pause pour le laisser vraiment finir) puis renvoie la mémoire heap utilisée. */
  private static long usedMemory() {
    Runtime rt = Runtime.getRuntime();
    for (int i = 0; i < 2; i++) {
      System.gc();
      try {
        Thread.sleep(200);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    return rt.totalMemory() - rt.freeMemory();
  }

  private static String format(long bytes) {
    return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
  }
}
