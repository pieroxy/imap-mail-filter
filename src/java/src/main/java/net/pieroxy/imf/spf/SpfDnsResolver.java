package net.pieroxy.imf.spf;

import java.util.List;

/**
 * Abstraction des requêtes DNS dont {@link SpfEvaluator} a besoin. Permet de tester
 * l'algorithme SPF avec des réponses en mémoire, sans réseau ni serveur DNS réel.
 * <p>
 * Toutes les méthodes renvoient une liste vide quand le domaine ou le type d'enregistrement
 * n'existe pas (NXDOMAIN / NODATA) : ce n'est pas une erreur, c'est un résultat DNS normal.
 * {@link SpfDnsException} est réservée aux échecs temporaires (timeout, SERVFAIL...).
 */
public interface SpfDnsResolver {
  /** Contenu brut des enregistrements TXT du domaine (chaque record concaténé en une String). */
  List<String> lookupTxt(String domain) throws SpfDnsException;

  /** Adresses IPv4 du domaine, sous forme de littéraux ("1.2.3.4"). */
  List<String> lookupA(String domain) throws SpfDnsException;

  /** Adresses IPv6 du domaine, sous forme de littéraux. */
  List<String> lookupAaaa(String domain) throws SpfDnsException;

  /** Noms d'hôtes des enregistrements MX du domaine. */
  List<String> lookupMx(String domain) throws SpfDnsException;
}
