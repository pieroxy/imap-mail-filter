package net.pieroxy.imf.rules;

/**
 * Persistent state of an account, used to process each message only once. uidValidity==0 means
 * "never initialized" (an IMAP server never returns 0).
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
