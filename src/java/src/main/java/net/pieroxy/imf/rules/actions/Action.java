package net.pieroxy.imf.rules.actions;

import net.pieroxy.imf.config.MailFilterRuleActionConfiguration;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public abstract class Action {
  private MailFilterRuleActionConfiguration config;
  private List<Action> children = Collections.emptyList();

  /**
   * Construit récursivement l'arbre d'actions décrit par la config (les actions
   * composites comme AND/OR référencent d'autres actions via leurs "children").
   */
  public static Action build(MailFilterRuleActionConfiguration config) {
    Action action = config.getType().getImplementation();
    action.setConfig(config);
    if (config.getChildren() != null) {
      action.children = config.getChildren().stream().map(Action::build).collect(Collectors.toList());
    }
    return action;
  }

  public abstract boolean run(Message message) throws MessagingException;

  public void setConfig(MailFilterRuleActionConfiguration config) {
    this.config = config;
  }
  protected MailFilterRuleActionConfiguration getConfig() {
    return config;
  }
  protected List<Action> getChildren() {
    return children;
  }
}
