package net.pieroxy.imf.config;

import net.pieroxy.imf.rules.actions.ActionType;

import java.util.List;

public class MailFilterRuleActionConfiguration {
  private ActionType type;
  private String key;
  /**
   * Uniquement renseigné pour les actions composites (AND / OR).
   */
  private List<MailFilterRuleActionConfiguration> children;

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

  public List<MailFilterRuleActionConfiguration> getChildren() {
    return children;
  }

  public void setChildren(List<MailFilterRuleActionConfiguration> children) {
    this.children = children;
  }
}
