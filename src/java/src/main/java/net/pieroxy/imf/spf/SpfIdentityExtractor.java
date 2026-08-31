package net.pieroxy.imf.spf;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Logger;

/**
 * Extrait, à partir des headers d'un message déjà reçu, les deux informations dont
 * {@link SpfEvaluator} a besoin : l'IP qui s'est connectée en SMTP, et le domaine expéditeur
 * à vérifier.
 * <p>
 * Notre serveur IMAP ne fait pas lui-même la vérification SPF (contrairement à un webmail
 * comme Gmail qui l'écrit dans {@code Authentication-Results}) : on la refait nous-mêmes à
 * partir de ce que le MTA qui a reçu le message a inscrit dans le header {@code Received} le
 * plus récent — celui ajouté par notre propre serveur, qui reflète la connexion TCP réelle et
 * est donc la seule source fiable (les {@code Received} plus anciens peuvent avoir été
 * fabriqués par l'expéditeur).
 */
public class SpfIdentityExtractor {
  // Capture le dernier "[ip]" (IPv4, ou IPv6 avec préfixe optionnel "IPv6:") de la clause
  // "from" d'un Received, ex: "from host.example.com (host.example.com [203.0.113.9])".
  private static final Pattern BRACKETED_IP = Pattern.compile("\\[(?:IPv6:)?([0-9a-fA-F:.]+)]", Pattern.CASE_INSENSITIVE);
  private static final Pattern BY_CLAUSE = Pattern.compile("\\sby\\s", Pattern.CASE_INSENSITIVE);
  private static final Logger DEFAULT_LOGGER = Logger.getLogger(SpfIdentityExtractor.class.getName());

  private SpfIdentityExtractor() {
  }

  /** L'IP qui s'est connectée pour envoyer ce message, lue dans le Received le plus récent. */
  public static Optional<String> extractClientIp(Message message) throws MessagingException {
    return extractClientIp(message, DEFAULT_LOGGER);
  }

  /** Comme {@link #extractClientIp(Message)}, mais journalise (niveau FINE) le header examiné et l'IP trouvée. */
  public static Optional<String> extractClientIp(Message message, Logger logger) throws MessagingException {
    String[] received = message.getHeader("Received");
    if (received == null || received.length == 0) {
      logger.fine(() -> "No Received header on message, cannot determine the connecting IP");
      return Optional.empty();
    }

    String normalized = received[0].replaceAll("\\s+", " ").trim();
    logger.fine(() -> "Most recent Received header: " + normalized);
    Matcher byMatcher = BY_CLAUSE.matcher(normalized);
    String fromClause = byMatcher.find() ? normalized.substring(0, byMatcher.start()) : normalized;

    String lastIp = null;
    Matcher ipMatcher = BRACKETED_IP.matcher(fromClause);
    while (ipMatcher.find()) {
      lastIp = ipMatcher.group(1);
    }
    String foundIp = lastIp;
    if (foundIp != null) {
      logger.fine(() -> "Extracted connecting IP: " + foundIp);
    } else {
      logger.fine(() -> "Could not find a bracketed IP in the most recent Received header");
    }
    return Optional.ofNullable(lastIp);
  }

  /**
   * Le domaine à vérifier vis-à-vis du SPF : celui de l'expéditeur d'enveloppe
   * ({@code Return-Path}, écrit par le MTA de livraison finale à partir du MAIL FROM SMTP)
   * si disponible, sinon celui du header {@code From} affiché.
   */
  public static Optional<String> extractSenderDomain(Message message) throws MessagingException {
    return extractSenderDomain(message, DEFAULT_LOGGER);
  }

  /** Comme {@link #extractSenderDomain(Message)}, mais journalise (niveau FINE) la source retenue. */
  public static Optional<String> extractSenderDomain(Message message, Logger logger) throws MessagingException {
    Optional<String> fromReturnPath = extractDomainFromReturnPath(message);
    if (fromReturnPath.isPresent()) {
      logger.fine(() -> "Sender domain from Return-Path: " + fromReturnPath.get());
      return fromReturnPath;
    }
    Optional<String> fromFrom = extractDomainFromFrom(message);
    if (fromFrom.isPresent()) {
      logger.fine(() -> "No usable Return-Path, sender domain from From: " + fromFrom.get());
    } else {
      logger.fine(() -> "Could not determine a sender domain from Return-Path or From");
    }
    return fromFrom;
  }

  private static Optional<String> extractDomainFromReturnPath(Message message) throws MessagingException {
    String[] returnPath = message.getHeader("Return-Path");
    if (returnPath == null || returnPath.length == 0) return Optional.empty();
    String value = returnPath[0].trim();
    if (value.startsWith("<") && value.endsWith(">")) value = value.substring(1, value.length() - 1);
    return domainOf(value);
  }

  private static Optional<String> extractDomainFromFrom(Message message) throws MessagingException {
    Address[] froms = message.getFrom();
    if (froms == null || froms.length != 1) return Optional.empty();
    String raw = froms[0] instanceof InternetAddress ? ((InternetAddress) froms[0]).getAddress() : froms[0].toString();
    return domainOf(raw);
  }

  private static Optional<String> domainOf(String address) {
    if (address == null) return Optional.empty();
    int at = address.lastIndexOf('@');
    if (at < 0 || at >= address.length() - 1) return Optional.empty();
    return Optional.of(address.substring(at + 1));
  }
}
