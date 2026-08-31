package net.pieroxy.imf.spf;

/**
 * Résultat d'une évaluation SPF (RFC 7208 §2.6). Le nom de la constante en minuscules
 * ({@link #getCode()}) est ce qui est comparé à la clé configurée sur un matcher, exactement
 * comme les valeurs lues dans un header {@code Authentication-Results} (spf=pass, spf=fail...).
 */
public enum SpfResult {
  PASS,
  FAIL,
  SOFTFAIL,
  NEUTRAL,
  NONE,
  PERMERROR,
  TEMPERROR;

  public String getCode() {
    return name().toLowerCase();
  }
}
