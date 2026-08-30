package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.rules.matchers.Matcher;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import java.util.Arrays;

/**
 * Comme {@link FromExactMatcher}, mais ne compare que l'adresse mail elle-même
 * (ex: "jdupont@hotmail.com"), sans le nom affiché ("Jean Dupont <jdupont@hotmail.com>"),
 * et de façon insensible à la casse.
 */
public class FromAddressMatcher extends Matcher {
  @Override
  public boolean matches(Message message) throws MessagingException {
    var froms = message.getFrom();
    if (froms == null) {
      getLogger().fine(() -> "no From header on message, no match against " + getConfig().getKey());
      return false;
    }
    if (froms.length == 1) {
      String address = extractAddress(froms[0]);
      boolean matched = address != null && address.equalsIgnoreCase(getConfig().getKey());
      getLogger().fine(() -> "tested from address=" + address + " against " + getConfig().getKey()
              + " -> " + (matched ? "match" : "no match"));
      return matched;
    }
    getLogger().fine(() -> "multiple From addresses " + Arrays.toString(froms) + ", no match against " + getConfig().getKey());
    return false;
  }

  @Override
  public String extractKeyFromExample(Message message) throws MessagingException {
    Address[] froms = message.getFrom();
    if (froms == null || froms.length != 1) {
      throw new MessagingException("Cannot learn a FROM_ADDRESS_EQUALS rule: message must have exactly one From address");
    }
    String address = extractAddress(froms[0]);
    if (address == null) {
      throw new MessagingException("Cannot learn a FROM_ADDRESS_EQUALS rule: From address has no email part");
    }
    return address;
  }

  private static String extractAddress(Address address) {
    return address instanceof InternetAddress ? ((InternetAddress) address).getAddress() : address.toString();
  }
}
