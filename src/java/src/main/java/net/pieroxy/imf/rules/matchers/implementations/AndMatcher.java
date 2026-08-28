package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.rules.matchers.Matcher;

import javax.mail.Message;
import javax.mail.MessagingException;

/**
 * Matche si tous les matchers enfants matchent (court-circuite au premier échec).
 * Sans enfant, un AND est vrai par convention (vacuous truth).
 */
public class AndMatcher extends Matcher {
  @Override
  public boolean matches(Message message) throws MessagingException {
    for (Matcher child : getChildren()) {
      if (!child.matches(message)) return false;
    }
    return true;
  }
}
