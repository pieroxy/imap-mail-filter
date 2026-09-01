package net.pieroxy.imf.reputation;

/**
 * Trie binaire pour des plages CIDR IPv4 : un noeud par bit, du poids fort au poids faible (32
 * niveaux maximum), pour un lookup borné à 32 comparaisons quel que soit le nombre de plages
 * enregistrées — contrairement à {@link IpReputationList}, qui compare l'IP testée à
 * <em>chaque</em> plage de la liste (O(n)). Voir {@code IpTrieBenchmark} pour la mesure
 * CPU/mémoire réelle des deux approches sur un vrai jeu de données (Spamhaus DROP).
 * <p>
 * Deux enfants possibles par noeud seulement (bit à 0 ou à 1) : pas besoin de dichotomie ni de
 * table de hachage comme pour {@link StringTree}, deux références directes suffisent et sont
 * déjà optimales.
 * <p>
 * Un noeud atteint pendant la descente et marqué "terminal" signifie : tous les bits de poids
 * fort parcourus jusqu'ici correspondent à un bloc CIDR enregistré, donc toute IP passant par ce
 * noeud est couverte, quels que soient ses bits restants — inutile de descendre plus loin. C'est
 * exactement la sémantique CIDR (un préfixe plus court couvre un espace plus large), et ça reste
 * correct quel que soit l'ordre d'insertion : un bloc large ajouté après un bloc plus étroit
 * qu'il contient étend bien la couverture (voir {@code IpTrieTest}).
 * <p>
 * IPv4 seulement pour l'instant, comme {@link CidrRange} — le même principe s'étend directement
 * à IPv6 avec 128 niveaux au lieu de 32.
 */
public class IpTrie {
  private final Node root = new Node();

  private static final class Node {
    private Node zero;
    private Node one;
    private boolean terminal;
  }

  /** Enregistre le bloc CIDR dont les prefixLength bits de poids fort de ip forment le préfixe. */
  public void add(long ip, int prefixLength) {
    Node node = root;
    for (int i = 0; i < prefixLength; i++) {
      if (node.terminal) return; // déjà couvert par un bloc plus large enregistré avant : rien à ajouter
      boolean bit = ((ip >>> (31 - i)) & 1L) != 0;
      Node next = bit ? node.one : node.zero;
      if (next == null) {
        next = new Node();
        if (bit) {
          node.one = next;
        } else {
          node.zero = next;
        }
      }
      node = next;
    }
    node.terminal = true;
  }

  public boolean contains(long ip) {
    Node node = root;
    for (int i = 0; i < 32; i++) {
      if (node.terminal) return true;
      boolean bit = ((ip >>> (31 - i)) & 1L) != 0;
      node = bit ? node.one : node.zero;
      if (node == null) return false;
    }
    return node.terminal;
  }
}
