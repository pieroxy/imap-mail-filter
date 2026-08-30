package net.pieroxy.imf.rules;

import net.pieroxy.imf.config.MailFilterRuleConfiguration;
import net.pieroxy.imf.learning.LearnedRulesStore;

import java.util.ArrayList;
import java.util.List;
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

  private List<Rule> build() {
    List<MailFilterRuleConfiguration> configs = new ArrayList<>(manualRules);
    configs.addAll(learnedRulesStore.load());
    return configs.stream().map(Rule::new).collect(Collectors.toList());
  }
}
