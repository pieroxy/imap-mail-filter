package net.pieroxy.imf.mail;

import com.sun.mail.imap.IMAPFolder;
import net.pieroxy.imf.config.MailAccountConfiguration;

import javax.mail.*;
import java.util.ArrayList;
import java.util.Arrays;
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
    Session session = Session.getDefaultInstance(peekProperties());
    session.setDebug(false);
    Store store = session.getStore("imaps");
    store.connect(config.getHost(), config.getPort(), config.getUsername(), config.getPassword());
    IMAPFolder inbox = (IMAPFolder) store.getFolder("INBOX");
    inbox.open(Folder.READ_WRITE);
    return new ImapMailboxConnection(store, inbox);
  }

  /**
   * Réservé aux tests : enveloppe un Store déjà connecté (ex: un serveur GreenMail en IMAP
   * simple) sans passer par connect()/IMAPS — permet de tester le reste de cette classe sans
   * TLS ni vrai serveur IMAP distant.
   */
  public static ImapMailboxConnection forTesting(Store connectedStore) throws MessagingException {
    IMAPFolder inbox = (IMAPFolder) connectedStore.getFolder("INBOX");
    inbox.open(Folder.READ_WRITE);
    return new ImapMailboxConnection(connectedStore, inbox);
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
  public Folder getOrCreateFolder(String... pathSegments) throws MessagingException {
    // Navigation relative niveau par niveau : indépendant du caractère séparateur du serveur
    // (contrairement à un nom pleinement qualifié passé directement à store.getFolder(...)).
    Folder current = store.getDefaultFolder();
    for (String segment : pathSegments) {
      current = current.getFolder(segment);
      if (!current.exists()) {
        current.create(Folder.HOLDS_MESSAGES | Folder.HOLDS_FOLDERS);
      }
    }
    return current;
  }

  @Override
  public Folder getRootFolder() throws MessagingException {
    return store.getDefaultFolder();
  }

  @Override
  public List<Folder> listSubfolders(Folder parent) throws MessagingException {
    return Arrays.asList(parent.list());
  }

  @Override
  public long getUidValidity(Folder folder) throws MessagingException {
    openReadOnly(folder);
    return ((IMAPFolder) folder).getUIDValidity();
  }

  @Override
  public Message[] getMessagesSince(Folder folder, long lastUid) throws MessagingException {
    openReadOnly(folder);
    IMAPFolder imapFolder = (IMAPFolder) folder;
    Message[] candidates = imapFolder.getMessagesByUID(lastUid + 1, UIDFolder.LASTUID);
    List<Message> result = new ArrayList<>();
    for (Message m : candidates) {
      // Comme pour l'INBOX : getMessagesByUID peut renvoyer la borne basse même si son UID
      // est en fait <= lastUid.
      if (imapFolder.getUID(m) > lastUid) result.add(m);
    }
    Message[] messages = result.toArray(new Message[0]);
    // Sans ce fetch groupé, chaque accès individuel à un en-tête (Subject/From/To/Date/
    // Received) déclenche sa propre commande IMAP par message : sur un dossier de plusieurs
    // milliers de messages (des années d'archives), ça peut prendre des dizaines de minutes
    // au lieu de quelques secondes.
    if (messages.length > 0) {
      FetchProfile profile = new FetchProfile();
      profile.add(FetchProfile.Item.ENVELOPE);
      profile.add("Received");
      folder.fetch(messages, profile);
    }
    return messages;
  }

  @Override
  public long getUid(Folder folder, Message message) throws MessagingException {
    return ((IMAPFolder) folder).getUID(message);
  }

  @Override
  public void closeReadOnly(Folder folder) throws MessagingException {
    if (folder.isOpen()) folder.close(false);
  }

  private void openReadOnly(Folder folder) throws MessagingException {
    if (!folder.isOpen()) folder.open(Folder.READ_ONLY);
  }

  @Override
  public Message[] getAllMessages(Folder folder) throws MessagingException {
    if (!folder.isOpen()) folder.open(Folder.READ_WRITE);
    return folder.getMessages();
  }

  @Override
  public void closeAndExpunge(Folder folder) throws MessagingException {
    if (folder.isOpen()) folder.close(true);
  }

  @Override
  public void close() {
    try {
      // expunge=true : les actions (MoveToAction) marquent \Deleted les messages qu'elles
      // relogent ailleurs ; il faut purger l'INBOX en conséquence à la fin du cycle.
      if (inbox.isOpen()) inbox.close(true);
    } catch (MessagingException e) {
      LOGGER.log(Level.WARNING, "Error closing inbox", e);
    }
    try {
      if (store.isConnected()) store.close();
    } catch (MessagingException e) {
      LOGGER.log(Level.WARNING, "Error closing store", e);
    }
  }

  /**
   * Filet de sécurité pour le préchargement automatique (FetchProfile) qu'un futur appel
   * pourrait déclencher — mais attention, ça ne suffit PAS pour le cas qui compte
   * aujourd'hui : lire le contenu complet d'un message à la demande (message.writeTo(),
   * utilisé par DkimResultMatcher/DmarcResultMatcher pour vérifier une signature DKIM) ignore
   * cette propriété (vérifié empiriquement avec un trace IMAP : javax.mail envoie quand même
   * BODY[], pas BODY.PEEK[], malgré "peek"=true ici). Le vrai correctif pour ce chemin-là est
   * {@code IMAPMessage.setPeek(true)} posé par message avant lecture, voir
   * {@link net.pieroxy.imf.utils.MailTools#readRawMessageWithoutMarkingSeen}. Les deux
   * propriétés ci-dessous existent parce qu'elle est par protocole ("imap" en clair vs "imaps").
   */
  private static Properties peekProperties() {
    Properties props = new Properties();
    props.setProperty("mail.imap.peek", "true");
    props.setProperty("mail.imaps.peek", "true");
    return props;
  }
}
