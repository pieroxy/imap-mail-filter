package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.rules.matchers.Matcher;

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
      froms[0].toString().equals(getConfig().getKey());
    }
    return false;
  }
}
