package net.pieroxy.imf.reputation;

/**
 * Donne accès au {@link ReputationRegistry} du process aux matchers, construits sans contexte
 * par {@code MatcherType.getImplementation()} (juste un constructeur vide) — pas d'autre moyen
 * de le leur transmettre. Contrairement à {@code SubjectClassifierContext} (un ThreadLocal, une
 * valeur par compte/thread), un seul registre sert tout le process : posé une fois par
 * {@code Runner.main} avant que le premier compte démarre, lu par tous les threads ensuite.
 * Défaut à {@link ReputationRegistry#empty()} pour qu'un matcher construit hors de ce
 * démarrage normal (typiquement un test qui ne s'intéresse pas à la réputation) ne plante pas.
 */
public final class ReputationRegistryHolder {
  private static volatile ReputationRegistry instance = ReputationRegistry.empty();

  private ReputationRegistryHolder() {}

  public static void set(ReputationRegistry registry) {
    instance = registry;
  }

  public static ReputationRegistry get() {
    return instance;
  }
}
