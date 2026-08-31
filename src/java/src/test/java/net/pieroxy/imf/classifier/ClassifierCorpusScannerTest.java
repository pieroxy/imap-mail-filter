package net.pieroxy.imf.classifier;

import net.pieroxy.imf.mail.GreenMailImapFixture;
import net.pieroxy.imf.mail.ImapMailboxConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Teste ClassifierCorpusScanner de bout en bout contre un vrai serveur IMAP (GreenMail),
 * en particulier le scénario qui a motivé scanSpamFolderNow() : un utilisateur qui vide son
 * dossier Spam avant que le scan quotidien complet n'ait eu l'occasion de le voir.
 */
public class ClassifierCorpusScannerTest {

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  private final GreenMailImapFixture fixture = new GreenMailImapFixture();
  private final Session session = Session.getDefaultInstance(new Properties());

  @Before
  public void startServer() {
    fixture.start();
  }

  @After
  public void stopServer() {
    fixture.stop();
  }

  private MimeMessage message(String subject, String from) throws Exception {
    MimeMessage m = new MimeMessage(session);
    m.setSubject(subject);
    m.setFrom(new InternetAddress(from));
    m.setText("Hello");
    return m;
  }

  private void deleteAllMessagesIn(String... folderPath) throws Exception {
    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      Folder folder = mailbox.getOrCreateFolder(folderPath);
      folder.open(Folder.READ_WRITE);
      for (Message m : folder.getMessages()) {
        m.setFlag(Flags.Flag.DELETED, true);
      }
      folder.close(true); // expunge
    }
  }

  @Test
  public void scanSpamFolderNowCapturesSpamBeforeItCouldBeDeletedByTheDailyScan() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tempFolder.getRoot().getAbsolutePath(), "account", 30);
    fixture.appendMessage(message("Buy cheap stuff now", "spammer@bad.example.com"), "Spam");

    // Le scan fréquent (à chaque cycle) capture le spam...
    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      new ClassifierCorpusScanner(mailbox, store, "Spam").scanSpamFolderNow(new ClassifierScanState());
    }

    // ...avant que l'utilisateur ne le supprime le soir, comme dans le scénario réel.
    deleteAllMessagesIn("Spam");

    List<ClassifierExample> examples = store.readAll();
    assertEquals(1, examples.size());
    assertEquals(ClassifierLabel.SPAM, examples.get(0).getLabel());
    assertEquals("Buy cheap stuff now", examples.get(0).getSubject());
  }

  @Test
  public void scanSpamFolderNowIgnoresOtherFolders() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tempFolder.getRoot().getAbsolutePath(), "account", 30);
    fixture.appendMessage(message("Weekly team sync", "colleague@example.com"), "Archive");

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      new ClassifierCorpusScanner(mailbox, store, "Spam").scanSpamFolderNow(new ClassifierScanState());
    }

    assertTrue("scanSpamFolderNow ne doit toucher qu'au dossier Spam", store.readAll().isEmpty());
  }

  @Test
  public void dailyScanStillCoversSpamAndOtherFoldersTogether() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tempFolder.getRoot().getAbsolutePath(), "account", 30);
    fixture.appendMessage(message("Buy cheap stuff now", "spammer@bad.example.com"), "Spam");
    fixture.appendMessage(message("Weekly team sync", "colleague@example.com"), "Archive");

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      new ClassifierCorpusScanner(mailbox, store, "Spam").scan(new ClassifierScanState(), LocalDate.now());
    }

    List<ClassifierExample> examples = store.readAll();
    assertEquals(2, examples.size());
  }

  @Test
  public void dailyScanDoesNotDuplicateWhatTheFrequentSpamScanAlreadyCaptured() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tempFolder.getRoot().getAbsolutePath(), "account", 30);
    fixture.appendMessage(message("Buy cheap stuff now", "spammer@bad.example.com"), "Spam");
    ClassifierScanState state = new ClassifierScanState(); // même état partagé entre les deux scans, comme dans MailAccount

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      new ClassifierCorpusScanner(mailbox, store, "Spam").scanSpamFolderNow(state);
      new ClassifierCorpusScanner(mailbox, store, "Spam").scan(state, LocalDate.now());
    }

    assertEquals("le scan quotidien ne doit pas re-capturer ce que le scan fréquent a déjà vu",
        1, store.readAll().size());
  }
}
