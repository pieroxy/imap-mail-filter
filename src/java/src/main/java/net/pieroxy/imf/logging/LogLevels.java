package net.pieroxy.imf.logging;

import java.util.logging.Level;

/**
 * Traduit le niveau de log tel qu'écrit dans config.json (DEBUG/INFO/WARNING/ERROR) vers
 * un java.util.logging.Level. java.util.logging n'a pas de niveau DEBUG natif : on le
 * fait correspondre à FINE.
 */
public final class LogLevels {
  private LogLevels() {}

  public static Level parse(String configured, Level defaultLevel) {
    if (configured == null || configured.isBlank()) return defaultLevel;
    switch (configured.trim().toUpperCase()) {
      case "DEBUG": return Level.FINE;
      case "INFO": return Level.INFO;
      case "WARNING":
      case "WARN": return Level.WARNING;
      case "ERROR":
      case "SEVERE": return Level.SEVERE;
      default: return defaultLevel;
    }
  }
}
