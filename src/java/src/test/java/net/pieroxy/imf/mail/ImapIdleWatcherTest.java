package net.pieroxy.imf.mail;

import net.pieroxy.imf.config.MailAccountConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

/** Exercises {@link ImapIdleWatcher} against a real in-memory IMAP server (GreenMail supports IDLE). */
public class ImapIdleWatcherTest {
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
  public void notifiesAsSoonAsAMessageArrivesInTheInbox() throws Exception {
    MailAccountConfiguration config = fixture.accountConfig("idle-test");
    CountDownLatch notified = new CountDownLatch(1);
    ImapIdleWatcher watcher = new ImapIdleWatcher(config, c -> fixture.connectStore(), notified::countDown);
    Thread watcherThread = new Thread(watcher, "idle-watcher-test");
    watcherThread.setDaemon(true);
    watcherThread.start();
    try {
      // Give the watcher time to connect and enter IDLE before delivering the message.
      Thread.sleep(300);
      fixture.appendMessage(messageFrom("sender@example.com"), "INBOX");
      assertTrue("onNewMail must fire once the message is delivered", notified.await(5, TimeUnit.SECONDS));
    } finally {
      watcher.shutdown();
      watcherThread.join(2000);
    }
  }

  private MimeMessage messageFrom(String address) throws Exception {
    MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
    message.setFrom(new InternetAddress(address));
    message.setSubject("Test");
    message.setText("Hello");
    return message;
  }
}
