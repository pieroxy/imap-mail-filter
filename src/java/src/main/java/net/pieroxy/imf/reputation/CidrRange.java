package net.pieroxy.imf.reputation;

/**
 * Un bloc IPv4/CIDR ("1.2.3.0/24", ou "1.2.3.4" traité comme un /32), comme on en trouve dans
 * les listes de type {@link ReputationListType#IP_CIDR}. IPv6 non supporté pour l'instant — une
 * adresse IPv6 ne matchera jamais aucun bloc ici (voir {@link IpReputationList}).
 */
final class CidrRange {
  private final long start;
  private final long end;

  private CidrRange(long start, long end) {
    this.start = start;
    this.end = end;
  }

  static CidrRange parse(String text) {
    int slash = text.indexOf('/');
    String ipPart = slash < 0 ? text : text.substring(0, slash);
    int prefixLength = slash < 0 ? 32 : Integer.parseInt(text.substring(slash + 1));
    if (prefixLength < 0 || prefixLength > 32) {
      throw new IllegalArgumentException("Invalid IPv4 prefix length: " + text);
    }
    long ip = ipToLong(ipPart);
    long mask = prefixLength == 0 ? 0 : (0xFFFFFFFFL << (32 - prefixLength)) & 0xFFFFFFFFL;
    long start = ip & mask;
    long end = start | (~mask & 0xFFFFFFFFL);
    return new CidrRange(start, end);
  }

  boolean contains(long ip) {
    return ip >= start && ip <= end;
  }

  static long ipToLong(String ip) {
    String[] parts = ip.split("\\.", -1);
    if (parts.length != 4) {
      throw new IllegalArgumentException("Not an IPv4 address: " + ip);
    }
    long result = 0;
    for (String part : parts) {
      int octet;
      try {
        octet = Integer.parseInt(part);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Not an IPv4 address: " + ip);
      }
      if (octet < 0 || octet > 255) {
        throw new IllegalArgumentException("Not an IPv4 address: " + ip);
      }
      result = (result << 8) | octet;
    }
    return result;
  }
}
