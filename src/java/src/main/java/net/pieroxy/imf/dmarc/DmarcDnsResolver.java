package net.pieroxy.imf.dmarc;

import java.util.List;

/**
 * Abstraction des requêtes DNS dont {@link DmarcEvaluator} a besoin : uniquement des lookups
 * TXT (le record DMARC vit à {@code _dmarc.<domaine>}). Permet de tester l'algorithme sans
 * réseau. Une liste vide signifie "pas de record à ce nom", pas une erreur ;
 * {@link DmarcDnsException} est réservée aux échecs temporaires (timeout, SERVFAIL...).
 */
public interface DmarcDnsResolver {
  List<String> lookupTxt(String name) throws DmarcDnsException;
}
