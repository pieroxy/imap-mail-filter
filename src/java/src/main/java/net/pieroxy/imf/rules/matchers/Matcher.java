package net.pieroxy.imf.rules.matchers;

import net.pieroxy.imf.config.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.logging.LogLevels;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.Collections;
import java.util.List;
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

  public abstract boolean matches(Message message) throws MessagingException;

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
    String name = Matcher.class.getName() + "." + config.getType()
            + (config.getKey() != null ? "[" + config.getKey() + "]" : "");
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

  /** Logger propre à ce noeud de config, dont le niveau suit son logLevel (INFO par défaut). */
  public Logger getLogger() {
    return logger;
  }
}
