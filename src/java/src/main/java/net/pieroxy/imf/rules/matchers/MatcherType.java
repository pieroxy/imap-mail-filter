package net.pieroxy.imf.rules.matchers;

import net.pieroxy.imf.rules.matchers.implementations.AndMatcher;
import net.pieroxy.imf.rules.matchers.implementations.DkimResultMatcher;
import net.pieroxy.imf.rules.matchers.implementations.DmarcResultMatcher;
import net.pieroxy.imf.rules.matchers.implementations.FcrdnsResultMatcher;
import net.pieroxy.imf.rules.matchers.implementations.FromAddressMatcher;
import net.pieroxy.imf.rules.matchers.implementations.FromDomainMatcher;
import net.pieroxy.imf.rules.matchers.implementations.FromExactMatcher;
import net.pieroxy.imf.rules.matchers.implementations.OrMatcher;
import net.pieroxy.imf.rules.matchers.implementations.SpfResultMatcher;
import net.pieroxy.imf.rules.matchers.implementations.SubjectStartsWithMatcher;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum MatcherType {
  FROM_EQUALS(FromExactMatcher::new, true),
  FROM_ADDRESS_EQUALS(FromAddressMatcher::new, true),
  FROM_DOMAIN_EQUALS(FromDomainMatcher::new, true),
  SUBJECT_STARTS_WITH(SubjectStartsWithMatcher::new, true),
  // Pas learnable : la clé possible (pass/fail/softfail/...) est un ensemble fixe et déjà
  // documenté, pas une valeur spécifique à découvrir par l'exemple. Et contrairement à
  // FROM_*, la règle apprise ne serait pas spécifique à l'exemple déposé : elle s'appliquerait
  // globalement à tout message ayant le même statut, pas juste à un expéditeur précis.
  SPF_RESULT_EQUALS(SpfResultMatcher::new, false),
  DKIM_RESULT_EQUALS(DkimResultMatcher::new, false),
  DMARC_RESULT_EQUALS(DmarcResultMatcher::new, false),
  FCRDNS_RESULT_EQUALS(FcrdnsResultMatcher::new, false),
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