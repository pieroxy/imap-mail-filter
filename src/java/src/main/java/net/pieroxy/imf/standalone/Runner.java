package net.pieroxy.imf.standalone;

import com.google.gson.Gson;
import net.pieroxy.imf.config.Configuration;
import net.pieroxy.imf.config.MailAccountConfiguration;
import net.pieroxy.imf.logging.LoggingBootstrap;
import net.pieroxy.imf.rules.MailAccount;

import java.io.*;
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

    config.getConfigurations().forEach(conf -> {
      Thread t = new Thread(new MailAccount(conf, config.getDataFolder()), "mail-account-" + conf.getDisplayName());
      accountThreads.add(t);
      t.start();
    });

    Runtime.getRuntime().addShutdownHook(new Thread(Runner::shutdown, "shutdown-hook"));
  }

  private static void shutdown() {
    LOGGER.warning("Shutting down, interrupting " + accountThreads.size() + " account thread(s)...");
    // NB: un cycle IMAP déjà en cours (I/O bloquante socket) ne sera pas interrompu à chaud ;
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
    LoggingBootstrap.shutdown();
    LOGGER.warning("Shutdown complete.");
  }

  private static boolean has(String[] args, String lookFor) {
    for (String s : args) if (s.equals(lookFor)) return true;
    return false;
  }
}
