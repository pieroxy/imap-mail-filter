package net.pieroxy.imf.config;

import net.pieroxy.imf.rules.matchers.MatcherType;

import java.util.List;

public class MailFilterRuleMatcherConfiguration {
  private MatcherType type;
  private String key;
  /**
   * Uniquement renseigné pour les matchers composites (AND / OR).
   */
  private List<MailFilterRuleMatcherConfiguration> children;
  /**
   * Niveau de log pour ce noeud (DEBUG/INFO/WARNING/ERROR), optionnel. Défaut : WARNING.
   */
  private String logLevel;

  public MatcherType getType() {
    return type;
  }

  public void setType(MatcherType type) {
    this.type = type;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public List<MailFilterRuleMatcherConfiguration> getChildren() {
    return children;
  }

  public void setChildren(List<MailFilterRuleMatcherConfiguration> children) {
    this.children = children;
  }

  public String getLogLevel() {
    return logLevel;
  }

  public void setLogLevel(String logLevel) {
    this.logLevel = logLevel;
  }
}
