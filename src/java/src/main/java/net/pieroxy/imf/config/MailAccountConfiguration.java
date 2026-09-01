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
   * Name of the IMAP folder considered spam for the classifier corpus (varies by provider:
   * "Spam" for most, "[Gmail]/Spam" for Gmail, "Junk Email" for Outlook...). Default: "Spam"
   * if absent/blank.
   */
  private String classifierSpamFolderName;
  /**
   * Folder names (anywhere in the tree, in addition to INBOX and imf-rules/ which are always
   * excluded already) to exclude entirely from the classifier corpus: neither SPAM nor HAM,
   * ignored. Useful for example for a folder dedicated to the classifier's own verdicts (e.g.
   * "SpamML" or "Spam/ML"), so it doesn't feed itself training examples.
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
