package net.pieroxy.imf.config;

import net.pieroxy.imf.reputation.ReputationListType;

/**
 * Une entrée de {@code reputationLists} dans la config globale (voir {@link Configuration}) :
 * une source de réputation à télécharger et rafraîchir périodiquement, jamais interrogée en
 * direct par message. {@code score} (0=ok, 1=spam) est la valeur attribuée à toute IP/domaine
 * trouvé dans cette liste ; quand un matcher référence plusieurs listes, le score retenu est le
 * pire (max) parmi celles qui contiennent la valeur testée.
 */
public class ReputationListConfig {
  private String id;
  private ReputationListType type;
  private String url;
  private int refreshHours;
  private double score;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public ReputationListType getType() {
    return type;
  }

  public void setType(ReputationListType type) {
    this.type = type;
  }

  /** http(s):// pour un téléchargement distant, ou file:// pour un fichier local. */
  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public int getRefreshHours() {
    return refreshHours;
  }

  public void setRefreshHours(int refreshHours) {
    this.refreshHours = refreshHours;
  }

  public double getScore() {
    return score;
  }

  public void setScore(double score) {
    this.score = score;
  }
}
