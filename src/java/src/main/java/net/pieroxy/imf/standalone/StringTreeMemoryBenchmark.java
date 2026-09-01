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
 * Memory benchmark outside the main process: loads an lz4-compressed file (like
 * {@code dataFolder/reputation/<id>.txt.lz4}) into a plain {@code HashSet<String>}, measures the
 * memory footprint, then does the same with {@link StringTree} and compares — to judge whether a
 * character tree is worth it for a given reputation list before adopting it in production.
 * Usage:
 * <pre>java -cp imf-core-*.jar net.pieroxy.imf.standalone.StringTreeMemoryBenchmark &lt;file.txt.lz4&gt;</pre>
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
    lines = null; // must not skew the measurement of flatSet itself
    long afterFlatSet = usedMemory();
    System.out.println("HashSet<String> (" + flatSet.size() + " entries): "
        + format(afterFlatSet - baseline) + " above baseline (" + format(afterFlatSet) + " total)");

    StringTree tree = new StringTree();
    tree.addAll(flatSet);
    flatSet = null; // discard the original Set<String> before re-measuring
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

  /** Forces the GC (two passes, with a short pause to let it actually finish) then returns the heap memory used. */
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
