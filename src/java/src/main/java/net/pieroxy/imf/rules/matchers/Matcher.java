package net.pieroxy.imf.rules.matchers;

import net.pieroxy.imf.config.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.logging.LogLevels;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public abstract class Matcher {
  private MailFilterRuleMatcherConfiguration config;
  private List<Matcher> children = Collections.emptyList();
  private Logger logger = Logger.getLogger(Matcher.class.getName());

  /**
   * Construit récursivement l'arbre de matchers décrit par la config (les matchers
   * composites comme AND/OR référencent d'autres matchers via leurs "children").
   */
  public static Matcher build(MailFilterRuleMatcherConfiguration config) {
    Matcher matcher = config.getType().getImplementation();
    matcher.setConfig(config);
    if (config.getChildren() != null) {
      matcher.children = config.getChildren().stream().map(Matcher::build).collect(Collectors.toList());
    }
    return matcher;
  }

  public abstract MatchResult matches(Message message) throws MessagingException;

  /**
   * Calcule la clé de config à partir d'un message d'exemple (apprentissage de règle via les
   * dossiers imf-rules/). Seuls les matchers "feuille" (voir {@link net.pieroxy.imf.rules.matchers.MatcherType#learnableValues()})
   * ont besoin de la redéfinir ; les composites ne sont jamais sollicités pour ça.
   */
  public String extractKeyFromExample(Message message) throws MessagingException {
    throw new UnsupportedOperationException(getClass().getSimpleName() + " does not support learning a rule from an example message");
  }

  public void setConfig(MailFilterRuleMatcherConfiguration config) {
    this.config = config;
    String name = Matcher.class.getName() + "." + config.getType() + "[" + describeKey() + "]";
    this.logger = Logger.getLogger(name);
    // Défaut = INFO : WARNING (erreurs) et INFO (matché) doivent être visibles sans configuration
    // explicite ; seul le détail DEBUG de chaque test de matching est un opt-in par nœud.
    this.logger.setLevel(LogLevels.parse(config.getLogLevel(), Level.INFO));
  }
  protected MailFilterRuleMatcherConfiguration getConfig() {
    return config;
  }
  protected List<Matcher> getChildren() {
    return children;
  }

  /** Pour les logs : la clé si key est utilisé, sinon un résumé de la taille de keys. */
  protected String describeKey() {
    if (config.getKeys() != null) return config.getKeys().size() + " keys";
    return config.getKey() != null ? config.getKey() : "";
  }

  /**
   * Teste candidate contre la config du matcher (keys si renseigné, sinon key), avec la
   * fonction de comparaison fournie (equals, equalsIgnoreCase...), et renvoie la clé
   * configurée qui a matché (utile pour {@link #matched}, notamment quand plusieurs "keys"
   * sont configurées et qu'on veut savoir laquelle a précisément fait mouche). Factorise ce
   * qui, sans ça, serait dupliqué dans chaque matcher "feuille" apprenant plusieurs clés pour
   * la même action (voir {@link net.pieroxy.imf.learning.LearnedRulesStore}).
   */
  protected Optional<String> matchingKey(String candidate, BiPredicate<String, String> comparator) {
    if (candidate == null) return Optional.empty();
    if (config.getKeys() != null) {
      return config.getKeys().stream().filter(k -> comparator.test(candidate, k)).findFirst();
    }
    if (config.getKey() != null && comparator.test(candidate, config.getKey())) {
      return Optional.of(config.getKey());
    }
    return Optional.empty();
  }

  /** Un match, avec une description lisible ("NomDeLaClasse(détail)") pour les logs. */
  protected MatchResult matched(String debugDetail) {
    return MatchResult.matched(getClass().getSimpleName() + "(" + debugDetail + ")");
  }

  protected MatchResult notMatched() {
    return MatchResult.notMatched();
  }

  /** Logger propre à ce noeud de config, dont le niveau suit son logLevel (INFO par défaut). */
  public Logger getLogger() {
    return logger;
  }
}
