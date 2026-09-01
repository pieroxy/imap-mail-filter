package net.pieroxy.imf.reputation;

import java.util.List;

/**
 * Stockée sous forme d'{@link IpTrie} (un noeud par bit, lookup borné à 32 comparaisons) plutôt
 * qu'une recherche linéaire dans les blocs CIDR : mesuré sur de vraies listes de réputation
 * (Spamhaus DROP, 1708 blocs ; FireHOL blocklist_de_mail, 12341 blocs), le trie est 37x à 225x
 * plus rapide au lookup qu'un parcours de la liste — l'écart croît avec la taille de la liste,
 * puisque le parcours linéaire est O(n) alors que le trie reste O(32) — pour un coût mémoire
 * négligeable (quelques Mo). Voir {@code net.pieroxy.imf.standalone.IpTrieBenchmark}.
 */
final class IpReputationList implements ReputationList {
  private final IpTrie trie;

  IpReputationList(List<CidrRange> ranges) {
    this.trie = new IpTrie();
    for (CidrRange range : ranges) {
      trie.add(range.start(), range.prefixLength());
    }
  }

  @Override
  public boolean contains(String value) {
    long ip;
    try {
      ip = CidrRange.ipToLong(value);
    } catch (IllegalArgumentException e) {
      return false; // pas une IPv4 (ex: IPv6, non supporté) : ne matche jamais une liste IP_CIDR
    }
    return trie.contains(ip);
  }
}
