package net.pieroxy.imf.dmarc;

/**
 * Résultat d'une évaluation DMARC (RFC 7489 §11.2). Contrairement à SPF/DKIM, DMARC n'a que
 * cinq issues possibles : il n'y a pas de "softfail"/"neutral"/"policy" — DMARC passe ou
 * échoue, purement en fonction de l'alignment SPF/DKIM (voir {@link DmarcEvaluator}).
 */
public enum DmarcResult {
  NONE,
  PASS,
  FAIL,
  TEMPERROR,
  PERMERROR;

  public String getCode() {
    return name().toLowerCase();
  }
}
