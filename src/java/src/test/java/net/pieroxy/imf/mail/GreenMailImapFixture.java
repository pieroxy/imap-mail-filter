package net.pieroxy.imf.mail;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import net.pieroxy.imf.config.MailAccountConfiguration;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;
import java.util.Properties;

/**
 * Banc d'essai IMAP réel pour tester {@link net.pieroxy.imf.rules.MailAccount},
 * {@link net.pieroxy.imf.learning.RuleLearner} et {@link net.pieroxy.imf.rules.ManualReprocessor} :
 * démarre un serveur IMAP en mémoire (GreenMail) plutôt que de faire à la main un faux
 * {@code javax.mail.Folder} (classe abstraite avec une quinzaine de méthodes, dont
 * {@code copyMessages} qu'utilisent {@code MoveToAction}/{@code RuleLearner}/{@code
 * ManualReprocessor}) — donne de vrais Folder/Message qui se comportent correctement, et teste
 * par la même occasion le vrai {@link ImapMailboxConnection}.
 */
public class GreenMailImapFixture {
  private static final String USERNAME = "test@localhost";
  private static final String PASSWORD = "password";

  // Port dynamique (attribué par l'OS), pas le port de test fixe de GreenMail : plusieurs
  // instances de cette fixture tournent en parallèle une fois les tests parallélisés (voir
  // pom.xml), et un port fixe ferait entrer ces instances en conflit entre elles.
  private final GreenMail greenMail = new GreenMail(ServerSetupTest.IMAP.dynamicPort());

  public void start() {
    greenMail.start();
    greenMail.setUser(USERNAME, USERNAME, PASSWORD);
  }

  public void stop() {
    greenMail.stop();
  }

  /** Config pointant vers ce serveur, prête à passer à un MailAccount de test. */
  public MailAccountConfiguration accountConfig(String displayName) {
    MailAccountConfiguration config = new MailAccountConfiguration();
    config.setDisplayName(displayName);
    config.setHost("127.0.0.1");
    config.setPort(greenMail.getImap().getPort());
    config.setUsername(USERNAME);
    config.setPassword(PASSWORD);
    config.setRunEvery(60);
    return config;
  }

  /** Connexion IMAP en clair (pas de TLS) vers ce serveur — ce que connect() fait normalement via "imaps". */
  public ImapMailboxConnection connectAsImapMailbox() throws MessagingException {
    Session session = Session.getDefaultInstance(new Properties());
    Store store = session.getStore("imap");
    store.connect("127.0.0.1", greenMail.getImap().getPort(), USERNAME, PASSWORD);
    return ImapMailboxConnection.forTesting(store);
  }

  /** Dépose message dans le dossier donné (créé si besoin), via IMAP APPEND. */
  public void appendMessage(Message message, String... folderPath) throws MessagingException {
    try (ImapMailboxConnection mailbox = connectAsImapMailbox()) {
      Folder folder = mailbox.getOrCreateFolder(folderPath);
      folder.appendMessages(new Message[]{message});
    }
  }
}
