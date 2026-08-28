package net.pieroxy.imf.rules;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persiste l'état de progression (dernier UID traité) d'un compte sur disque, dans un
 * fichier JSON par compte nommé d'après sa clé (le displayName).
 */
public class MailAccountStateStore {
  private final static Logger LOGGER = Logger.getLogger(MailAccountStateStore.class.getName());
  private final static Gson GSON = new Gson();

  private final File stateFile;

  public MailAccountStateStore(String dataFolder, String accountKey) {
    this.stateFile = new File(dataFolder, accountKey + ".json");
  }

  public MailAccountState load() {
    if (!stateFile.exists()) return new MailAccountState();
    try (FileReader r = new FileReader(stateFile)) {
      MailAccountState state = GSON.fromJson(r, MailAccountState.class);
      return state != null ? state : new MailAccountState();
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Could not read state file " + stateFile, e);
      return new MailAccountState();
    }
  }

  public void save(MailAccountState state) {
    stateFile.getParentFile().mkdirs();
    try (FileWriter w = new FileWriter(stateFile)) {
      GSON.toJson(state, w);
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Could not write state file " + stateFile, e);
    }
  }
}
