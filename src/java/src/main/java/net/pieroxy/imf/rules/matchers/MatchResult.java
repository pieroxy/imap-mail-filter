package net.pieroxy.imf.rules.matchers;

/**
 * Résultat d'un test de matcher : le booléen, plus une description lisible de ce qui a matché
 * (ex: {@code "FromDomainMatcher(gmail.com)"}) — utile dans les logs pour savoir précisément
 * pourquoi une règle s'est déclenchée, notamment laquelle des éventuelles plusieurs "keys"
 * configurées a fait mouche. {@code debugString} vaut {@code null} quand {@code matched} est
 * faux : il n'y a rien à expliquer sur un non-match.
 */
public record MatchResult(boolean matched, String debugString) {
  public static MatchResult matched(String debugString) {
    return new MatchResult(true, debugString);
  }

  public static MatchResult notMatched() {
    return new MatchResult(false, null);
  }
}
