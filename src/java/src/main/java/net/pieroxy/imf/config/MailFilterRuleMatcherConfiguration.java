package net.pieroxy.imf.config;

import net.pieroxy.imf.rules.matchers.MatcherType;

import java.util.List;
import java.util.Set;

public class MailFilterRuleMatcherConfiguration {
  private MatcherType type;
  private String key;
  /**
   * Alternative à key quand plusieurs clés déclenchent la même action (ex: N adresses
   * différentes toutes envoyées vers Spam) : évite une règle entière dupliquée par clé.
   * Un matcher utilise celui des deux qui est non-nul (keys prioritaire s'il est renseigné).
   */
  private Set<String> keys;
  /**
   * Uniquement renseigné pour les matchers composites (AND / OR).
   */
  private List<MailFilterRuleMatcherConfiguration> children;
  /**
   * Niveau de log pour ce noeud (DEBUG/INFO/WARNING/ERROR), optionnel. Défaut : WARNING.
   */
  private String logLevel;
  /**
   * Uniquement pour IP_REPUTATION_EQUALS / FROM_DOMAIN_REPUTATION_EQUALS : les "id" (voir
   * {@link Configuration#getReputationLists()}) des listes de réputation à consulter. Si la
   * valeur testée est trouvée dans plusieurs d'entre elles, le score retenu est le pire (max).
   */
  private Set<String> listIds;

  public MatcherType getType() {
    return type;
  }

  public void setType(MatcherType type) {
    this.type = type;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public Set<String> getKeys() {
    return keys;
  }

  public void setKeys(Set<String> keys) {
    this.keys = keys;
  }

  public List<MailFilterRuleMatcherConfiguration> getChildren() {
    return children;
  }

  public void setChildren(List<MailFilterRuleMatcherConfiguration> children) {
    this.children = children;
  }

  public String getLogLevel() {
    return logLevel;
  }

  public void setLogLevel(String logLevel) {
    this.logLevel = logLevel;
  }

  public Set<String> getListIds() {
    return listIds;
  }

  public void setListIds(Set<String> listIds) {
    this.listIds = listIds;
  }
}
