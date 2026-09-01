package net.pieroxy.imf.reputation;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * {@code Set<String>} représenté par un arbre radix (préfixe compressé) sur les chaînes
 * **inversées** — voir {@code StringTreeMemoryBenchmark} pour la démarche complète :
 * <ol>
 *   <li>Un trie naïf à un caractère par noeud coûtait 43x plus de mémoire qu'un simple
 *       {@code HashSet} sur une vraie liste de domaines (les domaines partagent rarement un
 *       préfixe, mais partagent souvent un suffixe — même TLD, même domaine parent).</li>
 *   <li>Inverser les chaînes + compresser les chemins (un noeud sans embranchement porte
 *       plusieurs caractères d'un coup — l'arête vers lui est une chaîne, pas un caractère,
 *       tant qu'aucun autre mot ne force à la scinder) a ramené ça à 3,75x.</li>
 *   <li>Remplacer le {@code HashMap<Character,Node>} de chaque noeud par deux tableaux triés
 *       ({@code char[]}/{@code Node[]}, dichotomie) a ramené ça à 2x.</li>
 *   <li>Ce fichier : les clés des enfants d'un noeud ne sont plus des {@code char} (juste le
 *       premier caractère d'une arête) mais des {@code String} complètes — l'arête entière.
 *       Un noeud n'a donc plus besoin de son propre champ {@code label} : cette chaîne vit
 *       uniquement dans le {@code String[] keys} du parent. Un mot totalement isolé (aucun
 *       autre mot ne partage son suffixe inversé) ne coûte donc qu'une seule entrée
 *       {@code (String, Node)} dans le parent — le {@code Node} lui-même ne porte plus qu'un
 *       booléen et deux tableaux vides partagés.</li>
 * </ol>
 * La dichotomie ({@link Node#indexOf}) ne compare que le premier caractère de chaque clé — les
 * clés d'un même noeud ont toujours des premiers caractères distincts par construction (sinon
 * elles auraient été scindées), donc c'est suffisant pour les départager.
 * <p>
 * {@link #contains} reste O(longueur de la chaîne testée). Insertion seulement : {@link #remove}
 * n'est pas supporté (hérité de {@link AbstractSet}) — une liste de réputation est entièrement
 * reconstruite à chaque refresh plutôt que modifiée en place, voir {@code ReputationListParser}.
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

    /** Index de la clé dont le premier caractère est c, ou -(point d'insertion)-1 — même contrat qu'Arrays.binarySearch. */
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

    /** at doit être un point d'insertion valide (voir indexOf) pour une clé pas encore présente. */
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

  /** @return true si une entrée nouvelle a été créée (false si déjà présente). suffix = ce qu'il reste à insérer sous node. */
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
      // La clé matche entièrement : on continue en profondeur avec le reste.
      return insert(child, suffix.substring(lcp));
    }

    // Divergence en cours de clé : on scinde l'arête existante en deux — un noeud
    // intermédiaire (le préfixe commun) dont l'unique enfant est l'ancien noeud (sous une clé
    // raccourcie du préfixe consommé), qui garde tel quel tout son sous-arbre existant.
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

  /** Parcours complet de l'arbre — coûteux, jamais utilisé sur le chemin chaud ({@link #contains}). */
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
