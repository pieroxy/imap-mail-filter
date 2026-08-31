package net.pieroxy.imf.dmarc;

import com.google.common.net.InternetDomainName;

import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Évaluateur DMARC (RFC 7489) : ne refait ni SPF ni DKIM — il prend leurs résultats déjà
 * calculés (voir {@link net.pieroxy.imf.spf.SpfEvaluator}, {@link net.pieroxy.imf.dkim.DkimVerifier})
 * et détermine si l'un des deux est "aligné" avec le domaine visible dans {@code From:}.
 * <p>
 * DMARC passe si SPF a réussi ET est aligné, OU si DKIM a réussi ET est aligné (RFC 7489
 * §3.1) — un seul des deux suffit. "Aligné" veut dire : même domaine exact (mode strict,
 * {@code adkim=s}/{@code aspf=s}), ou même domaine organisationnel (mode relaxed, par défaut)
 * — calculé via la Public Suffix List (Guava {@code InternetDomainName}), pas une comparaison
 * naïve des deux derniers labels : pour {@code mail.example.co.uk}, le domaine organisationnel
 * est {@code example.co.uk}, pas {@code co.uk} (qui est lui-même un suffixe public partagé par
 * des millions de domaines sans rapport).
 * <p>
 * Le record est cherché au domaine exact, puis — s'il est absent là — au domaine
 * organisationnel (RFC 7489 §6.6.3), pour couvrir le cas d'un sous-domaine qui n'a pas son
 * propre record DMARC.
 */
public class DmarcEvaluator {
  private final DmarcDnsResolver resolver;
  private final Logger defaultLogger = Logger.getLogger(DmarcEvaluator.class.getName());

  public DmarcEvaluator(DmarcDnsResolver resolver) {
    this.resolver = resolver;
  }

  /**
   * @param fromDomain domaine du header From: affiché (l'identité que DMARC protège).
   * @param spfPassed  true si SPF a renvoyé "pass" pour ce message.
   * @param spfDomain  le domaine que SPF a effectivement vérifié (voir SpfIdentityExtractor), non nul si spfPassed est true.
   * @param dkimPassingDomains les domaines (d=) de chaque signature DKIM qui a vérifié avec succès.
   */
  public DmarcResult evaluate(String fromDomain, boolean spfPassed, String spfDomain, List<String> dkimPassingDomains) {
    return evaluate(fromDomain, spfPassed, spfDomain, dkimPassingDomains, defaultLogger);
  }

  /** Comme {@link #evaluate(String, boolean, String, List)}, mais journalise (niveau FINE) sur le logger donné. */
  public DmarcResult evaluate(String fromDomain, boolean spfPassed, String spfDomain, List<String> dkimPassingDomains, Logger logger) {
    if (fromDomain == null || fromDomain.isBlank()) return DmarcResult.NONE;
    try {
      DmarcRecord record = findRecord(fromDomain, logger);
      if (record == null) {
        logger.fine(() -> "No DMARC record for " + fromDomain + " or its organizational domain");
        return DmarcResult.NONE;
      }
      boolean spfAligned = spfPassed && spfDomain != null && domainsAligned(fromDomain, spfDomain, record.strictSpf);
      boolean dkimAligned = dkimPassingDomains.stream().anyMatch(d -> domainsAligned(fromDomain, d, record.strictDkim));
      logger.fine(() -> "DMARC alignment for from=" + fromDomain + ": spfAligned=" + spfAligned + " dkimAligned=" + dkimAligned);
      return (spfAligned || dkimAligned) ? DmarcResult.PASS : DmarcResult.FAIL;
    } catch (DmarcPermErrorException e) {
      logger.fine(() -> "DMARC record error for " + fromDomain + ": " + e.getMessage());
      return DmarcResult.PERMERROR;
    } catch (DmarcDnsException e) {
      logger.log(Level.FINE, "DMARC DNS lookup failed for " + fromDomain, e);
      return DmarcResult.TEMPERROR;
    }
  }

  private DmarcRecord findRecord(String fromDomain, Logger logger) throws DmarcDnsException, DmarcPermErrorException {
    DmarcRecord record = fetchValidRecord(fromDomain);
    if (record != null) return record;

    Optional<String> orgDomain = organizationalDomainOf(fromDomain);
    if (orgDomain.isPresent() && !orgDomain.get().equalsIgnoreCase(fromDomain)) {
      logger.fine(() -> "No DMARC record at " + fromDomain + ", trying organizational domain " + orgDomain.get());
      return fetchValidRecord(orgDomain.get());
    }
    return null;
  }

  private DmarcRecord fetchValidRecord(String domain) throws DmarcDnsException, DmarcPermErrorException {
    List<String> candidates = resolver.lookupTxt("_dmarc." + domain).stream()
            .filter(DmarcEvaluator::looksLikeDmarcRecord)
            .toList();
    if (candidates.isEmpty()) return null;
    if (candidates.size() > 1) {
      throw new DmarcPermErrorException("Multiple DMARC records for " + domain);
    }
    return parseRecord(candidates.get(0), domain);
  }

  private static boolean looksLikeDmarcRecord(String txt) {
    return txt.regionMatches(true, 0, "v=DMARC1", 0, 8)
            && (txt.length() == 8 || txt.charAt(8) == ';' || Character.isWhitespace(txt.charAt(8)));
  }

  private static DmarcRecord parseRecord(String txt, String domain) throws DmarcPermErrorException {
    String policy = null;
    boolean strictDkim = false;
    boolean strictSpf = false;
    for (String rawTag : txt.split(";")) {
      String tag = rawTag.trim();
      int eq = tag.indexOf('=');
      if (eq < 0) continue;
      String name = tag.substring(0, eq).trim().toLowerCase();
      String value = tag.substring(eq + 1).trim();
      switch (name) {
        case "p" -> policy = value.toLowerCase();
        case "adkim" -> strictDkim = "s".equalsIgnoreCase(value);
        case "aspf" -> strictSpf = "s".equalsIgnoreCase(value);
        default -> {
          // sp=, pct=, rua=, ruf=, fo=, ri=, v=... : ignorés, pas nécessaires pour calculer
          // pass/fail (seulement pour le reporting ou la politique d'action, hors périmètre).
        }
      }
    }
    if (policy == null) {
      throw new DmarcPermErrorException("DMARC record for " + domain + " has no p= tag");
    }
    return new DmarcRecord(strictDkim, strictSpf);
  }

  private static boolean domainsAligned(String fromDomain, String otherDomain, boolean strict) {
    if (strict) return fromDomain.equalsIgnoreCase(otherDomain);
    String fromOrg = organizationalDomainOf(fromDomain).orElse(fromDomain.toLowerCase());
    String otherOrg = organizationalDomainOf(otherDomain).orElse(otherDomain.toLowerCase());
    return fromOrg.equalsIgnoreCase(otherOrg);
  }

  private static Optional<String> organizationalDomainOf(String domain) {
    try {
      InternetDomainName name = InternetDomainName.from(domain);
      if (!name.isUnderPublicSuffix()) return Optional.empty();
      return Optional.of(name.topPrivateDomain().toString());
    } catch (IllegalArgumentException | IllegalStateException e) {
      return Optional.empty();
    }
  }

  private static final class DmarcRecord {
    final boolean strictDkim;
    final boolean strictSpf;

    DmarcRecord(boolean strictDkim, boolean strictSpf) {
      this.strictDkim = strictDkim;
      this.strictSpf = strictSpf;
    }
  }
}
