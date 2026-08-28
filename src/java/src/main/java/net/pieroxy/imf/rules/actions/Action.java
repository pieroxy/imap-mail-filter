package net.pieroxy.imf.rules.actions;

import net.pieroxy.imf.config.MailFilterRuleActionConfiguration;
import net.pieroxy.imf.config.MailFilterRuleMatcherConfiguration;

import javax.mail.Message;
import javax.mail.MessagingException;

public abstract class Action {
  private MailFilterRuleActionConfiguration config;
  public abstract boolean run(Message message) throws MessagingException;
  public void setConfig(MailFilterRuleActionConfiguration config) {
    this.config = config;
  }
  protected MailFilterRuleActionConfiguration getConfig() {
    return config;
  }
}
