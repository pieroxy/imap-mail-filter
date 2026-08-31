package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.rules.matchers.MatchResult;
import net.pieroxy.imf.rules.matchers.Matcher;

import javax.mail.Message;
import javax.mail.MessagingException;

/**
 * Matche si au moins un des matchers enfants matche (court-circuite au premier succès).
 * Sans enfant, un OR est faux par convention.
 */
public class OrMatcher extends Matcher {
  @Override
  public MatchResult matches(Message message) throws MessagingException {
    for (Matcher child : getChildren()) {
      MatchResult result = child.matches(message);
      getLogger().fine(() -> "OR: child " + child.getClass().getSimpleName() + " -> " + result.matched());
      if (result.matched()) return matched(result.debugString());
    }
    return notMatched();
  }
}
