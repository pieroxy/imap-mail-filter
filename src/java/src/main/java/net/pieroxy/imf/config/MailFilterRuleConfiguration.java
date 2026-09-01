package net.pieroxy.imf.config;

public class MailFilterRuleConfiguration {
  private MailFilterRuleMatcherConfiguration matcher;
  private MailFilterRuleActionConfiguration action;
  /**
   * Par défaut (false), la première règle qui matche arrête l'évaluation pour ce message — voir
   * Rule.applyFirstMatching(). À true, son action s'exécute quand même, mais l'évaluation
   * continue sur les règles suivantes comme si celle-ci n'avait pas matché : utile pour une
   * règle qui se contente d'observer/journaliser (ex: comparer un nouveau classifieur à
   * l'ancien) sans jamais empêcher les règles réelles de s'appliquer ensuite.
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
