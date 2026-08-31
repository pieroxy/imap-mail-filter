package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.rules.matchers.MatchResult;
import net.pieroxy.imf.rules.matchers.Matcher;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.ArrayList;
import java.util.List;

/**
 * Matche si tous les matchers enfants matchent (court-circuite au premier échec).
 * Sans enfant, un AND est vrai par convention (vacuous truth).
 */
public class AndMatcher extends Matcher {
  @Override
  public MatchResult matches(Message message) throws MessagingException {
    List<String> details = new ArrayList<>();
    for (Matcher child : getChildren()) {
      MatchResult result = child.matches(message);
      getLogger().fine(() -> "AND: child " + child.getClass().getSimpleName() + " -> " + result.matched());
      if (!result.matched()) return notMatched();
      details.add(result.debugString());
    }
    return matched(String.join(", ", details));
  }
}
