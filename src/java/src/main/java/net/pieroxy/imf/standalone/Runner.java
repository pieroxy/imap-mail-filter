package net.pieroxy.imf.standalone;

import com.google.gson.Gson;
import net.pieroxy.imf.config.Configuration;
import net.pieroxy.imf.config.MailAccountConfiguration;
import net.pieroxy.imf.rules.MailAccount;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Runner {
  private final static Logger LOGGER = Logger.getLogger(Runner.class.getName());
  private final static String GIT_REV;
  private final static String MVN_VER;
  private static net.pieroxy.imf.standalone.Services services;
  private static Configuration config;

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
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      LOGGER.warning("Shutting down...");
      // fermer connexions DB, flush logs, sauvegarder état, etc.
    }));
    Gson gson = new Gson();
    Runner.config = gson.fromJson(new FileReader(new File(args[0], "config.json")), Configuration.class);
    config.getConfigurations().forEach(conf -> new Thread(new MailAccount(conf, config.getDataFolder())).start());
  }

  private static boolean has(String[] args, String lookFor) {
    for (String s : args) if (s.equals(lookFor)) return true;
    return false;
  }
}
