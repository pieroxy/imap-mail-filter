package net.pieroxy.imf.dkim;

import java.util.List;

/**
 * Résultat détaillé d'une vérification DKIM : le résultat agrégé (voir {@link DkimVerifier}),
 * plus les domaines ({@code d=}) de chaque signature qui a effectivement vérifié avec succès.
 * Ce détail sert au calcul de l'alignment DKIM d'un DMARC (RFC 7489 §3.1.2), qui a besoin de
 * savoir *quel* domaine a signé, pas seulement s'il y en a un qui a réussi.
 */
public record DkimVerification(DkimResult result, List<String> passingDomains) {
}
