package net.pieroxy.imf.rules.matchers;

import net.pieroxy.imf.rules.matchers.implementations.AndMatcher;
import net.pieroxy.imf.rules.matchers.implementations.FromExactMatcher;
import net.pieroxy.imf.rules.matchers.implementations.OrMatcher;

public enum MatcherType {
  FROM_EQUALS(FromExactMatcher::new),
  AND(AndMatcher::new),
  OR(OrMatcher::new);

  private final MatcherProvider provider;
  MatcherType(MatcherProvider provider) {
    this.provider = provider;
  }

  public Matcher getImplementation() {
    return provider.getMatcher();
  }
}

interface MatcherProvider {
  Matcher getMatcher();
}