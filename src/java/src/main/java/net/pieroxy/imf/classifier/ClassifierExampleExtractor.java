package net.pieroxy.imf.classifier;

import net.pieroxy.imf.utils.MailTools;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Construit un {@link ClassifierExample} à partir d'un message : uniquement des en-têtes
 * (Subject/From/To/Date/Received/In-Reply-To/References/Precedence/List-Id/List-Unsubscribe/
 * Return-Path/Reply-To), jamais le corps — reste léger et ne risque jamais de marquer \Seen
 * juste en le lisant pour le corpus.
 */
public final class ClassifierExampleExtractor {
  private static final Pattern IPV4_PATTERN = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");

  private ClassifierExampleExtractor() {}

  public static ClassifierExample extract(Message message, ClassifierLabel label, Instant fetchDate) throws MessagingException {
    ClassifierExample example = new ClassifierExample();
    example.setMessageId(headerValue(message, "Message-ID"));
    example.setFetchDate(DateTimeFormatter.ISO_INSTANT.format(fetchDate));
    example.setMailDate(message.getSentDate() != null
        ? DateTimeFormatter.ISO_INSTANT.format(message.getSentDate().toInstant())
        : null);
    Address[] from = message.getFrom();
    Address[] to = message.getRecipients(Message.RecipientType.TO);
    example.setFrom(addresses(from));
    example.setFromDisplayName(displayNames(from));
    example.setTo(addresses(to));
    example.setToDisplayName(displayNames(to));
    example.setSubject(message.getSubject());
    example.setIp(extractOriginatingIp(message));

    example.setReply(headerPresent(message, "In-Reply-To") || headerPresent(message, "References"));
    example.setPrecedence(headerValue(message, "Precedence"));
    example.setListId(headerValue(message, "List-Id"));
    example.setListUnsubscribePresent(headerPresent(message, "List-Unsubscribe"));

    String fromDomain = singleFromDomain(from);
    String returnPathDomain = returnPathDomain(message);
    String replyToDomain = replyToDomain(message);
    example.setReturnPathDomain(returnPathDomain);
    example.setReturnPathMismatch(mismatch(fromDomain, returnPathDomain));
    example.setReplyToDomain(replyToDomain);
    example.setReplyToMismatch(mismatch(fromDomain, replyToDomain));

    example.setLabel(label);
    return example;
  }

  private static List<String> addresses(Address[] addresses) throws MessagingException {
    List<String> result = new ArrayList<>();
    if (addresses == null) return result;
    for (Address a : addresses) {
      result.add(MailTools.getMailAddress(a));
    }
    return result;
  }

  /**
   * Display name(s) ("Alice" pour "Alice &lt;alice@example.com&gt;"), joints par un espace —
   * {@link MailTools#getMailAddress} les ignore, alors qu'ils sont potentiellement un signal
   * utile pour un futur classifieur (voir {@link ClassifierExample#getFromDisplayName()}).
   * @return null si aucune adresse n'a de display name renseigné.
   */
  private static String displayNames(Address[] addresses) {
    if (addresses == null) return null;
    List<String> names = new ArrayList<>();
    for (Address a : addresses) {
      if (a instanceof InternetAddress) {
        String personal = ((InternetAddress) a).getPersonal();
        if (personal != null && !personal.isBlank()) {
          names.add(personal);
        }
      }
    }
    return names.isEmpty() ? null : String.join(" ", names);
  }

  /**
   * Best-effort : prend la première adresse IPv4 trouvée dans le dernier en-tête Received
   * (chaque relais successif préfixe le sien en tête, donc le dernier est le plus proche de
   * l'expéditeur d'origine). Pas de parsing RFC5321 complet, pas d'IPv6 pour l'instant — à
   * affiner selon ce qui se révèle réellement utile une fois qu'on aura des données.
   */
  private static String extractOriginatingIp(Message message) throws MessagingException {
    String[] received = message.getHeader("Received");
    if (received == null || received.length == 0) return null;
    Matcher m = IPV4_PATTERN.matcher(received[received.length - 1]);
    return m.find() ? m.group() : null;
  }

  private static boolean headerPresent(Message message, String name) throws MessagingException {
    String[] values = message.getHeader(name);
    return values != null && values.length > 0;
  }

  private static String headerValue(Message message, String name) throws MessagingException {
    String[] values = message.getHeader(name);
    if (values == null || values.length == 0) return null;
    String value = values[0].trim();
    return value.isEmpty() ? null : value;
  }

  /** Domaine du From, seulement si le message a exactement une adresse From (même convention que FromDomainMatcher/SpfIdentityExtractor). */
  private static String singleFromDomain(Address[] from) {
    if (from == null || from.length != 1) return null;
    String raw = from[0] instanceof InternetAddress ? ((InternetAddress) from[0]).getAddress() : from[0].toString();
    return domainOf(raw);
  }

  /** Return-Path est au format "&lt;addr&gt;" (parfois "&lt;&gt;" pour un bounce sans retour) — pas une adresse RFC 5322 normale, jamais parsé comme telle (voir SpfIdentityExtractor). */
  private static String returnPathDomain(Message message) throws MessagingException {
    String value = headerValue(message, "Return-Path");
    if (value == null) return null;
    if (value.startsWith("<") && value.endsWith(">")) {
      value = value.substring(1, value.length() - 1);
    }
    return domainOf(value);
  }

  private static String replyToDomain(Message message) throws MessagingException {
    String value = headerValue(message, "Reply-To");
    if (value == null) return null;
    try {
      InternetAddress[] addresses = InternetAddress.parse(value, false);
      return addresses.length == 0 ? null : domainOf(addresses[0].getAddress());
    } catch (AddressException e) {
      return null;
    }
  }

  private static String domainOf(String address) {
    if (address == null) return null;
    int at = address.lastIndexOf('@');
    if (at < 0 || at >= address.length() - 1) return null;
    return address.substring(at + 1);
  }

  /** null (indéterminable) si l'un des deux domaines manque — jamais confondu avec "pas de mismatch". */
  private static Boolean mismatch(String fromDomain, String otherDomain) {
    if (fromDomain == null || otherDomain == null) return null;
    return !fromDomain.equalsIgnoreCase(otherDomain);
  }
}
