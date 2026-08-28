package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.rules.matchers.Matcher;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.Arrays;

public class FromExactMatcher extends Matcher {
  @Override
  public boolean matches(Message message) throws MessagingException {
    var froms = message.getFrom();
    if (froms == null) {
      getLogger().fine(() -> "no From header on message, no match against " + getConfig().getKey());
      return false;
    }
    if (froms.length == 1) {
      boolean matched = froms[0].toString().equals(getConfig().getKey());
      getLogger().fine(() -> "tested from=" + froms[0] + " against " + getConfig().getKey()
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
      throw new MessagingException("Cannot learn a FROM_EQUALS rule: message must have exactly one From address");
    }
    return froms[0].toString();
  }
}
