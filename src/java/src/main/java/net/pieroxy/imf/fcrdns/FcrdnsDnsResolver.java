package net.pieroxy.imf.fcrdns;

import java.util.List;

/**
 * Abstraction des requêtes DNS dont {@link FcrdnsEvaluator} a besoin : le lookup PTR (reverse)
 * d'une IP, et le lookup forward (A/AAAA) d'un hostname pour confirmer le PTR. Une liste vide
 * signifie "rien à ce nom", pas une erreur ; {@link FcrdnsDnsException} est réservée aux échecs
 * temporaires (timeout, SERVFAIL...).
 */
public interface FcrdnsDnsResolver {
  /** Hostnames PTR pour cette IP (littérale, IPv4 ou IPv6). */
  List<String> lookupPtr(String ip) throws FcrdnsDnsException;

  /** Adresses IPv4 de ce hostname. */
  List<String> lookupA(String hostname) throws FcrdnsDnsException;

  /** Adresses IPv6 de ce hostname. */
  List<String> lookupAaaa(String hostname) throws FcrdnsDnsException;
}
