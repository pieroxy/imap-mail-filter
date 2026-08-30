package net.pieroxy.imf.classifier;

import net.pieroxy.imf.utils.MailTools;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Construit un {@link ClassifierExample} à partir d'un message : uniquement des en-têtes
 * (Subject/From/To/Date/Received), jamais le corps — reste léger et ne risque jamais de
 * marquer \Seen juste en le lisant pour le corpus.
 */
public final class ClassifierExampleExtractor {
  private static final Pattern IPV4_PATTERN = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");

  private ClassifierExampleExtractor() {}

  public static ClassifierExample extract(Message message, ClassifierLabel label, Instant fetchDate) throws MessagingException {
    ClassifierExample example = new ClassifierExample();
    example.setFetchDate(DateTimeFormatter.ISO_INSTANT.format(fetchDate));
    example.setMailDate(message.getSentDate() != null
        ? DateTimeFormatter.ISO_INSTANT.format(message.getSentDate().toInstant())
        : null);
    example.setFrom(addresses(message.getFrom()));
    example.setTo(addresses(message.getRecipients(Message.RecipientType.TO)));
    example.setSubject(message.getSubject());
    example.setIp(extractOriginatingIp(message));
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
}
