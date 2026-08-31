package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.rules.matchers.Matcher;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import java.util.Arrays;

/**
 * Comme {@link FromAddressMatcher}, mais ne compare que le nom de domaine de l'adresse
 * (ex: "hotmail.com" pour "jdupont@hotmail.com"), de façon insensible à la casse — utile
 * pour bloquer tout un domaine plutôt qu'une adresse précise.
 */
public class FromDomainMatcher extends Matcher {
  @Override
  public boolean matches(Message message) throws MessagingException {
    var froms = message.getFrom();
    if (froms == null) {
      getLogger().fine(() -> "no From header on message, no match against " + describeKey());
      return false;
    }
    if (froms.length == 1) {
      String domain = extractDomain(froms[0]);
      boolean matched = domain != null && matchesKey(domain, String::equalsIgnoreCase);
      getLogger().fine(() -> "tested from domain=" + domain + " against " + describeKey()
              + " -> " + (matched ? "match" : "no match"));
      return matched;
    }
    getLogger().fine(() -> "multiple From addresses " + Arrays.toString(froms) + ", no match against " + describeKey());
    return false;
  }

  @Override
  public String extractKeyFromExample(Message message) throws MessagingException {
    Address[] froms = message.getFrom();
    if (froms == null || froms.length != 1) {
      throw new MessagingException("Cannot learn a FROM_DOMAIN_EQUALS rule: message must have exactly one From address");
    }
    String domain = extractDomain(froms[0]);
    if (domain == null) {
      throw new MessagingException("Cannot learn a FROM_DOMAIN_EQUALS rule: From address has no domain part");
    }
    return domain;
  }

  private static String extractDomain(Address address) {
    String raw = address instanceof InternetAddress ? ((InternetAddress) address).getAddress() : address.toString();
    if (raw == null) return null;
    int at = raw.lastIndexOf('@');
    return at >= 0 && at < raw.length() - 1 ? raw.substring(at + 1) : null;
  }
}
