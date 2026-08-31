package net.pieroxy.imf.rules;

import net.pieroxy.imf.config.MailAccountConfiguration;
import net.pieroxy.imf.config.MailFilterRuleActionConfiguration;
import net.pieroxy.imf.config.MailFilterRuleConfiguration;
import net.pieroxy.imf.config.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.mail.GreenMailImapFixture;
import net.pieroxy.imf.mail.ImapMailboxConnection;
import net.pieroxy.imf.rules.actions.ActionType;
import net.pieroxy.imf.rules.matchers.MatcherType;
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
import static org.junit.Assert.assertTrue;

/**
 * Teste MailAccount.processMessages() de bout en bout contre un vrai serveur IMAP en mémoire
 * (GreenMail), injecté via le constructeur package-private (ImapMailboxFactory) plutôt que le
 * chemin de connexion "imaps"/TLS réel utilisé en production.
 */
public class MailAccountTest {
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

  private static MailFilterRuleConfiguration moveToSpamOnDomain(String domain) {
    MailFilterRuleMatcherConfiguration matcher = new MailFilterRuleMatcherConfiguration();
    matcher.setType(MatcherType.FROM_DOMAIN_EQUALS);
    matcher.setKey(domain);
    MailFilterRuleActionConfiguration action = new MailFilterRuleActionConfiguration();
    action.setType(ActionType.MOVE_TO);
    action.setKey("Spam");
    MailFilterRuleConfiguration rule = new MailFilterRuleConfiguration();
    rule.setMatcher(matcher);
    rule.setAction(action);
    return rule;
  }

  private MimeMessage messageFrom(String address) throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress(address));
    message.setSubject("Test");
    message.setText("Hello");
    return message;
  }

  private MailAccount accountWith(MailFilterRuleConfiguration... rules) {
    MailAccountConfiguration config = fixture.accountConfig("test-account");
    config.setRules(List.of(rules));
    return new MailAccount(config, tempFolder.getRoot().getAbsolutePath(), 0, c -> fixture.connectAsImapMailbox());
  }

  @Test
  public void processesNewInboxMailAgainstConfiguredRules() throws Exception {
    MailAccount account = accountWith(moveToSpamOnDomain("spammy.example.com"));
    account.processMessages(); // premier cycle sur un compte neuf : établit le curseur UID sur "maintenant"

    fixture.appendMessage(messageFrom("sender@spammy.example.com"), "INBOX");
    account.processMessages();

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      assertEquals(1, mailbox.getAllMessages(mailbox.getOrCreateFolder("Spam")).length);
    }
  }

  @Test
  public void nonMatchingMailStaysInInbox() throws Exception {
    MailAccount account = accountWith(moveToSpamOnDomain("spammy.example.com"));
    account.processMessages();

    fixture.appendMessage(messageFrom("sender@unrelated.example.com"), "INBOX");
    account.processMessages();

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      assertEquals(1, mailbox.getAllMessages(mailbox.getOrCreateFolder("INBOX")).length);
    }
  }

  @Test
  public void aSecondCycleDoesNotReapplyRulesToAlreadyProcessedMail() throws Exception {
    MailAccount account = accountWith(moveToSpamOnDomain("spammy.example.com"));
    account.processMessages(); // établit le curseur UID avant tout dépôt de courrier

    fixture.appendMessage(messageFrom("first@spammy.example.com"), "INBOX");
    account.processMessages();
    fixture.appendMessage(messageFrom("second@spammy.example.com"), "INBOX");
    account.processMessages();

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      // Les deux messages spammy ont été déplacés : un par cycle, jamais retraité deux fois.
      assertEquals(2, mailbox.getAllMessages(mailbox.getOrCreateFolder("Spam")).length);
    }
  }

  @Test
  public void ensuresTheImfRulesFolderSkeletonOnFirstCycle() throws Exception {
    accountWith().processMessages();

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      assertTrue(mailbox.getOrCreateFolder("imf-rules", "ToProcess").exists());
      assertTrue(mailbox.getOrCreateFolder("imf-rules", "Done").exists());
    }
  }
}
