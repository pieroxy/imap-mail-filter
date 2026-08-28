package net.pieroxy.imf.config;

import net.pieroxy.imf.rules.matchers.MatcherType;

public class MailFilterRuleMatcherConfiguration {
  private MatcherType type;
  private String key;

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
}
