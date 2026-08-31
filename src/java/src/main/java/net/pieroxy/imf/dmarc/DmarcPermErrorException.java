package net.pieroxy.imf.dmarc;

/**
 * Erreur permanente d'évaluation DMARC : record malformé (pas de tag {@code p=}) ou ambigu
 * (plusieurs records TXT valides au même nom, RFC 7489 §6.6.3 — le récepteur ne peut pas savoir
 * lequel est autoritaire). Se traduit par {@link DmarcResult#PERMERROR}.
 */
class DmarcPermErrorException extends Exception {
  DmarcPermErrorException(String message) {
    super(message);
  }
}
