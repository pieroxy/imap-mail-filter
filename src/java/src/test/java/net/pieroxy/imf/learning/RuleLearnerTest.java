package net.pieroxy.imf.learning;

import net.pieroxy.imf.config.MailFilterRuleConfiguration;
import net.pieroxy.imf.mail.GreenMailImapFixture;
import net.pieroxy.imf.mail.ImapMailboxConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Teste RuleLearner contre un vrai serveur IMAP en mémoire (GreenMail) plutôt qu'un faux
 * Folder fait main — nécessaire notamment parce que moveToDone() appelle
 * message.getFolder().copyMessages(...), qui a besoin d'un vrai Folder attaché au message.
 */
public class RuleLearnerTest {
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

  private LearnedRulesStore store() {
    return new LearnedRulesStore(tempFolder.getRoot().getAbsolutePath(), "test-account");
  }

  private MimeMessage messageFrom(String address) throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress(address));
    message.setSubject("Test");
    message.setText("Hello");
    return message;
  }

  @Test
  public void ensureFolderSkeletonCreatesTheLearnableTree() throws Exception {
    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      new RuleLearner(mailbox, store()).ensureFolderSkeleton();

      assertTrue(mailbox.getOrCreateFolder("imf-rules", "FROM_DOMAIN_EQUALS", "MOVE_TO").exists());
      assertTrue(mailbox.getOrCreateFolder("imf-rules", "Done").exists());
    }
  }

  @Test
  public void learnsARuleFromAnExampleAndRunsItsAction() throws Exception {
    fixture.appendMessage(messageFrom("sender@newsletter.example.com"), "imf-rules", "FROM_DOMAIN_EQUALS", "MOVE_TO", "Spam");

    boolean learnedSomething;
    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      RuleLearner learner = new RuleLearner(mailbox, store());
      learner.ensureFolderSkeleton();
      learnedSomething = learner.learnFromExamples();
    }

    assertTrue(learnedSomething);

    List<MailFilterRuleConfiguration> learned = store().load();
    assertEquals(1, learned.size());
    assertEquals("newsletter.example.com", learned.get(0).getMatcher().getKey());

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      assertEquals(1, mailbox.getAllMessages(mailbox.getOrCreateFolder("Spam")).length);
      assertEquals(0, mailbox.getAllMessages(mailbox.getOrCreateFolder("imf-rules", "FROM_DOMAIN_EQUALS", "MOVE_TO", "Spam")).length);
    }
  }

  @Test
  public void secondRunWithNoNewExamplesLearnsNothing() throws Exception {
    fixture.appendMessage(messageFrom("sender@newsletter.example.com"), "imf-rules", "FROM_DOMAIN_EQUALS", "MOVE_TO", "Spam");

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      RuleLearner learner = new RuleLearner(mailbox, store());
      learner.ensureFolderSkeleton();
      learner.learnFromExamples();
    }

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      RuleLearner learner = new RuleLearner(mailbox, store());
      assertFalse(learner.learnFromExamples());
    }
  }
}
