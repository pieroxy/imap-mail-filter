package net.pieroxy.imf.dkim;

/**
 * Résultat d'une vérification DKIM (RFC 6376), au vocabulaire RFC 8601 §2.7.1. Les noms des
 * constantes correspondent exactement à {@code org.apache.james.jdkim.api.Result.Type}, pour
 * une conversion directe par {@link Enum#valueOf}.
 */
public enum DkimResult {
  NONE,
  PASS,
  FAIL,
  POLICY,
  NEUTRAL,
  TEMPERROR,
  PERMERROR;

  public String getCode() {
    return name().toLowerCase();
  }
}
