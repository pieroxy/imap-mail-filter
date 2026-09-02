package net.pieroxy.imf.scheduling;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generic "run / wait" loop with exponential backoff on failure (reset on the next success) and
 * clean shutdown on interruption. The first cycle runs immediately (no wait before the very
 * first run); the wait only happens between cycles. The inter-cycle wait can be cut short via
 * {@link #wake()}, letting an external event (e.g. IMAP IDLE noticing new mail) trigger an early
 * cycle without shrinking the normal interval. Knows nothing about the work it runs, which makes
 * it testable independently of any mail account.
 */
public class BackoffLoop {
  public interface Task {
    void run() throws Exception;
  }

  private final static Logger LOGGER = Logger.getLogger(BackoffLoop.class.getName());
  private final static Object WAKE = new Object();

  private final long initialDelayMs;
  private final long maxDelayMs;
  // Capacity 1: several wake() calls before the loop actually wakes up collapse into a single
  // early cycle, which is all a caller needs ("run soon"), not one extra cycle per call.
  private final LinkedBlockingQueue<Object> wakeSignal = new LinkedBlockingQueue<>(1);

  public BackoffLoop(long initialDelayMs, long maxDelayMs) {
    this.initialDelayMs = initialDelayMs;
    this.maxDelayMs = maxDelayMs;
  }

  /** Cuts the current inter-cycle wait short, if any, so the next cycle starts right away. */
  public void wake() {
    wakeSignal.offer(WAKE);
  }

  public void run(String name, Task task) {
    long delayMs = initialDelayMs;
    boolean firstRun = true;
    while (!Thread.currentThread().isInterrupted()) {
      if (!firstRun) {
        try {
          wakeSignal.poll(delayMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
      firstRun = false;
      try {
        task.run();
        delayMs = initialDelayMs;
      } catch (Exception e) {
        delayMs = Math.min(delayMs * 2, maxDelayMs);
        LOGGER.log(Level.WARNING, name + ": " + e.getMessage() + ". Next retry in " + delayMs + "ms.", e);
      }
    }
    LOGGER.info(name + " stopped.");
  }
}
