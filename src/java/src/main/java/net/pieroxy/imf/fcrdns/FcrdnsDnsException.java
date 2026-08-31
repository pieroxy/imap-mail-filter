package net.pieroxy.imf.fcrdns;

/**
 * Échec temporaire de résolution DNS (timeout, SERVFAIL...), qu'il s'agisse du lookup PTR ou
 * du lookup forward (A/AAAA) de confirmation. Se traduit par {@link FcrdnsResult#TEMPERROR}.
 */
public class FcrdnsDnsException extends Exception {
  public FcrdnsDnsException(String message) {
    super(message);
  }

  public FcrdnsDnsException(String message, Throwable cause) {
    super(message, cause);
  }
}
