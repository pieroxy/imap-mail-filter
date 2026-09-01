package net.pieroxy.imf.mail;

import net.pieroxy.imf.utils.MailTools;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.mail.Flags;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

import static org.junit.Assert.assertFalse;

/**
 * Régression : lire le contenu complet d'un message via
 * {@link MailTools#readRawMessageWithoutMarkingSeen} — ce que font DkimResultMatcher et
 * DmarcResultMatcher pour vérifier une signature DKIM — ne doit jamais marquer ce message comme
 * lu côté serveur. C'est exactement le bug observé : plus aucun message non-lu dans l'INBOX dès
 * que ces matchers étaient dans la liste des règles (peu importe qu'une règle matche ou non).
 * <p>
 * Deux pièges qui ont fait échouer les premières tentatives de correctif, gardés en commentaire
 * pour ne pas les refaire :
 * <ul>
 *   <li>La propriété de session "mail.imap.peek" ne suffit pas : elle ne couvre pas une lecture
 *   de contenu ad-hoc comme message.writeTo() (vérifié via un trace IMAP : javax.mail envoyait
 *   quand même BODY[], pas BODY.PEEK[], propriété posée ou non).</li>
 *   <li>Un {@link javax.mail.internet.MimeMessage} construit en mémoire (comme le fait
 *   DkimResultMatcherTest) ne peut pas détecter cette régression : writeTo() n'y parle à aucun
 *   serveur, donc l'effet de bord n'existe pas. Seul un vrai message IMAP (GreenMail) le peut.</li>
 * </ul>
 */
public class ImapMailboxConnectionTest {
  private final GreenMailImapFixture fixture = new GreenMailImapFixture();

  @Before
  public void startServer() {
    fixture.start();
  }

  @After
  public void stopServer() {
    fixture.stop();
  }

  @Test
  public void readingTheFullMessageContentDoesNotMarkItSeen() throws Exception {
    Session session = Session.getDefaultInstance(new Properties());
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("sender@example.com"));
    message.setSubject("Test");
    message.setText("Hello");
    fixture.appendMessage(message, "INBOX");

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      Message[] messages = mailbox.getMessagesSince(0);
      MailTools.readRawMessageWithoutMarkingSeen(messages[0]);
    }

    try (ImapMailboxConnection mailbox = fixture.connectAsImapMailbox()) {
      Message fetched = mailbox.getMessagesSince(0)[0];
      assertFalse("lire le contenu d'un message ne doit jamais le marquer \\Seen",
          fetched.isSet(Flags.Flag.SEEN));
    }
  }
}
