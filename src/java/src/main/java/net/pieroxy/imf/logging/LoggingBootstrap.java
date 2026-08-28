package net.pieroxy.imf.logging;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Configure java.util.logging au démarrage. Lève d'abord le plafond des handlers déjà en
 * place (par défaut, le ConsoleHandler du JDK filtre en dessous de INFO) : sans ça, un
 * noeud dont le logLevel est positionné à DEBUG ne s'afficherait jamais, quel que soit son
 * propre niveau, car le handler l'aurait déjà éliminé en amont. Ajoute ensuite, si
 * configuré, un fichier de log recevant tout ce qui passe.
 */
public final class LoggingBootstrap {
  private final static Logger LOGGER = Logger.getLogger(LoggingBootstrap.class.getName());

  private LoggingBootstrap() {}

  public static void configure(String logFile) {
    for (Handler handler : Logger.getLogger("").getHandlers()) {
      handler.setLevel(Level.ALL);
    }
    if (logFile == null || logFile.isBlank()) return;
    try {
      FileHandler handler = new FileHandler(logFile, true);
      handler.setFormatter(new SimpleFormatter());
      handler.setLevel(Level.ALL);
      Logger.getLogger("").addHandler(handler);
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, "Could not open log file " + logFile, e);
    }
  }
}
