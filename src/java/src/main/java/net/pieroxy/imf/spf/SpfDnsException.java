package net.pieroxy.imf.spf;

/**
 * Échec temporaire de résolution DNS (timeout, SERVFAIL...), distinct d'une réponse "pas
 * d'enregistrement" (qui, elle, est un résultat normal : liste vide, pas une exception).
 * Se traduit par {@link SpfResult#TEMPERROR} dans {@link SpfEvaluator}.
 */
public class SpfDnsException extends Exception {
  public SpfDnsException(String message) {
    super(message);
  }

  public SpfDnsException(String message, Throwable cause) {
    super(message, cause);
  }
}
