package net.pieroxy.imf.dmarc;

/**
 * Résultat détaillé d'une évaluation DMARC : le verdict pass/fail/none/... (voir
 * {@link DmarcResult}, utilisé par {@code DMARC_RESULT_EQUALS}), et la politique publiée par le
 * domaine (voir {@link DmarcPolicy}, utilisée par {@code DMARC_POLICY_EQUALS}) — les deux issus
 * du même record DNS, calculés en un seul passage par {@link DmarcEvaluator#evaluateDetailed}.
 */
public record DmarcEvaluation(DmarcResult result, DmarcPolicy policy) {
}
