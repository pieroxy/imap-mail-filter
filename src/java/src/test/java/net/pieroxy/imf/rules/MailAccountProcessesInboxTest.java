package net.pieroxy.imf.rules;

import net.pieroxy.imf.mail.ImapMailboxConnection;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MailAccountProcessesInboxTest extends AbstractMailAccountTest {
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
}
