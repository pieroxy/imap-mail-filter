package net.pieroxy.imf.config;

import java.util.List;

public class Configuration {
  private List<MailAccountConfiguration> configurations;
  private String dataFolder;
  private String logFile;

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

  public String getLogFile() {
    return logFile;
  }

  public void setLogFile(String logFile) {
    this.logFile = logFile;
  }
}
