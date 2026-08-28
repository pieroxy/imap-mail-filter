package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.rules.matchers.Matcher;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;

public class FromExactMatcher extends Matcher {
  @Override
  public boolean matches(Message message) throws MessagingException {
    var froms = message.getFrom();
    if (froms == null) {
      return false;
    }
    if (froms.length == 1) {
      return froms[0].toString().equals(getConfig().getKey());
    }
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
