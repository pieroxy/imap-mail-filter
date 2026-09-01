package net.pieroxy.imf.rules;

import net.pieroxy.imf.config.MailFilterRuleConfiguration;
import net.pieroxy.imf.learning.LearnedRulesStore;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Construit et met en cache la liste des {@link Rule} (config manuelle + règles apprises),
 * pour éviter de reconstruire l'arbre Matcher/Action à chaque message inspecté. La
 * (re)construction n'a lieu qu'au premier accès, puis après chaque appel à
 * {@link #invalidate()} — typiquement quand une nouvelle règle vient d'être apprise ce cycle.
 */
public class RuleCatalog {
  private final List<MailFilterRuleConfiguration> manualRules;
  private final LearnedRulesStore learnedRulesStore;
  private List<Rule> rules;

  public RuleCatalog(List<MailFilterRuleConfiguration> manualRules, LearnedRulesStore learnedRulesStore) {
    this.manualRules = manualRules != null ? manualRules : List.of();
    this.learnedRulesStore = learnedRulesStore;
  }

  /** Construit au premier appel, puis renvoie la même liste tant que invalidate() n'a pas été appelé. */
  public List<Rule> get() {
    List<Rule> current = rules;
    if (current == null) {
      current = build();
      rules = current;
    }
    return current;
  }

  /** Force la reconstruction (config manuelle + règles apprises relues du disque) au prochain get(). */
  public void invalidate() {
    rules = null;
  }

  /**
   * Journalise, dans l'ordre d'évaluation (voir {@link Rule#applyFirstMatching}), une ligne par
   * règle du catalogue — les règles de {@code config.json} d'abord, puis, séparées visuellement,
   * les règles apprises (voir {@link #build}, qui les concatène dans le même ordre). Le tout en
   * un seul appel à {@code logger.info} (un seul {@link java.util.logging.LogRecord}, donc un
   * seul appel à {@code Handler.publish} — synchronisé côté JDK) pour que le bloc entier
   * s'écrive d'un bloc et ne s'entrelace jamais avec celui d'un autre compte journalisant en
   * parallèle sur son propre thread (voir {@link MailAccount#run}).
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
    sb.setLength(sb.length() - System.lineSeparator().length()); // pas de ligne vide finale
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
