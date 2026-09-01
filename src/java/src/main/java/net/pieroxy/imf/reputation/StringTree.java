package net.pieroxy.imf.reputation;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * {@code Set<String>} represented as a radix tree (compressed prefix tree) on **reversed**
 * strings — see {@code StringTreeMemoryBenchmark} for the full story:
 * <ol>
 *   <li>A naive one-character-per-node trie cost 43x more memory than a plain {@code HashSet}
 *       on a real domain list (domains rarely share a prefix, but often share a suffix — same
 *       TLD, same parent domain).</li>
 *   <li>Reversing the strings + compressing paths (a node with no branching carries several
 *       characters at once — the edge to it is a string, not a character, as long as no other
 *       word forces a split) brought that down to 3.75x.</li>
 *   <li>Replacing each node's {@code HashMap<Character,Node>} with two sorted arrays
 *       ({@code char[]}/{@code Node[]}, binary search) brought that down to 2x.</li>
 *   <li>This file: a node's children keys are no longer {@code char} (just the first character
 *       of an edge) but full {@code String}s — the whole edge. A node therefore no longer needs
 *       its own {@code label} field: that string lives only in the parent's {@code String[] keys}.
 *       A completely isolated word (no other word shares its reversed suffix) therefore costs
 *       only a single {@code (String, Node)} entry in the parent — the {@code Node} itself now
 *       carries only a boolean and two shared empty arrays.</li>
 * </ol>
 * The binary search ({@link Node#indexOf}) only compares the first character of each key — keys
 * within the same node always have distinct first characters by construction (otherwise they
 * would have been split), so that's enough to tell them apart.
 * <p>
 * {@link #contains} stays O(length of the tested string). Insertion only: {@link #remove} isn't
 * supported (inherited from {@link AbstractSet}) — a reputation list is entirely rebuilt on
 * every refresh rather than modified in place, see {@code ReputationListParser}.
 */
public class StringTree extends AbstractSet<String> {
  private final Node root = new Node();
  private int size;

  private static final class Node {
    private static final String[] NO_KEYS = new String[0];
    private static final Node[] NO_CHILDREN = new Node[0];

    private String[] keys = NO_KEYS;
    private Node[] children = NO_CHILDREN;
    private boolean terminal;

    /** Index of the key whose first character is c, or -(insertion point)-1 — same contract as Arrays.binarySearch. */
    int indexOf(char c) {
      int lo = 0;
      int hi = keys.length - 1;
      while (lo <= hi) {
        int mid = (lo + hi) >>> 1;
        char midChar = keys[mid].charAt(0);
        if (midChar < c) {
          lo = mid + 1;
        } else if (midChar > c) {
          hi = mid - 1;
        } else {
          return mid;
        }
      }
      return -(lo + 1);
    }

    /** at must be a valid insertion point (see indexOf) for a key that isn't present yet. */
    void insertAt(int at, String key, Node value) {
      String[] newKeys = new String[keys.length + 1];
      Node[] newChildren = new Node[children.length + 1];
      System.arraycopy(keys, 0, newKeys, 0, at);
      System.arraycopy(children, 0, newChildren, 0, at);
      newKeys[at] = key;
      newChildren[at] = value;
      System.arraycopy(keys, at, newKeys, at + 1, keys.length - at);
      System.arraycopy(children, at, newChildren, at + 1, children.length - at);
      keys = newKeys;
      children = newChildren;
    }
  }

  @Override
  public boolean add(String value) {
    if (value == null) throw new NullPointerException();
    boolean added = insert(root, reverse(value));
    if (added) size++;
    return added;
  }

  /** @return true if a new entry was created (false if already present). suffix = what's left to insert under node. */
  private boolean insert(Node node, String suffix) {
    if (suffix.isEmpty()) {
      boolean wasNew = !node.terminal;
      node.terminal = true;
      return wasNew;
    }

    int idx = node.indexOf(suffix.charAt(0));
    if (idx < 0) {
      Node leaf = new Node();
      leaf.terminal = true;
      node.insertAt(-idx - 1, suffix, leaf);
      return true;
    }

    String key = node.keys[idx];
    Node child = node.children[idx];
    int lcp = commonPrefixLength(suffix, key);
    if (lcp == key.length()) {
      // The key matches in full: keep going deeper with the rest.
      return insert(child, suffix.substring(lcp));
    }

    // Divergence partway through the key: split the existing edge in two — an intermediate
    // node (the common prefix) whose sole child is the old node (under a key shortened by the
    // consumed prefix), which keeps its whole existing subtree as-is.
    Node splitOff = child;
    String splitOffKey = key.substring(lcp);
    Node mid = new Node();
    mid.insertAt(0, splitOffKey, splitOff);

    node.keys[idx] = suffix.substring(0, lcp);
    node.children[idx] = mid;

    String remaining = suffix.substring(lcp);
    if (remaining.isEmpty()) {
      mid.terminal = true;
    } else {
      Node leaf = new Node();
      leaf.terminal = true;
      int midIdx = mid.indexOf(remaining.charAt(0));
      mid.insertAt(-midIdx - 1, remaining, leaf);
    }
    return true;
  }

  @Override
  public boolean contains(Object o) {
    if (!(o instanceof String value)) return false;
    return lookup(root, reverse(value));
  }

  private boolean lookup(Node node, String suffix) {
    if (suffix.isEmpty()) return node.terminal;
    int idx = node.indexOf(suffix.charAt(0));
    if (idx < 0) return false;
    String key = node.keys[idx];
    if (!suffix.startsWith(key)) return false;
    return lookup(node.children[idx], suffix.substring(key.length()));
  }

  @Override
  public int size() {
    return size;
  }

  /** Full walk of the tree — expensive, never used on the hot path ({@link #contains}). */
  @Override
  public Iterator<String> iterator() {
    List<String> all = new ArrayList<>(size);
    collect(root, new StringBuilder(), all);
    return all.iterator();
  }

  private static void collect(Node node, StringBuilder prefix, List<String> out) {
    if (node.terminal) {
      out.add(reverse(prefix.toString()));
    }
    for (int i = 0; i < node.keys.length; i++) {
      String key = node.keys[i];
      prefix.append(key);
      collect(node.children[i], prefix, out);
      prefix.setLength(prefix.length() - key.length());
    }
  }

  private static int commonPrefixLength(String a, String b) {
    int max = Math.min(a.length(), b.length());
    int i = 0;
    while (i < max && a.charAt(i) == b.charAt(i)) i++;
    return i;
  }

  private static String reverse(String s) {
    return new StringBuilder(s).reverse().toString();
  }
}
