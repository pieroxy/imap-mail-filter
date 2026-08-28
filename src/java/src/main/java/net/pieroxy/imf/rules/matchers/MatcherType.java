package net.pieroxy.imf.rules.matchers;

import net.pieroxy.imf.rules.matchers.implementations.FromExactMatcher;

public enum MatcherType {
  FROM_EQUALS(FromExactMatcher::new);

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