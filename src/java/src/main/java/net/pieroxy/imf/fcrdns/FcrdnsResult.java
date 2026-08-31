package net.pieroxy.imf.fcrdns;

/**
 * Résultat d'un contrôle FCrDNS (Forward-Confirmed reverse DNS) sur une IP connectée.
 * Contrairement à SPF/DKIM/DMARC, il n'existe pas de RFC d'authentification dédiée à ce
 * vocabulaire — ces quatre valeurs sont une convention interne à IMF, pas un standard.
 */
public enum FcrdnsResult {
  /** L'IP a un PTR, et ce PTR se confirme (une résolution A/AAAA du hostname retombe sur l'IP). */
  PASS,
  /** L'IP a un ou plusieurs PTR, mais aucun ne se confirme. */
  FAIL,
  /** L'IP n'a aucun PTR. */
  NONE,
  /** Une résolution DNS a échoué temporairement (timeout, SERVFAIL...). */
  TEMPERROR;

  public String getCode() {
    return name().toLowerCase();
  }
}
