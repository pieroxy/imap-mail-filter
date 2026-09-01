package net.pieroxy.imf.classifier;

import java.util.List;

/**
 * Un enregistrement du corpus d'entraînement : de quoi nourrir un futur classifieur (subject,
 * from/to avec leur display name, IP d'origine best-effort, quelques en-têtes de plus —
 * In-Reply-To/References, Precedence, List-Id, List-Unsubscribe, cohérence Return-Path/Reply-To
 * avec From), étiqueté SPAM/HAM d'après le dossier IMAP d'où il vient.
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
  private boolean reply;
  private String precedence;
  private String listId;
  private boolean listUnsubscribePresent;
  private String returnPathDomain;
  private Boolean returnPathMismatch;
  private String replyToDomain;
  private Boolean replyToMismatch;
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

  /** In-Reply-To ou References présent : signal HAM fort, une vraie réponse à un fil existant. */
  public boolean isReply() {
    return reply;
  }

  public void setReply(boolean reply) {
    this.reply = reply;
  }

  /** Valeur brute du header Precedence ("bulk", "list", "junk"...) ; null si absent. Vocabulaire fixe, contrairement aux autres valeurs brutes ci-dessous. */
  public String getPrecedence() {
    return precedence;
  }

  public void setPrecedence(String precedence) {
    this.precedence = precedence;
  }

  /** Valeur brute du header List-Id ; null si absent. Se répète d'un envoi à l'autre pour une même liste, contrairement à un Message-ID. */
  public String getListId() {
    return listId;
  }

  public void setListId(String listId) {
    this.listId = listId;
  }

  public boolean isListUnsubscribePresent() {
    return listUnsubscribePresent;
  }

  public void setListUnsubscribePresent(boolean listUnsubscribePresent) {
    this.listUnsubscribePresent = listUnsubscribePresent;
  }

  /** Domaine de Return-Path ; null si absent ou vide (bounce sans retour, "&lt;&gt;"). */
  public String getReturnPathDomain() {
    return returnPathDomain;
  }

  public void setReturnPathDomain(String returnPathDomain) {
    this.returnPathDomain = returnPathDomain;
  }

  /** true si le domaine de Return-Path diffère de celui de From ; null si l'un des deux est indéterminable (pas juste "pas de mismatch"). */
  public Boolean getReturnPathMismatch() {
    return returnPathMismatch;
  }

  public void setReturnPathMismatch(Boolean returnPathMismatch) {
    this.returnPathMismatch = returnPathMismatch;
  }

  /** Domaine de la première adresse Reply-To ; null si absent ou mal formé. */
  public String getReplyToDomain() {
    return replyToDomain;
  }

  public void setReplyToDomain(String replyToDomain) {
    this.replyToDomain = replyToDomain;
  }

  /** true si le domaine de Reply-To diffère de celui de From ; null si l'un des deux est indéterminable. */
  public Boolean getReplyToMismatch() {
    return replyToMismatch;
  }

  public void setReplyToMismatch(Boolean replyToMismatch) {
    this.replyToMismatch = replyToMismatch;
  }

  public ClassifierLabel getLabel() {
    return label;
  }

  public void setLabel(ClassifierLabel label) {
    this.label = label;
  }
}
