package net.pieroxy.imf.rules;

import net.pieroxy.imf.config.MailFilterRuleConfiguration;
import net.pieroxy.imf.rules.actions.Action;
import net.pieroxy.imf.rules.matchers.Matcher;

public class Rule {

  private final MailFilterRuleConfiguration config;
  private final Matcher matcher;
  private final Action action;

  public Rule(MailFilterRuleConfiguration config) {
    this.config = config;
    matcher = config.getMatcher().getType().getImplementation();
    matcher.setConfig(config.getMatcher());
    action = config.getAction().getType().getImplementation();
    action.setConfig(config.getAction());
  }
}
