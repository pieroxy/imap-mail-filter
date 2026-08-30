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
