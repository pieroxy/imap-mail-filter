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
   * Recursively builds the action tree described by the config (composite actions like AND/OR
   * reference other actions via their "children").
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
    // Default = INFO: WARNING (errors) and INFO (action applied) must be visible without any
    // explicit configuration; only the DEBUG-level detail is an opt-in per node.
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

  /**
   * Compact representation of this action's tree for the startup logs (see
   * {@link net.pieroxy.imf.rules.RuleCatalog#logRules}), e.g. {@code MOVE_TO(Work)} or, for a
   * composite like {@code MOVE_TO_AND_READ}, {@code MOVE_TO_AND_READ(READ(),MOVE_TO(Work))}.
   */
  public String describe() {
    String type = config.getType().name();
    if (!children.isEmpty()) {
      return type + "(" + children.stream().map(Action::describe).collect(Collectors.joining(",")) + ")";
    }
    return type + "(" + (config.getKey() != null ? config.getKey() : "") + ")";
  }

  /** This config node's own logger, whose level follows its logLevel (INFO by default). */
  public Logger getLogger() {
    return logger;
  }
}
