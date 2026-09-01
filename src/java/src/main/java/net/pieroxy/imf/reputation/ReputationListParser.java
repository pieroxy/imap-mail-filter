package net.pieroxy.imf.reputation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Parse le contenu brut d'une liste téléchargée (une entrée par ligne, lignes vides ignorées)
 * en {@link ReputationList} exploitable. "#" ou ";" démarrent un commentaire, en tête de ligne
 * ou en fin de ligne — ex: Spamhaus DROP (https://www.spamhaus.org/drop/drop.txt) publie
 * {@code 1.10.16.0/20 ; SBL256894}, où {@code ; SBL256894} doit être ignoré sans invalider toute
 * la ligne. Une entrée invalide est ignorée (avec un warning) plutôt que de faire échouer le
 * chargement de toute la liste pour une seule ligne malformée — une liste externe peut évoluer
 * sans prévenir.
 */
final class ReputationListParser {
  private static final Logger LOGGER = Logger.getLogger(ReputationListParser.class.getName());

  private ReputationListParser() {}

  /** validCount/invalidCount comptent les lignes (pas les entrées finales : un domaine dupliqué compte deux fois côté lignes, une seule fois dans la liste chargée) — voir {@link ReputationRegistry} pour le log de stats/timing qui s'en sert. */
  record ParseResult(ReputationList list, int validCount, int invalidCount) {}

  static ParseResult parse(String id, ReputationListType type, String content) {
    return switch (type) {
      case IP_CIDR -> parseIpCidr(id, content);
      case DOMAIN -> parseDomain(content);
    };
  }

  private static ParseResult parseIpCidr(String id, String content) {
    List<CidrRange> ranges = new ArrayList<>();
    int valid = 0;
    int invalid = 0;
    for (String line : lines(content)) {
      try {
        ranges.add(CidrRange.parse(line));
        valid++;
      } catch (IllegalArgumentException e) {
        invalid++;
        LOGGER.warning("Reputation list [" + id + "]: skipping invalid IPv4/CIDR entry \"" + line + "\"");
      }
    }
    return new ParseResult(new IpReputationList(ranges), valid, invalid);
  }

  private static ParseResult parseDomain(String content) {
    Set<String> domains = new HashSet<>();
    int valid = 0;
    for (String line : lines(content)) {
      domains.add(line.toLowerCase(Locale.ROOT));
      valid++;
    }
    return new ParseResult(new DomainReputationList(domains), valid, 0);
  }

  private static List<String> lines(String content) {
    List<String> result = new ArrayList<>();
    for (String raw : content.split("\\r?\\n")) {
      String line = stripComment(raw).trim();
      if (line.isEmpty()) continue;
      result.add(line);
    }
    return result;
  }

  private static String stripComment(String line) {
    int hash = line.indexOf('#');
    int semi = line.indexOf(';');
    int cut = hash < 0 ? semi : (semi < 0 ? hash : Math.min(hash, semi));
    return cut < 0 ? line : line.substring(0, cut);
  }
}
