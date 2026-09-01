package net.pieroxy.imf.reputation;

import java.util.List;

/**
 * Recherche linéaire dans les blocs CIDR de la liste : les listes de réputation publiques (ex:
 * Spamhaus DROP/EDROP) comptent typiquement de quelques centaines à quelques milliers de blocs,
 * largement assez peu pour une recherche linéaire par message (pas de structure d'index à
 * maintenir, et correcte même si des blocs se chevauchent).
 */
final class IpReputationList implements ReputationList {
  private final List<CidrRange> ranges;

  IpReputationList(List<CidrRange> ranges) {
    this.ranges = ranges;
  }

  @Override
  public boolean contains(String value) {
    long ip;
    try {
      ip = CidrRange.ipToLong(value);
    } catch (IllegalArgumentException e) {
      return false; // pas une IPv4 (ex: IPv6, non supporté) : ne matche jamais une liste IP_CIDR
    }
    for (CidrRange range : ranges) {
      if (range.contains(ip)) return true;
    }
    return false;
  }

  int size() {
    return ranges.size();
  }
}
