package net.pieroxy.imf.classifier;

import java.util.List;

/**
 * Un enregistrement du corpus d'entraînement : de quoi nourrir un futur classifieur (subject,
 * from/to avec leur display name, IP d'origine best-effort), étiqueté SPAM/HAM d'après le
 * dossier IMAP d'où il vient.
 */
public class ClassifierExample {
  private String fetchDate;
  private String mailDate;
  private List<String> from;
  private String fromDisplayName;
  private List<String> to;
  private String toDisplayName;
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

  /** Display name(s) du/des expéditeur(s) ("Alice" pour "Alice &lt;alice@example.com&gt;"), joints par un espace ; null si aucun. */
  public String getFromDisplayName() {
    return fromDisplayName;
  }

  public void setFromDisplayName(String fromDisplayName) {
    this.fromDisplayName = fromDisplayName;
  }

  /** Display name(s) du/des destinataire(s) To, joints par un espace ; null si aucun. */
  public String getToDisplayName() {
    return toDisplayName;
  }

  public void setToDisplayName(String toDisplayName) {
    this.toDisplayName = toDisplayName;
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
