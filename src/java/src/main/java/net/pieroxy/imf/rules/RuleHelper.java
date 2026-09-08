package net.pieroxy.imf.rules;

import net.pieroxy.imf.utils.MailTools;

import javax.mail.Message;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Walks an ordered list of {@link RuleInterface}s against a message. */
public class RuleHelper {
  private RuleHelper() {}

  /**
   * Core loop, reusable both at the top level (a whole account's rules) and inside a group like
   * {@link LearnedRulesGroupRule} (a sub-list of learned rules): applies rules in order until
   * one stops evaluation (a rule that matched without {@code keepProcessing}), or the list is
   * exhausted. A rule that throws doesn't block the following ones either.
   */
  public static RuleExecutionResult evaluate(List<RuleInterface> rules, Message message, Logger logger, String context) {
    boolean anyMatched = false;
    boolean learnedRulesExecuted = false;
    for (RuleInterface rule : rules) {
      try {
        RuleExecutionResult result = rule.apply(message);
        learnedRulesExecuted |= result.learnedRulesExecuted();
        if (result.ruleApplied()) {
          anyMatched = true;
          if (!result.keepProcessing()) {
            return new RuleExecutionResult(true, false, learnedRulesExecuted);
          }
        }
      } catch (Exception e) {
        logger.log(Level.WARNING, "Rule failed on " + context + " for message from " + MailTools.describeFromSafely(message), e);
      }
    }
    return new RuleExecutionResult(anyMatched, true, learnedRulesExecuted);
  }

  /**
   * Top-level entry point (shared by INBOX processing and manual replay from
   * imf-rules/ToProcess): runs rules via {@link #evaluate}, then, only if evaluation reached the
   * natural end of the list (no blocking match occurred) and the learned rules never got a
   * chance to run along the way, falls back to running learnedRulesFallback now. This is what
   * keeps every {@code config.json} that doesn't place a {@code LEARNED_RULES} entry explicitly
   * behaving exactly as it did before that type existed — learned rules still always run, just
   * implicitly, after everything else.
   * @return true if at least one rule matched (in rules or in the learned-rules fallback).
   */
  public static boolean processRules(List<RuleInterface> rules, RuleInterface learnedRulesFallback, Message message, Logger logger, String context) {
    RuleExecutionResult result = evaluate(rules, message, logger, context);
    if (!result.keepProcessing()) {
      return true; // a blocking rule already matched: never touch the learned rules afterward
    }
    if (!result.learnedRulesExecuted()) {
      // Bitwise |, not ||: the fallback must run even if a keepProcessing rule already matched
      // above (result.ruleApplied() true doesn't mean we can skip it, unlike the early return).
      return result.ruleApplied() | learnedRulesFallback.apply(message).ruleApplied();
    }
    return result.ruleApplied();
  }
}
