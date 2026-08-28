package net.pieroxy.imf.rules;

import com.google.gson.Gson;
import com.sun.mail.imap.IMAPFolder;
import net.pieroxy.imf.config.MailAccountConfiguration;

import javax.mail.*;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MailAccount implements Runnable{
  private final static Logger LOGGER = Logger.getLogger(MailAccount.class.getName());
  private final static long MAX_BACKOFF_MS = 30 * 60 * 1000L; // 30 minutes
  private final MailAccountConfiguration config;
  private final String dataFolder;

  public MailAccount(MailAccountConfiguration config, String dataFolder) {
    this.config = config;
    this.dataFolder = dataFolder;
  }


  @Override
  public void run() {
    long delayMs = config.getRunEvery();
    while (!Thread.currentThread().isInterrupted()) {
      try {
        Thread.sleep(delayMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
      try {
        processMessages();
        delayMs = config.getRunEvery();
      } catch (Exception e) {
        // Backoff exponentiel : évite de marteler un serveur en panne ou un mot de passe
        // invalide toutes les `runEvery` ms indéfiniment. Réinitialisé au prochain succès.
        delayMs = Math.min(delayMs * 2, MAX_BACKOFF_MS);
        LOGGER.log(Level.WARNING, "Account " + config.getDisplayName() + ": " + e.getMessage()
                + ". Next retry in " + delayMs + "ms.", e);
      }
    }
    LOGGER.info("Account " + config.getDisplayName() + " stopped.");
  }

  private void inspect(Message message) {
  }

  private void processMessages() throws MessagingException {
    Session session = Session.getDefaultInstance(new Properties());
    session.setDebug(false);
    Store store = session.getStore("imaps");
    try {
      store.connect(
              config.getHost(),
              config.getPort(),
              config.getUsername(),
              config.getPassword());
      IMAPFolder inbox = (IMAPFolder) store.getFolder("INBOX");
      // READ_WRITE: les actions (déplacement, marquage, etc.) doivent pouvoir modifier l'inbox.
      inbox.open(Folder.READ_WRITE);
      try {
        processNewMessages(inbox);
      } finally {
        inbox.close(false);
      }
    } finally {
      if (store.isConnected()) store.close();
    }
  }

  /**
   * Ne traite que les messages dont l'UID est strictement supérieur au dernier UID connu,
   * afin qu'un message ne soit jamais inspecté deux fois d'un cycle à l'autre.
   */
  private void processNewMessages(IMAPFolder inbox) throws MessagingException {
    MailAccountState state = loadState();

    long uidValidity = inbox.getUIDValidity();
    if (state.getUidValidity() != uidValidity) {
      // Première exécution pour ce compte, ou UIDVALIDITY changée côté serveur (mailbox recréée) :
      // les anciens UID ne veulent plus rien dire. On repart de "maintenant" plutôt que de rejouer
      // tout l'historique de la boîte.
      state.setUidValidity(uidValidity);
      state.setLastUid(inbox.getUIDNext() - 1);
    }

    Message[] messages = inbox.getMessagesByUID(state.getLastUid() + 1, UIDFolder.LASTUID);
    for (Message message : messages) {
      long uid = inbox.getUID(message);
      if (uid <= state.getLastUid()) continue; // getMessagesByUID peut renvoyer la borne basse même absente

      try {
        inspect(message);
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Failed to inspect message UID " + uid + " on account " + config.getDisplayName(), e);
      }
      state.setLastUid(uid);
    }

    saveState(state);
  }

  private File getStateFile() {
    return new File(dataFolder, config.getDisplayName() + ".json");
  }

  private MailAccountState loadState() {
    File f = getStateFile();
    if (!f.exists()) return new MailAccountState();
    try (FileReader r = new FileReader(f)) {
      MailAccountState state = new Gson().fromJson(r, MailAccountState.class);
      return state != null ? state : new MailAccountState();
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Could not read state file " + f, e);
      return new MailAccountState();
    }
  }

  private void saveState(MailAccountState state) {
    File f = getStateFile();
    f.getParentFile().mkdirs();
    try (FileWriter w = new FileWriter(f)) {
      new Gson().toJson(state, w);
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Could not write state file " + f, e);
    }
  }
}
