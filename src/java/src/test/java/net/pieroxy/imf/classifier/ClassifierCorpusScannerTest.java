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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests ClassifierCorpusScanner end to end against a real IMAP server (GreenMail), in
 * particular the scenario that motivated scanSpamFolderNow(): a user who empties their Spam
 * folder before the daily full scan has had a chance to see it.
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

    // The frequent scan (every cycle) captures the spam...
    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      new ClassifierCorpusScanner(mailbox, store, "Spam", List.of(), "test-account").scanSpamFolderNow(new ClassifierScanState());
    }

    // ...before the user deletes it in the evening, as in the real-world scenario.
    deleteAllMessagesIn("Spam");

    List<ClassifierExample> examples = store.readAll();
    assertEquals(1, examples.size());
    assertEquals(ClassifierLabel.SPAM, examples.get(0).getLabel());
    assertEquals("Buy cheap stuff now", examples.get(0).getSubject());
  }

  @Test
  public void capturesTheServerRecordedReceivedDateNotJustTheSelfReportedMailDate() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tempFolder.getRoot().getAbsolutePath(), "account", 30);
    fixture.appendMessage(message("Weekly team sync", "colleague@example.com"), "Archive");

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      new ClassifierCorpusScanner(mailbox, store, "Spam", List.of(), "test-account").scan(new ClassifierScanState(), LocalDate.now());
    }

    ClassifierExample example = store.readAll().get(0);
    assertTrue("receivedDate must come from the server's own INTERNALDATE, not be left null",
        example.getReceivedDate() != null && !example.getReceivedDate().isBlank());
  }

  @Test
  public void scanSpamFolderNowIgnoresOtherFolders() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tempFolder.getRoot().getAbsolutePath(), "account", 30);
    fixture.appendMessage(message("Weekly team sync", "colleague@example.com"), "Archive");

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      new ClassifierCorpusScanner(mailbox, store, "Spam", List.of(), "test-account").scanSpamFolderNow(new ClassifierScanState());
    }

    assertTrue("scanSpamFolderNow must only touch the Spam folder", store.readAll().isEmpty());
  }

  @Test
  public void dailyScanStillCoversSpamAndOtherFoldersTogether() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tempFolder.getRoot().getAbsolutePath(), "account", 30);
    fixture.appendMessage(message("Buy cheap stuff now", "spammer@bad.example.com"), "Spam");
    fixture.appendMessage(message("Weekly team sync", "colleague@example.com"), "Archive");

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      new ClassifierCorpusScanner(mailbox, store, "Spam", List.of(), "test-account").scan(new ClassifierScanState(), LocalDate.now());
    }

    List<ClassifierExample> examples = store.readAll();
    assertEquals(2, examples.size());
  }

  @Test
  public void dailyScanDoesNotDuplicateWhatTheFrequentSpamScanAlreadyCaptured() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tempFolder.getRoot().getAbsolutePath(), "account", 30);
    fixture.appendMessage(message("Buy cheap stuff now", "spammer@bad.example.com"), "Spam");
    ClassifierScanState state = new ClassifierScanState(); // same state shared between the two scans, as in MailAccount

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      new ClassifierCorpusScanner(mailbox, store, "Spam", List.of(), "test-account").scanSpamFolderNow(state);
      new ClassifierCorpusScanner(mailbox, store, "Spam", List.of(), "test-account").scan(state, LocalDate.now());
    }

    assertEquals("the daily scan must not re-capture what the frequent scan already saw",
        1, store.readAll().size());
  }

  @Test
  public void scanningNeverMarksMessagesSeen() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tempFolder.getRoot().getAbsolutePath(), "account", 30);
    fixture.appendMessage(message("Weekly team sync", "colleague@example.com"), "Archive");

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      new ClassifierCorpusScanner(mailbox, store, "Spam", List.of(), "test-account").scan(new ClassifierScanState(), LocalDate.now());
    }

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      Message[] messages = mailbox.getAllMessages(mailbox.getOrCreateFolder("Archive"));
      assertFalse("scanning the corpus (headers + MIME structure) must never mark a message \\Seen",
          messages[0].isSet(Flags.Flag.SEEN));
    }
  }

  @Test
  public void stopsMidFolderOnceTheBudgetIsReachedAndResumesNextTime() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tempFolder.getRoot().getAbsolutePath(), "account", 30);
    int total = ClassifierCorpusScanner.MAX_MESSAGES_PER_SCAN + 5;
    Message[] messages = new Message[total];
    for (int i = 0; i < total; i++) {
      messages[i] = message("Newsletter " + i, "sender@example.com");
    }
    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      mailbox.getOrCreateFolder("Archive").appendMessages(messages);
    }

    ClassifierScanState state = new ClassifierScanState();
    boolean budgetExceededFirstPass;
    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      budgetExceededFirstPass = new ClassifierCorpusScanner(mailbox, store, "Spam", List.of(), "test-account")
          .scan(state, LocalDate.now());
    }
    assertTrue("the first pass must stop at the budget, mid-folder", budgetExceededFirstPass);
    assertEquals(ClassifierCorpusScanner.MAX_MESSAGES_PER_SCAN, store.readAll().size());

    boolean budgetExceededSecondPass;
    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      budgetExceededSecondPass = new ClassifierCorpusScanner(mailbox, store, "Spam", List.of(), "test-account")
          .scan(state, LocalDate.now());
    }
    assertFalse("the second pass must finish covering the rest of the folder", budgetExceededSecondPass);
    assertEquals("all messages must be captured across the two passes", total, store.readAll().size());
  }

  @Test
  public void excludedFoldersAreSkippedEntirelyNeitherSpamNorHam() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tempFolder.getRoot().getAbsolutePath(), "account", 30);
    // "SpamML" isn't named "Spam": without exclusion it would be scanned by the full scan and
    // wrongly labeled HAM (worse than not learning from it at all — it would poison the corpus).
    fixture.appendMessage(message("Buy cheap stuff now", "spammer@bad.example.com"), "SpamML");
    fixture.appendMessage(message("Weekly team sync", "colleague@example.com"), "Archive");

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      new ClassifierCorpusScanner(mailbox, store, "Spam", List.of("SpamML"), "test-account").scan(new ClassifierScanState(), LocalDate.now());
    }

    List<ClassifierExample> examples = store.readAll();
    assertEquals("only Archive should be captured, SpamML is excluded", 1, examples.size());
    assertEquals("Weekly team sync", examples.get(0).getSubject());
  }
}
