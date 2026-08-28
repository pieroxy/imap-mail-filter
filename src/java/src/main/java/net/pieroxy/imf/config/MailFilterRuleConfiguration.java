package net.pieroxy.imf.config;

public class MailFilterRuleConfiguration {
  private MailFilterRuleMatcherConfiguration matcher;
  private MailFilterRuleActionConfiguration action;

  public MailFilterRuleMatcherConfiguration getMatcher() {
    return matcher;
  }

  public void setMatcher(MailFilterRuleMatcherConfiguration matcher) {
    this.matcher = matcher;
  }

  public MailFilterRuleActionConfiguration getAction() {
    return action;
  }

  public void setAction(MailFilterRuleActionConfiguration action) {
    this.action = action;
  }
}
