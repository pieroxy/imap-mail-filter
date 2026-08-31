package net.pieroxy.imf.rules;

import net.pieroxy.imf.mail.ImapMailboxConnection;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MailAccountSecondCycleTest extends AbstractMailAccountTest {
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
}
