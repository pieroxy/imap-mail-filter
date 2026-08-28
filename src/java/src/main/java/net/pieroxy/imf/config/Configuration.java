package net.pieroxy.imf.config;

import java.util.List;

public class Configuration {
  private List<MailAccountConfiguration> configurations;
  private String dataFolder;

  public List<MailAccountConfiguration> getConfigurations() {
    return configurations;
  }

  public void setConfigurations(List<MailAccountConfiguration> configurations) {
    this.configurations = configurations;
  }

  public String getDataFolder() {
    return dataFolder;
  }

  public void setDataFolder(String dataFolder) {
    this.dataFolder = dataFolder;
  }
}
