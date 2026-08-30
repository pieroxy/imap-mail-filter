package net.pieroxy.imf.classifier;

import java.util.List;

/**
 * Un enregistrement du corpus d'entraînement : de quoi nourrir un futur classifieur (subject,
 * from, to, IP d'origine best-effort), étiqueté SPAM/HAM d'après le dossier IMAP d'où il vient.
 */
public class ClassifierExample {
  private String fetchDate;
  private String mailDate;
  private List<String> from;
  private List<String> to;
  private String subject;
  private String ip;
  private ClassifierLabel label;

  public String getFetchDate() {
    return fetchDate;
  }

  public void setFetchDate(String fetchDate) {
    this.fetchDate = fetchDate;
  }

  public String getMailDate() {
    return mailDate;
  }

  public void setMailDate(String mailDate) {
    this.mailDate = mailDate;
  }

  public List<String> getFrom() {
    return from;
  }

  public void setFrom(List<String> from) {
    this.from = from;
  }

  public List<String> getTo() {
    return to;
  }

  public void setTo(List<String> to) {
    this.to = to;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  public String getIp() {
    return ip;
  }

  public void setIp(String ip) {
    this.ip = ip;
  }

  public ClassifierLabel getLabel() {
    return label;
  }

  public void setLabel(ClassifierLabel label) {
    this.label = label;
  }
}
