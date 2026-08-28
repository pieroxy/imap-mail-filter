package net.pieroxy.imf.mail;

import com.sun.mail.imap.IMAPFolder;
import net.pieroxy.imf.config.MailAccountConfiguration;

import javax.mail.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implémentation de {@link ImapMailbox} basée sur javax.mail. Seule classe du projet à
 * connaître les détails de connexion IMAP bas niveau.
 */
public class ImapMailboxConnection implements ImapMailbox {
  private final static Logger LOGGER = Logger.getLogger(ImapMailboxConnection.class.getName());

  private final Store store;
  private final IMAPFolder inbox;

  private ImapMailboxConnection(Store store, IMAPFolder inbox) {
    this.store = store;
    this.inbox = inbox;
  }

  /**
   * Se connecte au compte et ouvre l'INBOX en lecture/écriture (les actions doivent pouvoir
   * modifier les messages). L'appelant est responsable de fermer la connexion, idéalement via
   * un try-with-resources.
   */
  public static ImapMailboxConnection connect(MailAccountConfiguration config) throws MessagingException {
    Session session = Session.getDefaultInstance(new Properties());
    session.setDebug(false);
    Store store = session.getStore("imaps");
    store.connect(config.getHost(), config.getPort(), config.getUsername(), config.getPassword());
    IMAPFolder inbox = (IMAPFolder) store.getFolder("INBOX");
    inbox.open(Folder.READ_WRITE);
    return new ImapMailboxConnection(store, inbox);
  }

  @Override
  public long getUidValidity() throws MessagingException {
    return inbox.getUIDValidity();
  }

  @Override
  public long getUidNext() throws MessagingException {
    return inbox.getUIDNext();
  }

  @Override
  public Message[] getMessagesSince(long lastUid) throws MessagingException {
    Message[] candidates = inbox.getMessagesByUID(lastUid + 1, UIDFolder.LASTUID);
    List<Message> result = new ArrayList<>();
    for (Message m : candidates) {
      // getMessagesByUID peut renvoyer la borne basse même si son UID est en fait <= lastUid.
      if (inbox.getUID(m) > lastUid) result.add(m);
    }
    return result.toArray(new Message[0]);
  }

  @Override
  public long getUid(Message message) throws MessagingException {
    return inbox.getUID(message);
  }

  @Override
  public void close() {
    try {
      if (inbox.isOpen()) inbox.close(false);
    } catch (MessagingException e) {
      LOGGER.log(Level.WARNING, "Error closing inbox", e);
    }
    try {
      if (store.isConnected()) store.close();
    } catch (MessagingException e) {
      LOGGER.log(Level.WARNING, "Error closing store", e);
    }
  }
}
