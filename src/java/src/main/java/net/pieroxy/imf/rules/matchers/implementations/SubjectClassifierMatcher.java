package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.classifier.ClassifierLabel;
import net.pieroxy.imf.config.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.rules.matchers.MatchResult;
import net.pieroxy.imf.rules.matchers.Matcher;
import net.pieroxy.imf.rules.matchers.SubjectClassifierContext;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.tokenize.SimpleTokenizer;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Classifie le sujet du message via le modèle entraîné par
 * {@link net.pieroxy.imf.classifier.SubjectClassifierTrainer} sur le corpus collecté par
 * {@link net.pieroxy.imf.classifier.ClassifierCorpusScanner}. Contrairement aux autres
 * matchers "feuille", pas d'apprentissage par dépôt d'exemple dans imf-rules/ (il n'y a pas de
 * "clé" à extraire d'un message) : l'apprentissage vient du corpus, la clé de config est un
 * seuil de probabilité (ex: "&gt;0.9", "&lt;0.1") plutôt qu'une valeur à comparer.
 * <p>
 * Pour comparer confiant/pas sûr sans complexifier le contrat de {@link Matcher} (qui reste
 * booléen), on configure deux règles à deux seuils différents plutôt qu'un résultat à
 * plusieurs niveaux de confiance — ex: "&gt;0.99" vers une action ferme, "&gt;0.5" vers une
 * action plus prudente.
 */
public class SubjectClassifierMatcher extends Matcher {
  private final static Pattern THRESHOLD_PATTERN = Pattern.compile("(>=|<=|>|<)\\s*([0-9]*\\.?[0-9]+)");

  private String operator;
  private double threshold;

  private DocumentCategorizerME categorizer;
  private long loadedModelMtime = -1;
  private boolean loggedInactive;

  @Override
  public void setConfig(MailFilterRuleMatcherConfiguration config) {
    super.setConfig(config);
    String key = config.getKey();
    java.util.regex.Matcher m = THRESHOLD_PATTERN.matcher(key == null ? "" : key.trim());
    if (!m.matches()) {
      throw new IllegalArgumentException("SUBJECT_CLASSIFIER_EQUALS key must look like \">0.9\" or \"<0.1\", got: " + key);
    }
    operator = m.group(1);
    threshold = Double.parseDouble(m.group(2));

    // Vérifie/logge l'état tout de suite (actif ou pas, et pourquoi) plutôt que d'attendre le
    // premier message inspecté — MailAccount construit le catalogue de règles dès le démarrage
    // précisément pour ça (voir MailAccount.run()) : sur un compte qui ne reçoit rien tout de
    // suite, on saurait sinon jamais si ce matcher est opérationnel.
    loadCategorizer();
  }

  @Override
  public MatchResult matches(Message message) throws MessagingException {
    String subject = message.getSubject();
    if (subject == null || subject.isBlank()) {
      getLogger().fine(() -> "no subject on message, no match against " + describeKey());
      return notMatched();
    }

    DocumentCategorizerME model = loadCategorizer();
    if (model == null) {
      return notMatched();
    }

    String[] tokens = SimpleTokenizer.INSTANCE.tokenize(subject);
    Map<String, Double> scores = model.scoreMap(tokens);
    double spamScore = scores.getOrDefault(ClassifierLabel.SPAM.name(), 0.0);
    boolean matched = passesThreshold(spamScore);
    getLogger().fine(() -> "subject spam score=" + spamScore + " against " + describeKey()
        + " -> " + (matched ? "match" : "no match"));
    return matched ? matched("score=" + spamScore) : notMatched();
  }

  private boolean passesThreshold(double score) {
    return switch (operator) {
      case ">" -> score > threshold;
      case ">=" -> score >= threshold;
      case "<" -> score < threshold;
      case "<=" -> score <= threshold;
      default -> throw new IllegalStateException("Unknown operator: " + operator);
    };
  }

  /**
   * Charge/recharge le modèle si besoin. Le matcher n'est reconstruit que quand une règle
   * apprise change (voir RuleCatalog) — jamais quand ce modèle-ci est réentraîné, puisqu'il
   * n'est pas appris par dossier. On détecte donc nous-mêmes qu'un nouveau modèle est apparu
   * via sa date de dernière modification, plutôt que de dépendre de ce cycle d'invalidation.
   */
  private DocumentCategorizerME loadCategorizer() {
    File modelFile = SubjectClassifierContext.get();
    if (modelFile == null) {
      // Ne doit pas arriver en usage normal (MailAccount.run() positionne le contexte avant
      // tout traitement sur ce thread) ; le signaler clairement plutôt que planter plus loin.
      logInactiveOnce("no classifier model context for this thread");
      return null;
    }

    long currentMtime = modelFile.lastModified(); // 0 si le fichier n'existe pas
    if (currentMtime == 0) {
      categorizer = null;
      loadedModelMtime = -1;
      logInactiveOnce("no trained model yet for this account (not enough data collected so far)");
      return null;
    }

    if (categorizer == null || currentMtime != loadedModelMtime) {
      try {
        categorizer = new DocumentCategorizerME(new DoccatModel(modelFile));
        loadedModelMtime = currentMtime;
        loggedInactive = false; // redevenu actif : une future disparition sera re-logguée
        getLogger().info("Loaded classifier model " + modelFile + " (trained " + Instant.ofEpochMilli(currentMtime) + ")");
      } catch (IOException e) {
        getLogger().log(Level.WARNING, "Failed to load classifier model " + modelFile, e);
        categorizer = null;
      }
    }
    return categorizer;
  }

  private void logInactiveOnce(String reason) {
    if (!loggedInactive) {
      getLogger().info("SubjectClassifierMatcher inactive: " + reason);
      loggedInactive = true;
    }
  }
}
