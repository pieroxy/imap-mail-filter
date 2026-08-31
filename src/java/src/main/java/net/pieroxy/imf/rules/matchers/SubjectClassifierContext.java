package net.pieroxy.imf.rules.matchers;

import java.io.File;

/**
 * Fait connaître à {@link net.pieroxy.imf.rules.matchers.implementations.SubjectClassifierMatcher}
 * le fichier modèle du compte pour lequel il tourne. Un matcher est construit par
 * {@code MatcherType.getImplementation()} sans aucun contexte (juste un constructeur vide),
 * donc il n'y a pas d'autre moyen de lui faire savoir "de quel compte" il s'agit.
 * <p>
 * Ça tient avec un simple ThreadLocal parce que chaque compte tourne sur un thread dédié et
 * permanent pour toute la durée de vie du process (voir {@code MailAccount}) : ce n'est pas
 * du contexte de requête éphémère, c'est un vrai 1:1 thread/compte, posé une fois par
 * {@code MailAccount.run()} avant tout traitement sur ce thread.
 */
public final class SubjectClassifierContext {
  private static final ThreadLocal<File> MODEL_FILE = new ThreadLocal<>();

  private SubjectClassifierContext() {}

  public static void set(File modelFile) {
    MODEL_FILE.set(modelFile);
  }

  public static File get() {
    return MODEL_FILE.get();
  }
}
