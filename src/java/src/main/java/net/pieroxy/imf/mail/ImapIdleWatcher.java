package net.pieroxy.imf.mail;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;
import net.pieroxy.imf.config.MailAccountConfiguration;
import net.pieroxy.imf.scheduling.BackoffLoop;

import javax.mail.Folder;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.event.MessageCountAdapter;
import javax.mail.event.MessageCountEvent;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Watches the INBOX for new mail via IMAP IDLE (RFC 2177), so it's noticed the instant it
 * arrives instead of waiting for the account's regular {@code runEvery} cycle. Deliberately does
 * nothing with what it detects beyond calling {@code onNewMail}: the actual scan and rule
 * application stays exactly what {@link net.pieroxy.imf.rules.MailAccount#processMessages()}
 * already does, on its own schedule and its own connection — this class only makes that schedule
 * fire early. Runs on its own dedicated connection (IDLE monopolizes whichever connection it's
 * issued on) and is meant to run on its own thread, for the lifetime of the account.
 * <p>
 * Self-healing: a dropped connection (network blip, server-side IDLE timeout — RFC 2177
 * recommends the server not exceed ~29 minutes) is caught and reconnected with backoff via the
 * same {@link BackoffLoop} the account's main cycle uses. A server that doesn't advertise IDLE
 * support at all is detected once and the watcher then gives up for good: that's a static
 * server property, so retrying it would just be noise — the account still gets new mail via its
 * normal {@code runEvery} cycle regardless.
 */
public class ImapIdleWatcher implements Runnable {
  private final static Logger LOGGER = Logger.getLogger(ImapIdleWatcher.class.getName());
  private final static long INITIAL_BACKOFF_MS = 5_000L;
  private final static long MAX_BACKOFF_MS = 30 * 60 * 1000L;

  private final MailAccountConfiguration config;
  private final ImapStoreConnector storeConnector;
  private final Runnable onNewMail;
  private volatile Store currentStore;
  private volatile boolean stopped;

  public ImapIdleWatcher(MailAccountConfiguration config, Runnable onNewMail) {
    this(config, ImapIdleWatcher::connectImaps, onNewMail);
  }

  /** Visible for tests: lets a store connector be injected without real IMAPS/TLS. */
  ImapIdleWatcher(MailAccountConfiguration config, ImapStoreConnector storeConnector, Runnable onNewMail) {
    this.config = config;
    this.storeConnector = storeConnector;
    this.onNewMail = onNewMail;
  }

  private static Store connectImaps(MailAccountConfiguration config) throws MessagingException {
    Session session = Session.getDefaultInstance(new Properties());
    Store store = session.getStore("imaps");
    store.connect(config.getHost(), config.getPort(), config.getUsername(), config.getPassword());
    return store;
  }

  /**
   * Stops the watcher for good. Closes the connection to unblock an in-progress {@code idle()}
   * call: it's a blocking socket read, which a plain {@code Thread.interrupt()} cannot break —
   * same caveat as an in-progress main cycle, see {@code Runner.shutdown()}.
   */
  public void shutdown() {
    stopped = true;
    Store store = currentStore;
    if (store != null) {
      try {
        store.close();
      } catch (MessagingException ignored) {
      }
    }
  }

  @Override
  public void run() {
    new BackoffLoop(INITIAL_BACKOFF_MS, MAX_BACKOFF_MS)
        .run("IDLE watcher [" + config.getDisplayName() + "]", this::watchUntilDisconnected);
  }

  private void watchUntilDisconnected() throws MessagingException {
    Store store;
    try {
      store = storeConnector.connect(config);
    } catch (MessagingException e) {
      if (stopped) return; // a connect racing shutdown(): not worth reporting as a failure
      throw e;
    }
    currentStore = store;
    try {
      if (!((IMAPStore) store).hasCapability("IDLE")) {
        LOGGER.warning("IDLE watcher [" + config.getDisplayName() + "]: server does not advertise IDLE "
            + "support, giving up (new mail will still be picked up by the regular runEvery cycle).");
        stopped = true;
        Thread.currentThread().interrupt(); // tells the enclosing BackoffLoop to stop for good
        return;
      }

      IMAPFolder inbox = (IMAPFolder) store.getFolder("INBOX");
      inbox.open(Folder.READ_ONLY);
      inbox.addMessageCountListener(new MessageCountAdapter() {
        @Override
        public void messagesAdded(MessageCountEvent e) {
          onNewMail.run();
        }
      });
      LOGGER.info("IDLE watcher [" + config.getDisplayName() + "] connected, watching INBOX for new mail.");

      while (!stopped && !Thread.currentThread().isInterrupted()) {
        inbox.idle();
      }
    } catch (MessagingException e) {
      if (stopped) return; // shutdown() closed the connection to unblock idle(): not a real failure
      throw e;
    } finally {
      currentStore = null;
      closeQuietly(store);
    }
  }

  private void closeQuietly(Store store) {
    try {
      if (store.isConnected()) store.close();
    } catch (MessagingException e) {
      LOGGER.log(Level.FINE, "Error closing IDLE watcher connection", e);
    }
  }
}
