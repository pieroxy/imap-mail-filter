package net.pieroxy.imf.config;

import java.util.List;

public class MailAccountConfiguration {
  private String host;
  private int port;
  private String username;
  private String password;
  private String displayName;
  /**
   * Time to sleep between two runs, in seconds.
   */
  private int runEvery;
  /**
   * Nom du dossier IMAP considéré comme spam pour le corpus classifieur (dépend du
   * fournisseur : "Spam" chez la plupart, "[Gmail]/Spam" chez Gmail, "Junk Email" chez
   * Outlook...). Défaut : "Spam" si absent/vide.
   */
  private String classifierSpamFolderName;
  /**
   * Noms de dossiers (n'importe où dans l'arbre, en plus d'INBOX et imf-rules/ qui le sont
   * déjà toujours) à exclure complètement du corpus classifieur : ni SPAM ni HAM, ignorés.
   * Utile par exemple pour un dossier dédié aux verdicts du classifieur lui-même (ex: "SpamML"
   * ou "Spam/ML"), pour qu'il ne s'auto-alimente pas en exemples d'entraînement.
   */
  private List<String> classifierExcludedFolders;

  private List<MailFilterRuleConfiguration> rules;


  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public int getRunEvery() {
    return runEvery;
  }

  public void setRunEvery(int runEvery) {
    this.runEvery = runEvery;
  }

  public String getClassifierSpamFolderName() {
    return classifierSpamFolderName;
  }

  public void setClassifierSpamFolderName(String classifierSpamFolderName) {
    this.classifierSpamFolderName = classifierSpamFolderName;
  }

  public List<String> getClassifierExcludedFolders() {
    return classifierExcludedFolders;
  }

  public void setClassifierExcludedFolders(List<String> classifierExcludedFolders) {
    this.classifierExcludedFolders = classifierExcludedFolders;
  }

  public List<MailFilterRuleConfiguration> getRules() {
    return rules;
  }

  public void setRules(List<MailFilterRuleConfiguration> rules) {
    this.rules = rules;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }
}
