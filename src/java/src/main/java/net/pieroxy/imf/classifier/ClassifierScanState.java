package net.pieroxy.imf.classifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Etat persistant du scan du corpus classifieur : date du dernier scan réussi (pour ne
 * scanner qu'une fois par jour) et, par dossier IMAP, le dernier UID traité (pour ne
 * refetcher que les nouveaux messages d'un scan à l'autre).
 */
public class ClassifierScanState {
  private String lastScanDate;
  private Map<String, FolderProgress> folders = new HashMap<>();

  public String getLastScanDate() {
    return lastScanDate;
  }

  public void setLastScanDate(String lastScanDate) {
    this.lastScanDate = lastScanDate;
  }

  public Map<String, FolderProgress> getFolders() {
    return folders;
  }

  public FolderProgress getFolderProgress(String folderFullName) {
    return folders.get(folderFullName);
  }

  public void setFolderProgress(String folderFullName, long uidValidity, long lastUid) {
    FolderProgress progress = new FolderProgress();
    progress.setUidValidity(uidValidity);
    progress.setLastUid(lastUid);
    folders.put(folderFullName, progress);
  }

  /** uidValidity==0 signifie "jamais scanné" (un serveur IMAP ne renvoie jamais 0). */
  public static class FolderProgress {
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
}
