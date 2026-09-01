package net.pieroxy.imf.reputation;

import java.util.Locale;
import java.util.Set;

/** Correspondance exacte, insensible à la casse — pas de repli sur le domaine parent. */
final class DomainReputationList implements ReputationList {
  private final Set<String> domains;

  DomainReputationList(Set<String> domains) {
    this.domains = domains;
  }

  @Override
  public boolean contains(String value) {
    return value != null && domains.contains(value.toLowerCase(Locale.ROOT));
  }

  int size() {
    return domains.size();
  }
}
