package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.config.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.reputation.ReputationRegistry;
import net.pieroxy.imf.reputation.ReputationRegistryHolder;
import net.pieroxy.imf.rules.matchers.MatchResult;
import net.pieroxy.imf.rules.matchers.Matcher;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Comme {@link IpReputationMatcher}, mais sur le domaine de l'adresse {@code From:} plutôt que
 * l'IP connectante, contre des listes de type {@code DOMAIN}.
 */
public class FromDomainReputationMatcher extends Matcher {
  private final ReputationRegistry registry;
  private Set<String> listIds;
  private ReputationThreshold threshold;

  public FromDomainReputationMatcher() {
    this(ReputationRegistryHolder.get());
  }

  /** Visible pour les tests : permet d'injecter un registre sans téléchargement réel. */
  FromDomainReputationMatcher(ReputationRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void setConfig(MailFilterRuleMatcherConfiguration config) {
    super.setConfig(config);
    listIds = config.getListIds();
    if (listIds == null || listIds.isEmpty()) {
      throw new IllegalArgumentException("FROM_DOMAIN_REPUTATION_EQUALS requires at least one listId");
    }
    threshold = ReputationThreshold.parse(config.getKey(), "FROM_DOMAIN_REPUTATION_EQUALS");
  }

  @Override
  protected String describeKey() {
    return getConfig().getKey() + " in " + listIds;
  }

  @Override
  public MatchResult matches(Message message) throws MessagingException {
    Address[] froms = message.getFrom();
    if (froms == null || froms.length != 1) {
      getLogger().fine(() -> "no single From address on message, no match against " + threshold);
      return notMatched();
    }
    String domain = extractDomain(froms[0]);
    if (domain == null) {
      getLogger().fine(() -> "From address has no domain part, no match against " + threshold);
      return notMatched();
    }
    OptionalDouble score = registry.domainScore(domain, listIds);
    if (score.isEmpty()) {
      getLogger().fine(() -> "from domain=" + domain + " not present in any referenced reputation list");
      return notMatched();
    }
    boolean matched = threshold.test(score.getAsDouble());
    getLogger().fine(() -> "from domain=" + domain + " reputation score=" + score.getAsDouble() + " against " + threshold
        + " -> " + (matched ? "match" : "no match"));
    return matched ? matched("score=" + score.getAsDouble()) : notMatched();
  }

  private static String extractDomain(Address address) {
    String raw = address instanceof InternetAddress ? ((InternetAddress) address).getAddress() : address.toString();
    if (raw == null) return null;
    int at = raw.lastIndexOf('@');
    return at >= 0 && at < raw.length() - 1 ? raw.substring(at + 1) : null;
  }
}
