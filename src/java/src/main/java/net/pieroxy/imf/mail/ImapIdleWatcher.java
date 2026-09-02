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
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link BackoffLoop.Waiter} that watches the INBOX via IMAP IDLE (RFC 2177) during what would
 * otherwise be a plain sleep between {@link net.pieroxy.imf.rules.MailAccount#processMessages()}
 * cycles, so new mail wakes the next cycle immediately instead of waiting out the full {@code
 * runEvery}. It does no processing itself — returning from {@link #await} just means "time for a
 * cycle now", whether that's because new mail showed up or because the wait budget ran out.
 * <p>
 * Uses its own short-lived connection, always closed before {@link #await} returns. This isn't
 * just tidiness: some IMAP servers refuse a second, concurrent {@code SELECT} of the same
 * mailbox by the same account, so this watcher must never still hold INBOX open when {@code
 * processMessages()}'s own connection tries to open it right after. Being a {@code Waiter}
 * (called in-line, in the same thread that runs {@code processMessages()}, right before it)
 * makes the two connections sequential by construction instead of relying on synchronization to
 * keep them apart.
 * <p>
 * The wait is sliced into short IDLE calls rather than one continuous {@code idle()} for the
 * whole budget: keeps shutdown responsive (a blocked socket read can't be interrupted by {@code
 * Thread.interrupt()} — see {@code Runner.shutdown()} — so one long wait could otherwise delay
 * process exit by up to the full {@code runEvery}/backoff delay) and stays well under the ~29
 * minute IDLE duration servers are recommended to tolerate (RFC 2177).
 */
public class ImapIdleWatcher implements BackoffLoop.Waiter {
  private final static Logger LOGGER = Logger.getLogger(ImapIdleWatcher.class.getName());
  private final static long SLICE_MS = 2 * 60 * 1000L;

  private final MailAccountConfiguration config;
  private final ImapStoreConnector storeConnector;
  // Once a server proves it doesn't support IDLE, that's a static property of the server: no
  // point reconnecting to re-ask on every single wait for the rest of the process's lifetime.
  private volatile boolean idleUnsupported;

  public ImapIdleWatcher(MailAccountConfiguration config) {
    this(config, ImapIdleWatcher::connectImaps);
  }

  /** Visible for tests: lets a store connector be injected without real IMAPS/TLS. */
  ImapIdleWatcher(MailAccountConfiguration config, ImapStoreConnector storeConnector) {
    this.config = config;
    this.storeConnector = storeConnector;
  }

  private static Store connectImaps(MailAccountConfiguration config) throws MessagingException {
    Session session = Session.getDefaultInstance(new Properties());
    Store store = session.getStore("imaps");
    store.connect(config.getHost(), config.getPort(), config.getUsername(), config.getPassword());
    return store;
  }

  @Override
  public void await(long delayMs) throws InterruptedException {
    if (idleUnsupported) {
      Thread.sleep(delayMs);
      return;
    }
    long deadline = System.currentTimeMillis() + delayMs;
    while (System.currentTimeMillis() < deadline) {
      if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
      long sliceMs = Math.min(deadline - System.currentTimeMillis(), SLICE_MS);
      try {
        if (idleForOneSlice(sliceMs)) return; // new mail: let the caller run a cycle right now
      } catch (MessagingException e) {
        LOGGER.log(Level.FINE, "IDLE watcher [" + config.getDisplayName() + "]: wait failed ("
            + e.getMessage() + "), falling back to a plain wait for the rest of this cycle.", e);
        Thread.sleep(Math.max(0, deadline - System.currentTimeMillis()));
        return;
      }
      if (idleUnsupported) {
        Thread.sleep(Math.max(0, deadline - System.currentTimeMillis()));
        return;
      }
      // Otherwise just this slice's timeout, no new mail: loop again if budget remains.
    }
  }

  /** @return true if new mail arrived in the INBOX during this slice. */
  private boolean idleForOneSlice(long sliceMs) throws MessagingException {
    Store store = storeConnector.connect(config);
    try {
      if (!((IMAPStore) store).hasCapability("IDLE")) {
        LOGGER.warning("IDLE watcher [" + config.getDisplayName() + "]: server does not advertise IDLE "
            + "support, giving up for this account (new mail will still be picked up by the regular runEvery cycle).");
        idleUnsupported = true;
        return false;
      }

      IMAPFolder inbox = (IMAPFolder) store.getFolder("INBOX");
      inbox.open(Folder.READ_ONLY);

      // idle() does not reliably return on its own when new mail arrives, so the listener itself
      // forces it to unblock (by closing the connection) rather than waiting for idle() to
      // return naturally.
      AtomicBoolean newMail = new AtomicBoolean(false);
      inbox.addMessageCountListener(new MessageCountAdapter() {
        @Override
        public void messagesAdded(MessageCountEvent e) {
          newMail.set(true); // happens-before the close() below, so no race reading it after idle() unblocks
          closeQuietly(store);
        }
      });

      // Forces idle() to return once this slice's budget is up if no mail shows up first — same
      // mechanism as the listener above and as a deliberate shutdown: closing the connection
      // breaks the blocking read idle() is waiting on.
      Timer sliceTimer = new Timer("idle-watcher-slice-" + config.getDisplayName(), true);
      sliceTimer.schedule(new TimerTask() {
        @Override
        public void run() {
          closeQuietly(store);
        }
      }, sliceMs);
      try {
        inbox.idle();
      } catch (MessagingException e) {
        // Either of the two closes above, or a genuine connection drop — either way this slice
        // is over; a real connectivity problem still surfaces on the next processMessages()
        // connection attempt.
      } finally {
        sliceTimer.cancel();
      }
      return newMail.get();
    } finally {
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
