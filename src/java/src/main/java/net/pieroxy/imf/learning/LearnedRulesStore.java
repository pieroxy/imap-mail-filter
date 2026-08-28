package net.pieroxy.imf.learning;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import net.pieroxy.imf.config.MailFilterRuleConfiguration;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persiste, par compte, les règles apprises automatiquement en déplaçant des messages
 * d'exemple dans l'arborescence imf-rules/. Fichier séparé de la config manuelle (config.json)
 * pour ne jamais réécrire ce que l'utilisateur édite lui-même.
 */
public class LearnedRulesStore {
  private final static Logger LOGGER = Logger.getLogger(LearnedRulesStore.class.getName());
  private final static Gson GSON = new Gson();
  private final static Type LIST_TYPE = new TypeToken<List<MailFilterRuleConfiguration>>() {}.getType();

  private final File file;

  public LearnedRulesStore(String dataFolder, String accountKey) {
    this.file = new File(dataFolder, accountKey + "-learned-rules.json");
  }

  public List<MailFilterRuleConfiguration> load() {
    if (!file.exists()) return new ArrayList<>();
    try (FileReader r = new FileReader(file)) {
      List<MailFilterRuleConfiguration> rules = GSON.fromJson(r, LIST_TYPE);
      return rules != null ? rules : new ArrayList<>();
    } catch (IOException | JsonParseException e) {
      LOGGER.log(Level.WARNING, "Could not read learned rules file " + file, e);
      return new ArrayList<>();
    }
  }

  public void save(List<MailFilterRuleConfiguration> rules) {
    file.getParentFile().mkdirs();
    try (FileWriter w = new FileWriter(file)) {
      GSON.toJson(rules, LIST_TYPE, w);
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Could not write learned rules file " + file, e);
    }
  }

  /**
   * Ajoute la règle si aucune règle équivalente (même type+clé de matcher, même type+clé
   * d'action) n'existe déjà.
   * @return true si la règle a effectivement été ajoutée.
   */
  public boolean addIfAbsent(MailFilterRuleConfiguration rule) {
    List<MailFilterRuleConfiguration> rules = load();
    for (MailFilterRuleConfiguration existing : rules) {
      if (sameMatcher(existing, rule) && sameAction(existing, rule)) return false;
    }
    rules.add(rule);
    save(rules);
    return true;
  }

  private boolean sameMatcher(MailFilterRuleConfiguration a, MailFilterRuleConfiguration b) {
    return a.getMatcher().getType() == b.getMatcher().getType()
            && Objects.equals(a.getMatcher().getKey(), b.getMatcher().getKey());
  }

  private boolean sameAction(MailFilterRuleConfiguration a, MailFilterRuleConfiguration b) {
    return a.getAction().getType() == b.getAction().getType()
            && Objects.equals(a.getAction().getKey(), b.getAction().getKey());
  }
}
