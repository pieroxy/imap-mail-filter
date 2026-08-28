package net.pieroxy.imf.rules;

/**
 * Etat persistant d'un compte, utilisé pour ne traiter chaque message qu'une seule fois.
 * uidValidity==0 signifie "jamais initialisé" (un serveur IMAP ne renvoie jamais 0).
 */
public class MailAccountState {
  private long uidValidity;
  private long lastUid;

  public long getUidValidity() {
    return uidValidity;
  }

  public void setUidValidity(long uidValidity) {
    this.uidValidity = uidValidity;
  }

  public long getLastUid() {
    return lastUid;
  }

  public void setLastUid(long lastUid) {
    this.lastUid = lastUid;
  }
}
