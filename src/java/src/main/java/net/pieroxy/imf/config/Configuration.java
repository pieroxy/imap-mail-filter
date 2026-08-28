package net.pieroxy.imf.config;

import java.util.List;

public class Configuration {
  private List<MailAccountConfiguration> configurations;

  public List<MailAccountConfiguration> getConfigurations() {
    return configurations;
  }

  public void setConfigurations(List<MailAccountConfiguration> configurations) {
    this.configurations = configurations;
  }
}
