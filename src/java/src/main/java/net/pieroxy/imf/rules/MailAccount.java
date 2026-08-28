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
        Arrays.stream(getMessages()).forEach(this::inspect);
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, e.getMessage(), e);
      }
    }
  }

  private void inspect(Message message) {
  }

  private Message[] getMessages() throws MessagingException {
    Session session = Session.getDefaultInstance(new Properties());
    session.setDebug(false);
    Store store = session.getStore("imaps");
    store.connect(
            config.getHost(),
            config.getPort(),
            config.getUsername(),
            config.getPassword());
    Folder inbox = store.getFolder("INBOX");
    inbox.open(Folder.READ_ONLY);

    Date d = new Date(System.currentTimeMillis() - 5*24*60*60*1000L); // 5 days
    Message[] messages = inbox.search(new ReceivedDateTerm(ComparisonTerm.GT, d));
    return messages;
  }
}
