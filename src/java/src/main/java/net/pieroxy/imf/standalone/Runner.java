package net.pieroxy.imf.standalone;

import com.google.gson.Gson;
import net.pieroxy.imf.config.Configuration;
import net.pieroxy.imf.config.MailAccountConfiguration;
import net.pieroxy.imf.logging.LoggingBootstrap;
import net.pieroxy.imf.reputation.ReputationRegistry;
import net.pieroxy.imf.reputation.ReputationRegistryHolder;
import net.pieroxy.imf.rules.MailAccount;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Runner {
  private final static Logger LOGGER = Logger.getLogger(Runner.class.getName());
  private final static long SHUTDOWN_JOIN_TIMEOUT_MS = 5000;
  private final static String GIT_REV;
  private final static String MVN_VER;
  private static Configuration config;
  private static final List<Thread> accountThreads = new ArrayList<>();
  private static ReputationRegistry reputationRegistry;

  static {
    GIT_REV = readResourceFileAsString("GIT_REV");
    MVN_VER = readResourceFileAsString("MVN_VER");
  }

  private static String readResourceFileAsString(String filename) {
    try {
      InputStream is = Runner.class.getClassLoader().getResourceAsStream(filename);
      return new BufferedReader(new InputStreamReader(is)).lines().findFirst().get();
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, "Could not read " + filename, e);
      return filename + "_" + Math.random();
    }
  }

  public static void main(String[] args) throws Exception {
    Gson gson = new Gson();
    Runner.config = gson.fromJson(new FileReader(new File(args[0], "config.json")), Configuration.class);
    LoggingBootstrap.configure(config.getLogFile(), config.getKeepLogFiles());

    reputationRegistry = new ReputationRegistry(config.getReputationLists(), config.getDataFolder());
    reputationRegistry.start();
    ReputationRegistryHolder.set(reputationRegistry);

    config.getConfigurations().forEach(conf -> {
      MailAccount account = new MailAccount(conf, config.getDataFolder(), config.getClassifierCorpusRetentionDays());
      Thread t = new Thread(account, "mail-account-" + conf.getDisplayName());
      accountThreads.add(t);
      t.start();
    });

    Runtime.getRuntime().addShutdownHook(new Thread(Runner::shutdown, "shutdown-hook"));
    LOGGER.info("Started IMAP-MAIL-FILTER version " + MVN_VER + " rev " + GIT_REV);
  }

  private static void shutdown() {
    logDirectly("Shutting down, interrupting " + accountThreads.size() + " account thread(s)...");
    // Un cycle IMAP déjà en cours (I/O bloquante socket) ne sera pas interrompu à chaud ;
    // ceci empêche seulement le déclenchement d'un nouveau cycle et laisse un cycle en cours
    // se terminer dans la limite du timeout ci-dessous.
    accountThreads.forEach(Thread::interrupt);
    for (Thread t : accountThreads) {
      try {
        t.join(SHUTDOWN_JOIN_TIMEOUT_MS);
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
      }
    }
    if (reputationRegistry != null) {
      reputationRegistry.stop();
    }
    LoggingBootstrap.shutdown();
    logDirectly("Shutdown complete.");
  }

  /**
   * java.util.logging installe son propre shutdown hook (LogManager) qui réinitialise les
   * handlers ; l'ordre d'exécution entre hooks concurrents n'est pas garanti, donc un appel à
   * LOGGER ici peut silencieusement disparaître selon lequel des deux hooks passe en premier.
   * On écrit donc directement sur stderr et dans le fichier de log, seuls canaux fiables à ce
   * stade de l'arrêt.
   */
  private static void logDirectly(String message) {
    String line = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " " + message;
    System.err.println(line);
    String logFile = config.getLogFile();
    if (logFile != null && !logFile.isBlank()) {
      try (FileWriter writer = new FileWriter(logFile, true)) {
        writer.write(line + System.lineSeparator());
      } catch (IOException ignored) {
        // best effort : rien de plus fiable à faire à ce stade de l'arrêt.
      }
    }
  }

  private static boolean has(String[] args, String lookFor) {
    for (String s : args) if (s.equals(lookFor)) return true;
    return false;
  }
}
