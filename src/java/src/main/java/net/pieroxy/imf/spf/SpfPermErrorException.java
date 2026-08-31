package net.pieroxy.imf.spf;

/**
 * Erreur permanente d'évaluation SPF : record malformé, mécanisme inconnu, valeur manquante,
 * budget de lookups DNS dépassé (RFC 7208 §4.6.4), ou "include"/"redirect" pointant vers un
 * domaine sans record SPF. Distincte de {@link SpfDnsException} (échec réseau temporaire) :
 * ici, réessayer plus tard ne changerait rien tant que le record SPF n'est pas corrigé.
 * Se traduit par {@link SpfResult#PERMERROR} dans {@link SpfEvaluator}.
 */
class SpfPermErrorException extends Exception {
  SpfPermErrorException(String message) {
    super(message);
  }
}
