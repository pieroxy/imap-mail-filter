package net.pieroxy.imf.rules.matchers;

import net.pieroxy.imf.rules.matchers.implementations.AndMatcher;
import net.pieroxy.imf.rules.matchers.implementations.FromAddressMatcher;
import net.pieroxy.imf.rules.matchers.implementations.FromExactMatcher;
import net.pieroxy.imf.rules.matchers.implementations.OrMatcher;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum MatcherType {
  FROM_EQUALS(FromExactMatcher::new, true),
  FROM_ADDRESS_EQUALS(FromAddressMatcher::new, true),
  AND(AndMatcher::new, false),
  OR(OrMatcher::new, false);

  private final MatcherProvider provider;
  private final boolean learnable;

  MatcherType(MatcherProvider provider, boolean learnable) {
    this.provider = provider;
    this.learnable = learnable;
  }

  public Matcher getImplementation() {
    return provider.getMatcher();
  }

  /**
   * Types "feuille" pour lesquels l'apprentissage de règle par l'exemple (dossiers imf-rules/)
   * a un sens. Les composites (AND/OR) en sont exclus : réservés à la config manuelle.
   */
  public static List<MatcherType> learnableValues() {
    return Arrays.stream(values()).filter(t -> t.learnable).collect(Collectors.toList());
  }
}

interface MatcherProvider {
  Matcher getMatcher();
}