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
   * Représentation compacte pour les logs de démarrage, ex:
   * {@code Rule(FROM_DOMAIN_EQUALS(toto.com),MOVE_TO(Work))}, ou
   * {@code Rule(...,...) [keepProcessing]} si la règle laisse l'évaluation continuer sur les
   * suivantes même quand elle matche.
   */
  public String describe() {
    String base = "Rule(" + matcher.describe() + "," + action.describe() + ")";
    return isKeepProcessing() ? base + " [keepProcessing]" : base;
  }

  /**
   * Journalise sur le logger propre à chaque noeud (matcher/action) : WARNING sur erreur,
   * INFO quand le matcher matche et quand l'action s'exécute.
   * @return true si le matcher a matché (indépendamment du succès de l'action).
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

  /** true si cette règle ne doit pas arrêter l'évaluation des suivantes même quand elle matche — voir {@link #applyFirstMatching}. */
  public boolean isKeepProcessing() {
    return config.isKeepProcessing();
  }

  /**
   * Applique la première règle de la liste qui matche message (partagé par le traitement de
   * l'INBOX et le rejeu manuel depuis imf-rules/ToProcess), sauf qu'une règle marquée
   * keepProcessing n'arrête pas la recherche : son action s'exécute quand même, mais
   * l'évaluation continue comme si elle n'avait pas matché. Une règle qui lève une exception ne
   * bloque pas les suivantes non plus.
   * @return true si au moins une règle a matché (keepProcessing ou non).
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
