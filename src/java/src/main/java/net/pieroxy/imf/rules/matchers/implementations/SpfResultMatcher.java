package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.rules.matchers.Matcher;
import net.pieroxy.imf.spf.DnsJavaSpfDnsResolver;
import net.pieroxy.imf.spf.SpfEvaluator;
import net.pieroxy.imf.spf.SpfIdentityExtractor;
import net.pieroxy.imf.spf.SpfResult;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.Optional;

/**
 * Compare le résultat de la vérification SPF du message (ex: "pass", "fail", "softfail",
 * "neutral", "none") à la clé configurée, de façon insensible à la casse.
 * <p>
 * Le résultat est lu en priorité dans le header {@code Authentication-Results} (RFC 8601) ou
 * {@code Received-SPF}, si un serveur en amont (ex: un relais) les a déjà écrits. À défaut —
 * notamment parce que notre propre serveur IMAP ne fait pas cette vérification à la
 * réception — il est recalculé nous-mêmes via une résolution DNS live, à partir de l'IP
 * connectée (lue dans le {@code Received} le plus récent) et du domaine expéditeur (voir
 * {@link SpfIdentityExtractor}).
 */
public class SpfResultMatcher extends Matcher {
  private static final String AUTHENTICATION_RESULTS_HEADER = "Authentication-Results";
  private static final String RECEIVED_SPF_HEADER = "Received-SPF";

  private final SpfEvaluator evaluator;

  public SpfResultMatcher() {
    this(new SpfEvaluator(new DnsJavaSpfDnsResolver()));
  }

  /** Visible pour les tests : permet d'injecter un évaluateur sans résolution DNS réelle. */
  SpfResultMatcher(SpfEvaluator evaluator) {
    this.evaluator = evaluator;
  }

  @Override
  public boolean matches(Message message) throws MessagingException {
    String result = extractSpfResult(message);
    boolean matched = result != null && matchesKey(result, String::equalsIgnoreCase);
    getLogger().fine(() -> "tested spf result=" + result + " against " + describeKey()
            + " -> " + (matched ? "match" : "no match"));
    return matched;
  }

  @Override
  public String extractKeyFromExample(Message message) throws MessagingException {
    String result = extractSpfResult(message);
    if (result == null) {
      throw new MessagingException("Cannot learn a SPF_RESULT_EQUALS rule: could not determine an SPF result for this message");
    }
    return result;
  }

  private String extractSpfResult(Message message) throws MessagingException {
    String fromHeaders = extractFromHeaders(message);
    if (fromHeaders != null) return fromHeaders;
    return evaluateLive(message);
  }

  private static String extractFromHeaders(Message message) throws MessagingException {
    String[] authResults = message.getHeader(AUTHENTICATION_RESULTS_HEADER);
    if (authResults != null) {
      for (String header : authResults) {
        String result = extractFromAuthenticationResults(header);
        if (result != null) return result;
      }
    }
    String[] receivedSpf = message.getHeader(RECEIVED_SPF_HEADER);
    if (receivedSpf != null) {
      for (String header : receivedSpf) {
        String result = extractFirstToken(header);
        if (result != null) return result;
      }
    }
    return null;
  }

  private String evaluateLive(Message message) throws MessagingException {
    // getLogger() (niveau piloté par le "logLevel" de CETTE règle dans le JSON) est passé
    // jusqu'au fond de l'extraction et de l'évaluation, pour que "logLevel": "DEBUG" sur la
    // règle suffise à voir toute la trace (header Received examiné, record SPF trouvé,
    // mécanisme par mécanisme) sans toucher à une configuration de logging globale.
    Optional<String> ip = SpfIdentityExtractor.extractClientIp(message, getLogger());
    Optional<String> domain = SpfIdentityExtractor.extractSenderDomain(message, getLogger());
    if (ip.isEmpty() || domain.isEmpty()) {
      getLogger().fine(() -> "Cannot evaluate SPF live: missing client IP or sender domain");
      return null;
    }
    SpfResult result = evaluator.evaluate(ip.get(), domain.get(), getLogger());
    getLogger().fine(() -> "Evaluated SPF live for ip=" + ip.get() + " domain=" + domain.get() + " -> " + result.getCode());
    return result.getCode();
  }

  private static String extractFromAuthenticationResults(String headerValue) {
    if (headerValue == null) return null;
    int idx = headerValue.toLowerCase().indexOf("spf=");
    if (idx < 0) return null;
    int start = idx + "spf=".length();
    int end = start;
    while (end < headerValue.length() && !Character.isWhitespace(headerValue.charAt(end))
            && headerValue.charAt(end) != ';' && headerValue.charAt(end) != '(') {
      end++;
    }
    return end > start ? headerValue.substring(start, end) : null;
  }

  private static String extractFirstToken(String headerValue) {
    if (headerValue == null) return null;
    String trimmed = headerValue.trim();
    int end = 0;
    while (end < trimmed.length() && !Character.isWhitespace(trimmed.charAt(end)) && trimmed.charAt(end) != '(') {
      end++;
    }
    return end > 0 ? trimmed.substring(0, end) : null;
  }
}
