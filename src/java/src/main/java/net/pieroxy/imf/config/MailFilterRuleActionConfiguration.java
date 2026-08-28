package net.pieroxy.imf.config;

import net.pieroxy.imf.rules.actions.ActionType;
import net.pieroxy.imf.rules.matchers.MatcherType;

public class MailFilterRuleActionConfiguration {
  private ActionType type;
  private String key;

  public ActionType getType() {
    return type;
  }

  public void setType(ActionType type) {
    this.type = type;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }
}
