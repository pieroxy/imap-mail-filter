package net.pieroxy.imf.learning;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import net.pieroxy.imf.config.MailFilterRuleConfiguration;
import net.pieroxy.imf.config.MailFilterRuleMatcherConfiguration;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persiste, par compte, les règles apprises automatiquement en déplaçant des messages
 * d'exemple dans l'arborescence imf-rules/. Fichier séparé de la config manuelle (config.json)
 * pour ne jamais réécrire ce que l'utilisateur édite lui-même.
 */
public class LearnedRulesStore {
  private final static Logger LOGGER = Logger.getLogger(LearnedRulesStore.class.getName());
  // Fichier édité à la main en cas de correction (pas d'UI pour l'instant) : indenté pour
  // rester lisible/éditable sans avoir à le reformater soi-même.
  private final static Gson GSON = new GsonBuilder().setPrettyPrinting().create();
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
   * Ajoute la règle si sa clé de matcher n'est pas déjà couverte par une règle équivalente
   * (même type de matcher, même type+clé d'action). Si une telle règle existe déjà mais avec
   * une clé de matcher différente, la nouvelle clé est fusionnée dans la règle existante
   * (matcher.keys) plutôt que d'ajouter une règle entière dupliquée juste pour une clé de
   * plus — beaucoup de règles apprises partagent la même action (ex: plusieurs expéditeurs
   * tous envoyés vers Spam).
   * @return true si la règle a effectivement été ajoutée ou une clé effectivement fusionnée.
   */
  public boolean addIfAbsent(MailFilterRuleConfiguration rule) {
    List<MailFilterRuleConfiguration> rules = load();
    String newKey = rule.getMatcher().getKey();

    for (MailFilterRuleConfiguration existing : rules) {
      if (existing.getMatcher().getType() != rule.getMatcher().getType() || !sameAction(existing, rule)) continue;
      if (hasKey(existing.getMatcher(), newKey)) return false; // déjà appris

      mergeKey(existing.getMatcher(), newKey);
      save(rules);
      return true;
    }

    rules.add(rule);
    save(rules);
    return true;
  }

  private boolean sameAction(MailFilterRuleConfiguration a, MailFilterRuleConfiguration b) {
    return a.getAction().getType() == b.getAction().getType()
            && Objects.equals(a.getAction().getKey(), b.getAction().getKey());
  }

  private boolean hasKey(MailFilterRuleMatcherConfiguration matcher, String key) {
    if (matcher.getKeys() != null) return matcher.getKeys().contains(key);
    return Objects.equals(matcher.getKey(), key);
  }

  /** Convertit key en keys (avec l'ancienne valeur dedans) si besoin, puis y ajoute key. */
  private void mergeKey(MailFilterRuleMatcherConfiguration matcher, String key) {
    Set<String> keys = matcher.getKeys();
    if (keys == null) {
      keys = new LinkedHashSet<>();
      if (matcher.getKey() != null) keys.add(matcher.getKey());
      matcher.setKey(null);
      matcher.setKeys(keys);
    }
    keys.add(key);
  }
}
