package net.pieroxy.imf.rules.actions;

import net.pieroxy.imf.config.MailFilterRuleActionConfiguration;
import net.pieroxy.imf.logging.LogLevels;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public abstract class Action {
  private MailFilterRuleActionConfiguration config;
  private List<Action> children = Collections.emptyList();
  private Logger logger = Logger.getLogger(Action.class.getName());

  /**
   * Construit récursivement l'arbre d'actions décrit par la config (les actions
   * composites comme AND/OR référencent d'autres actions via leurs "children").
   */
  public static Action build(MailFilterRuleActionConfiguration config) {
    Action action = config.getType().getImplementation();
    action.setConfig(config);
    if (config.getChildren() != null) {
      action.setChildren(config.getChildren().stream().map(Action::build).collect(Collectors.toList()));
    }
    return action;
  }

  public abstract boolean run(Message message) throws MessagingException;

  public void setConfig(MailFilterRuleActionConfiguration config) {
    this.config = config;
    String name = Action.class.getName() + "." + config.getType()
            + (config.getKey() != null ? "[" + config.getKey() + "]" : "");
    this.logger = Logger.getLogger(name);
    // Défaut = INFO : WARNING (erreurs) et INFO (action appliquée) doivent être visibles sans
    // configuration explicite ; seul le détail DEBUG est un opt-in par nœud.
    this.logger.setLevel(LogLevels.parse(config.getLogLevel(), Level.INFO));
  }
  protected MailFilterRuleActionConfiguration getConfig() {
    return config;
  }
  protected List<Action> getChildren() {
    return children;
  }
  protected void setChildren(List<Action> children) {
    this.children = children;
  }

  /** Logger propre à ce noeud de config, dont le niveau suit son logLevel (INFO par défaut). */
  public Logger getLogger() {
    return logger;
  }
}
