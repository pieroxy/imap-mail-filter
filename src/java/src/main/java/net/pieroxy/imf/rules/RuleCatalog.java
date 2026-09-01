package net.pieroxy.imf.rules;

import net.pieroxy.imf.config.MailFilterRuleConfiguration;
import net.pieroxy.imf.learning.LearnedRulesStore;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Builds and caches the list of {@link Rule}s (manual config + learned rules), to avoid
 * rebuilding the Matcher/Action tree on every inspected message. (Re)building only happens on
 * first access, then after each call to {@link #invalidate()} — typically when a new rule was
 * just learned this cycle.
 */
public class RuleCatalog {
  private final List<MailFilterRuleConfiguration> manualRules;
  private final LearnedRulesStore learnedRulesStore;
  private List<Rule> rules;

  public RuleCatalog(List<MailFilterRuleConfiguration> manualRules, LearnedRulesStore learnedRulesStore) {
    this.manualRules = manualRules != null ? manualRules : List.of();
    this.learnedRulesStore = learnedRulesStore;
  }

  /** Builds on first call, then returns the same list until invalidate() is called. */
  public List<Rule> get() {
    List<Rule> current = rules;
    if (current == null) {
      current = build();
      rules = current;
    }
    return current;
  }

  /** Forces a rebuild (manual config + learned rules re-read from disk) on the next get(). */
  public void invalidate() {
    rules = null;
  }

  /**
   * Logs, in evaluation order (see {@link Rule#applyFirstMatching}), one line per rule in the
   * catalog — {@code config.json} rules first, then, visually separated, the learned rules (see
   * {@link #build}, which concatenates them in the same order). All in a single call to
   * {@code logger.info} (a single {@link java.util.logging.LogRecord}, hence a single call to
   * {@code Handler.publish} — synchronized on the JDK side) so the whole block is written as one
   * unit and never interleaves with another account logging in parallel on its own thread (see
   * {@link MailAccount#run}).
   */
  public void logRules(Logger logger, String accountLabel) {
    List<Rule> current = get();
    int manualCount = manualRules.size();
    StringBuilder sb = new StringBuilder();
    sb.append("Rules for account ").append(accountLabel).append(':').append(System.lineSeparator());
    sb.append("  Rules from config.json (").append(manualCount).append("):").append(System.lineSeparator());
    appendRuleRange(sb, current, 0, manualCount);
    sb.append("  Learned rules (").append(current.size() - manualCount).append("):").append(System.lineSeparator());
    appendRuleRange(sb, current, manualCount, current.size());
    sb.setLength(sb.length() - System.lineSeparator().length()); // no trailing blank line
    logger.info(sb.toString());
  }

  private static void appendRuleRange(StringBuilder sb, List<Rule> rules, int from, int to) {
    if (from == to) {
      sb.append("    (none)").append(System.lineSeparator());
      return;
    }
    for (int i = from; i < to; i++) {
      sb.append("    ").append(rules.get(i).describe()).append(System.lineSeparator());
    }
  }

  private List<Rule> build() {
    List<MailFilterRuleConfiguration> configs = new ArrayList<>(manualRules);
    configs.addAll(learnedRulesStore.load());
    return configs.stream().map(Rule::new).collect(Collectors.toList());
  }
}
