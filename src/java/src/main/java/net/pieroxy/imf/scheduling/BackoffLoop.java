package net.pieroxy.imf.scheduling;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Boucle générique "exécuter / attendre" avec backoff exponentiel sur échec (réinitialisé
 * au prochain succès) et arrêt propre sur interruption. Le premier cycle s'exécute
 * immédiatement (pas d'attente avant le tout premier run) ; l'attente n'intervient qu'entre
 * deux cycles. Ne connaît rien du travail exécuté, ce qui la rend testable indépendamment de
 * tout compte mail.
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
    boolean firstRun = true;
    while (!Thread.currentThread().isInterrupted()) {
      if (!firstRun) {
        try {
          Thread.sleep(delayMs);
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
