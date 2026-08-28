package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.rules.matchers.Matcher;

import javax.mail.Message;
import javax.mail.MessagingException;

/**
 * Matche si au moins un des matchers enfants matche (court-circuite au premier succès).
 * Sans enfant, un OR est faux par convention.
 */
public class OrMatcher extends Matcher {
  @Override
  public boolean matches(Message message) throws MessagingException {
    for (Matcher child : getChildren()) {
      if (child.matches(message)) return true;
    }
    return false;
  }
}
