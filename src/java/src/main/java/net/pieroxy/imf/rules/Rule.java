package net.pieroxy.imf.rules;

import net.pieroxy.imf.config.MailFilterRuleConfiguration;
import net.pieroxy.imf.rules.actions.Action;
import net.pieroxy.imf.rules.matchers.Matcher;

import javax.mail.Message;
import javax.mail.MessagingException;

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
   * @return true si le matcher a matché (indépendamment du succès de l'action).
   */
  public boolean apply(Message message) throws MessagingException {
    if (matcher.matches(message)) {
      action.run(message);
      return true;
    }
    return false;
  }
}
