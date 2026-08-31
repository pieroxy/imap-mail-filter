package net.pieroxy.imf.dmarc;

/**
 * Échec temporaire de résolution DNS (timeout, SERVFAIL...) lors de la recherche d'un record
 * DMARC. Se traduit par {@link DmarcResult#TEMPERROR} dans {@link DmarcEvaluator}.
 */
public class DmarcDnsException extends Exception {
  public DmarcDnsException(String message) {
    super(message);
  }

  public DmarcDnsException(String message, Throwable cause) {
    super(message, cause);
  }
}
