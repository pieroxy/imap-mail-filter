package net.pieroxy.imf.reputation;

/**
 * Format d'une liste de réputation, une entrée par ligne (commentaires "#", lignes vides
 * ignorées) : soit des IPv4/CIDR ({@code IP_CIDR}), soit des noms de domaine ({@code DOMAIN}).
 * Détermine à la fois le parseur à utiliser ({@link ReputationListParser}) et quel matcher peut
 * référencer la liste ({@code IP_REPUTATION_EQUALS} pour IP_CIDR, {@code FROM_DOMAIN_REPUTATION_EQUALS}
 * pour DOMAIN) — voir {@link ReputationRegistry}.
 */
public enum ReputationListType {
  IP_CIDR,
  DOMAIN
}
