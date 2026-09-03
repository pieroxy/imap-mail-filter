package net.pieroxy.imf.config;

import java.util.List;

public class Configuration {
  private List<MailAccountConfiguration> configurations;
  private String dataFolder;
  private String logFile;
  private int keepLogFiles;
  /** Number of days of classifier corpus files to keep (0 or absent = disabled). */
  private int classifierCorpusRetentionDays;
  /**
   * Cap on messages fetched/processed in one classifier corpus scan cycle (0 or absent =
   * {@link net.pieroxy.imf.classifier.ClassifierCorpusScanner}'s built-in default of 500).
   * Bounds how much of the account's IMAP connection a single cycle can monopolize when
   * catching up on a large backlog (e.g. the first scan of a folder with years of history) —
   * lower it on a slow link or server, raise it to catch up faster on a fast one.
   */
  private int classifierCorpusScanBatchSize;
  /** IP/domain reputation sources (see {@link ReputationListConfig}) — absent = feature disabled. */
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

  public int getClassifierCorpusScanBatchSize() {
    return classifierCorpusScanBatchSize;
  }

  public void setClassifierCorpusScanBatchSize(int classifierCorpusScanBatchSize) {
    this.classifierCorpusScanBatchSize = classifierCorpusScanBatchSize;
  }

  public List<ReputationListConfig> getReputationLists() {
    return reputationLists;
  }

  public void setReputationLists(List<ReputationListConfig> reputationLists) {
    this.reputationLists = reputationLists;
  }
}
