package net.pieroxy.imf.rules.matchers;

import net.pieroxy.imf.config.MailFilterRuleMatcherConfiguration;

import javax.mail.Message;
import javax.mail.MessagingException;

public abstract class Matcher {
  private MailFilterRuleMatcherConfiguration config;
  public abstract boolean matches(Message message) throws MessagingException;
  public void setConfig(MailFilterRuleMatcherConfiguration config) {
    this.config = config;
  }
  protected MailFilterRuleMatcherConfiguration getConfig() {
    return config;
  }
}
