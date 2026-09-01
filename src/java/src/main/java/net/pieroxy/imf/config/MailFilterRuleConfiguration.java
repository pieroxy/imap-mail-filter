package net.pieroxy.imf.config;

public class MailFilterRuleConfiguration {
  private MailFilterRuleMatcherConfiguration matcher;
  private MailFilterRuleActionConfiguration action;
  /**
   * By default (false), the first rule that matches stops evaluation for this message — see
   * Rule.applyFirstMatching(). When true, its action still runs, but evaluation continues on to
   * the following rules as if this one hadn't matched: useful for a rule that only
   * observes/logs (e.g. comparing a new classifier against the old one) without ever blocking
   * the real rules that would otherwise apply afterward.
   */
  private boolean keepProcessing;

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

  public boolean isKeepProcessing() {
    return keepProcessing;
  }

  public void setKeepProcessing(boolean keepProcessing) {
    this.keepProcessing = keepProcessing;
  }
}
