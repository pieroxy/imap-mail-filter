package net.pieroxy.imf.rules;

import net.pieroxy.imf.config.MailFilterRuleConfiguration;
import net.pieroxy.imf.rules.actions.Action;
import net.pieroxy.imf.rules.matchers.MatchResult;
import net.pieroxy.imf.rules.matchers.Matcher;
import net.pieroxy.imf.utils.MailTools;

import javax.mail.Message;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Rule {

  private final MailFilterRuleConfiguration config;
  private final Matcher matcher;
  private final Action action;

  public Rule(MailFilterRuleConfiguration config) {
    this.config = config;
    matcher = Matcher.build(config.getMatcher());
    action = Action.build(config.getAction());
  }

  /**
   * Compact representation for the startup logs, e.g.
   * {@code Rule(FROM_DOMAIN_EQUALS(toto.com),MOVE_TO(Work))}, or
   * {@code Rule(...,...) [keepProcessing]} if the rule lets evaluation carry on to the following
   * rules even when it matches.
   */
  public String describe() {
    String base = "Rule(" + matcher.describe() + "," + action.describe() + ")";
    return isKeepProcessing() ? base + " [keepProcessing]" : base;
  }

  /**
   * Logs on each node's own logger (matcher/action): WARNING on error, INFO when the matcher
   * matches and when the action runs.
   * @return true if the matcher matched (regardless of whether the action succeeded).
   */
  public boolean apply(Message message) {
    MatchResult matchResult;
    try {
      matchResult = matcher.matches(message);
    } catch (Exception e) {
      matcher.getLogger().log(Level.WARNING, "Matcher failed on message from " + MailTools.describeFromSafely(message), e);
      return false;
    }
    if (!matchResult.matched()) {
      return false;
    }
    matcher.getLogger().info(() -> matchResult.debugString() + " matched message from " + MailTools.describeFromSafely(message));

    try {
      boolean result = action.run(message);
      action.getLogger().info(() -> "Action applied (success=" + result + ") to message from " + MailTools.describeFromSafely(message));
    } catch (Exception e) {
      action.getLogger().log(Level.WARNING, "Action failed on message from " + MailTools.describeFromSafely(message), e);
    }
    return true;
  }

  /** true if this rule shouldn't stop evaluation of the following ones even when it matches — see {@link #applyFirstMatching}. */
  public boolean isKeepProcessing() {
    return config.isKeepProcessing();
  }

  /**
   * Applies the first rule in the list that matches message (shared by INBOX processing and
   * manual replay from imf-rules/ToProcess), except a rule marked keepProcessing doesn't stop
   * the search: its action still runs, but evaluation carries on as if it hadn't matched. A rule
   * that throws an exception doesn't block the following ones either.
   * @return true if at least one rule matched (keepProcessing or not).
   */
  public static boolean applyFirstMatching(List<Rule> rules, Message message, Logger logger, String context) {
    boolean anyMatched = false;
    for (Rule rule : rules) {
      try {
        if (rule.apply(message)) {
          anyMatched = true;
          if (!rule.isKeepProcessing()) {
            return true;
          }
        }
      } catch (Exception e) {
        logger.log(Level.WARNING, "Rule failed on " + context + " for message from " + MailTools.describeFromSafely(message), e);
      }
    }
    return anyMatched;
  }
}
