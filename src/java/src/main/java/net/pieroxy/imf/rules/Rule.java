package net.pieroxy.imf.rules;

import net.pieroxy.imf.config.MailFilterRuleConfiguration;
import net.pieroxy.imf.rules.actions.Action;
import net.pieroxy.imf.rules.matchers.Matcher;
import net.pieroxy.imf.utils.MailTools;

import javax.mail.Message;
import java.util.logging.Level;

public class Rule {

  private final MailFilterRuleConfiguration config;
  private final Matcher matcher;
  private final Action action;

  public Rule(MailFilterRuleConfiguration config) {
    this.config = config;
    matcher = Matcher.build(config.getMatcher());
    action = Action.build(config.getAction());
  }

  /**
   * Journalise sur le logger propre à chaque noeud (matcher/action) : WARNING sur erreur,
   * INFO quand le matcher matche et quand l'action s'exécute.
   * @return true si le matcher a matché (indépendamment du succès de l'action).
   */
  public boolean apply(Message message) {
    boolean matched;
    try {
      matched = matcher.matches(message);
    } catch (Exception e) {
      matcher.getLogger().log(Level.WARNING, "Matcher failed on message from " + MailTools.describeFromSafely(message), e);
      return false;
    }
    if (!matched) {
      return false;
    }
    matcher.getLogger().info(() -> "Matched message from " + MailTools.describeFromSafely(message));

    try {
      boolean result = action.run(message);
      action.getLogger().info(() -> "Action applied (success=" + result + ") to message from " + MailTools.describeFromSafely(message));
    } catch (Exception e) {
      action.getLogger().log(Level.WARNING, "Action failed on message from " + MailTools.describeFromSafely(message), e);
    }
    return true;
  }
}
