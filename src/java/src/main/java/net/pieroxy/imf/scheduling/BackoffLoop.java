package net.pieroxy.imf.scheduling;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Boucle générique "attendre / exécuter" avec backoff exponentiel sur échec (réinitialisé
 * au prochain succès) et arrêt propre sur interruption. Ne connaît rien du travail exécuté,
 * ce qui la rend testable indépendamment de tout compte mail.
 */
public class BackoffLoop {
  public interface Task {
    void run() throws Exception;
  }

  private final static Logger LOGGER = Logger.getLogger(BackoffLoop.class.getName());

  private final long initialDelayMs;
  private final long maxDelayMs;

  public BackoffLoop(long initialDelayMs, long maxDelayMs) {
    this.initialDelayMs = initialDelayMs;
    this.maxDelayMs = maxDelayMs;
  }

  public void run(String name, Task task) {
    long delayMs = initialDelayMs;
    while (!Thread.currentThread().isInterrupted()) {
      try {
        Thread.sleep(delayMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
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
