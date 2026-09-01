package net.pieroxy.imf.config;

import java.util.List;

public class Configuration {
  private List<MailAccountConfiguration> configurations;
  private String dataFolder;
  private String logFile;
  private int keepLogFiles;
  /** Nombre de jours de fichiers du corpus classifieur à garder (0 ou absent = désactivé). */
  private int classifierCorpusRetentionDays;
  /** Sources de réputation IP/domaine (voir {@link ReputationListConfig}) — absent = fonctionnalité désactivée. */
  private List<ReputationListConfig> reputationLists;

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

  public int getKeepLogFiles() {
    return keepLogFiles;
  }

  public void setKeepLogFiles(int keepLogFiles) {
    this.keepLogFiles = keepLogFiles;
  }

  public int getClassifierCorpusRetentionDays() {
    return classifierCorpusRetentionDays;
  }

  public void setClassifierCorpusRetentionDays(int classifierCorpusRetentionDays) {
    this.classifierCorpusRetentionDays = classifierCorpusRetentionDays;
  }

  public List<ReputationListConfig> getReputationLists() {
    return reputationLists;
  }

  public void setReputationLists(List<ReputationListConfig> reputationLists) {
    this.reputationLists = reputationLists;
  }
}
