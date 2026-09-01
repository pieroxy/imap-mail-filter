package net.pieroxy.imf.reputation;

/** Une liste chargée et prête à interroger — voir {@link IpReputationList}/{@link DomainReputationList}. */
interface ReputationList {
  boolean contains(String value);
}
