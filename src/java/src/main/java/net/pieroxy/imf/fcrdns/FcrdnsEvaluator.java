package net.pieroxy.imf.fcrdns;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Évaluateur FCrDNS (Forward-Confirmed reverse DNS) : vérifie qu'une IP connectée a un
 * enregistrement PTR (reverse DNS) qui se confirme par une résolution forward (A/AAAA) du
 * hostname retombant sur la même IP.
 * <p>
 * Contrairement à SPF/DKIM/DMARC, ce n'est pas un standard d'authentification de domaine — le
 * PTR est contrôlé par le propriétaire du bloc d'IP (le FAI/hébergeur), pas par le domaine
 * expéditeur. C'est un signal sur la légitimité de l'infrastructure qui s'est connectée (une
 * IP dynamique/résidentielle typique de botnet n'a en général pas de PTR forward-confirmé),
 * pas une preuve que le message vient bien du domaine qu'il prétend représenter.
 */
public class FcrdnsEvaluator {
  private static final Pattern IP_LITERAL = Pattern.compile("^[0-9a-fA-F:.]+$");

  private final FcrdnsDnsResolver resolver;
  private final Logger defaultLogger = Logger.getLogger(FcrdnsEvaluator.class.getName());

  public FcrdnsEvaluator(FcrdnsDnsResolver resolver) {
    this.resolver = resolver;
  }

  /** @return le résultat FCrDNS pour l'IP connectée. */
  public FcrdnsResult evaluate(String ip) {
    return evaluate(ip, defaultLogger);
  }

  /** Comme {@link #evaluate(String)}, mais journalise (niveau FINE) le détail sur le logger donné. */
  public FcrdnsResult evaluate(String ip, Logger logger) {
    if (ip == null || !IP_LITERAL.matcher(ip).matches()) {
      logger.fine(() -> "Not a valid IP literal, cannot evaluate FCrDNS: " + ip);
      return FcrdnsResult.NONE;
    }
    InetAddress target;
    try {
      target = InetAddress.getByName(ip);
    } catch (UnknownHostException e) {
      logger.fine(() -> "Not a valid IP literal, cannot evaluate FCrDNS: " + ip);
      return FcrdnsResult.NONE;
    }
    try {
      List<String> ptrNames = resolver.lookupPtr(ip);
      if (ptrNames.isEmpty()) {
        logger.fine(() -> "No PTR record for " + ip);
        return FcrdnsResult.NONE;
      }
      boolean isV6 = target.getAddress().length == 16;
      for (String ptrName : ptrNames) {
        logger.fine(() -> "PTR for " + ip + ": " + ptrName);
        List<String> forwardAddresses = isV6 ? resolver.lookupAaaa(ptrName) : resolver.lookupA(ptrName);
        if (forwardConfirms(target, forwardAddresses)) {
          logger.fine(() -> "Forward-confirmed: " + ptrName + " resolves back to " + ip);
          return FcrdnsResult.PASS;
        }
      }
      logger.fine(() -> "PTR record(s) found for " + ip + " but none forward-confirm");
      return FcrdnsResult.FAIL;
    } catch (FcrdnsDnsException e) {
      logger.log(Level.FINE, "FCrDNS DNS lookup failed for " + ip, e);
      return FcrdnsResult.TEMPERROR;
    }
  }

  private static boolean forwardConfirms(InetAddress target, List<String> candidates) {
    for (String candidate : candidates) {
      try {
        // Comparaison par InetAddress, pas par égalité de chaîne : deux représentations
        // textuelles différentes (notamment IPv6 compressé) peuvent désigner la même adresse.
        if (InetAddress.getByName(candidate).equals(target)) {
          return true;
        }
      } catch (UnknownHostException ignored) {
        // ne devrait pas arriver : candidate vient du resolver, déjà un littéral valide
      }
    }
    return false;
  }
}
