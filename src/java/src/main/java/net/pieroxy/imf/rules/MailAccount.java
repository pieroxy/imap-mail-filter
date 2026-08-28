package net.pieroxy.imf.rules;

import com.sun.mail.imap.IMAPFolder;
import net.pieroxy.imf.config.MailAccountConfiguration;
import net.pieroxy.imf.standalone.Runner;

import javax.mail.*;
import javax.mail.search.ComparisonTerm;
import javax.mail.search.ReceivedDateTerm;
import java.util.Arrays;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MailAccount implements Runnable{
  private final static Logger LOGGER = Logger.getLogger(MailAccount.class.getName());
  private final MailAccountConfiguration config;

  public MailAccount(MailAccountConfiguration config) {
    this.config = config;
  }


  @Override
  public void run() {
    while (true) {
      try { Thread.sleep(config.getRunEvery()); } catch (Exception ignored) {}
      try {
        processMessages();
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, e.getMessage(), e);
      }
    }
  }

  private void inspect(Message message) {
  }

  private void processMessages() throws MessagingException {
    Session session = Session.getDefaultInstance(new Properties());
    session.setDebug(false);
    Store store = session.getStore("imaps");
    try {
      store.connect(
              config.getHost(),
              config.getPort(),
              config.getUsername(),
              config.getPassword());
      Folder inbox = store.getFolder("INBOX");
      // READ_WRITE: les actions (déplacement, marquage, etc.) doivent pouvoir modifier l'inbox.
      inbox.open(Folder.READ_WRITE);
      try {
        Date d = new Date(System.currentTimeMillis() - 5*24*60*60*1000L); // 5 days
        Message[] messages = inbox.search(new ReceivedDateTerm(ComparisonTerm.GT, d));
        Arrays.stream(messages).forEach(this::inspect);
      } finally {
        inbox.close(false);
      }
    } finally {
      if (store.isConnected()) store.close();
    }
  }
}
