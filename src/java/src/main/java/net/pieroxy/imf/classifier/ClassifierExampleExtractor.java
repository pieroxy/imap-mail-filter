package net.pieroxy.imf.classifier;

import net.pieroxy.imf.utils.MailTools;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a {@link ClassifierExample} from a message: mostly headers
 * (Subject/From/To/Date/Received/In-Reply-To/References/Precedence/List-Id/List-Unsubscribe/
 * Return-Path/Reply-To) plus the MIME structure's attachment filenames — never the actual body
 * content (text/HTML), so this stays lightweight (a BODYSTRUCTURE fetch, not the message's full
 * bytes) and never risks marking \Seen just by reading it for the corpus.
 */
public final class ClassifierExampleExtractor {
  private static final Pattern IPV4_PATTERN = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
  private static final String NO_EXTENSION = "(none)";

  private ClassifierExampleExtractor() {}

  public static ClassifierExample extract(Message message, ClassifierLabel label, Instant fetchDate) throws MessagingException {
    ClassifierExample example = new ClassifierExample();
    example.setMessageId(headerValue(message, "Message-ID"));
    example.setFetchDate(DateTimeFormatter.ISO_INSTANT.format(fetchDate));
    example.setMailDate(message.getSentDate() != null
        ? DateTimeFormatter.ISO_INSTANT.format(message.getSentDate().toInstant())
        : null);
    example.setReceivedDate(message.getReceivedDate() != null
        ? DateTimeFormatter.ISO_INSTANT.format(message.getReceivedDate().toInstant())
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

    List<String> attachmentExtensions = new ArrayList<>();
    collectAttachmentExtensions(message, attachmentExtensions);
    example.setAttachmentExtensions(attachmentExtensions);

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
   * Display name(s) ("Alice" for "Alice &lt;alice@example.com&gt;"), joined by a space —
   * {@link MailTools#getMailAddress} ignores them, even though they're a potentially useful
   * signal for a future classifier (see {@link ClassifierExample#getFromDisplayName()}).
   * @return null if no address has a display name set.
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
   * Best-effort: takes the first IPv4 address found in the last (oldest) Received header (each
   * successive relay prepends its own, so the last one is closest to the original sender). No
   * full RFC 5321 parsing, no IPv6 for now — to be refined based on what actually turns out to be
   * useful once there's real data.
   */
  private static String extractOriginatingIp(Message message) throws MessagingException {
    String[] received = message.getHeader("Received");
    if (received == null || received.length == 0) return null;
    Matcher m = IPV4_PATTERN.matcher(received[received.length - 1]);
    return m.find() ? m.group() : null;
  }

  /**
   * Recursively walks the MIME tree (fetched as BODYSTRUCTURE, not the actual body bytes — see
   * ImapMailboxConnection's FetchProfile.Item.CONTENT_INFO for the corpus scan's batched
   * prefetch of this) collecting one lowercase extension per attachment-like part found — any
   * part carrying a filename, not just ones marked Content-Disposition: attachment, since some
   * mailers omit that but still name the part.
   */
  private static void collectAttachmentExtensions(Part part, List<String> extensionsOut) throws MessagingException {
    if (!part.isMimeType("multipart/*")) {
      String filename = part.getFileName();
      if (filename != null && !filename.isBlank()) {
        extensionsOut.add(extensionOf(filename));
      }
      return;
    }
    try {
      Object content = part.getContent();
      if (content instanceof Multipart multipart) {
        for (int i = 0; i < multipart.getCount(); i++) {
          collectAttachmentExtensions(multipart.getBodyPart(i), extensionsOut);
        }
      }
    } catch (IOException e) {
      // Malformed/undecodable part: no attachment info recoverable from it, not worth failing
      // the whole example extraction over.
    }
  }

  private static String extensionOf(String filename) {
    int dot = filename.lastIndexOf('.');
    return dot >= 0 && dot < filename.length() - 1 ? filename.substring(dot + 1).toLowerCase(Locale.ROOT) : NO_EXTENSION;
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

  /** Domain of From, only if the message has exactly one From address (same convention as FromDomainMatcher/SpfIdentityExtractor). */
  private static String singleFromDomain(Address[] from) {
    if (from == null || from.length != 1) return null;
    String raw = from[0] instanceof InternetAddress ? ((InternetAddress) from[0]).getAddress() : from[0].toString();
    return domainOf(raw);
  }

  /** Return-Path is in the "&lt;addr&gt;" format (sometimes "&lt;&gt;" for a bounce with no return address) — not a normal RFC 5322 address, never parsed as one (see SpfIdentityExtractor). */
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

  /** null (undeterminable) if either domain is missing — never confused with "no mismatch". */
  private static Boolean mismatch(String fromDomain, String otherDomain) {
    if (fromDomain == null || otherDomain == null) return null;
    return !fromDomain.equalsIgnoreCase(otherDomain);
  }
}
