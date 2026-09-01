package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.config.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.reputation.ReputationRegistry;
import net.pieroxy.imf.reputation.ReputationRegistryHolder;
import net.pieroxy.imf.rules.matchers.MatchResult;
import net.pieroxy.imf.rules.matchers.Matcher;
import net.pieroxy.imf.spf.SpfIdentityExtractor;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Compare le score de réputation de l'IP connectante (0=ok, 1=spam, le pire parmi les listes
 * {@code IP_CIDR} référencées par {@code listIds} qui la contiennent) à un seuil
 * ({@code ">0.5"}, {@code "<=0.2"}...). Les listes elles-mêmes sont téléchargées et rafraîchies
 * une fois pour tout le process, jamais interrogées en direct par message — voir
 * {@link ReputationRegistry}.
 */
public class IpReputationMatcher extends Matcher {
  private final ReputationRegistry registry;
  private Set<String> listIds;
  private ReputationThreshold threshold;

  public IpReputationMatcher() {
    this(ReputationRegistryHolder.get());
  }

  /** Visible pour les tests : permet d'injecter un registre sans téléchargement réel. */
  IpReputationMatcher(ReputationRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void setConfig(MailFilterRuleMatcherConfiguration config) {
    super.setConfig(config);
    listIds = config.getListIds();
    if (listIds == null || listIds.isEmpty()) {
      throw new IllegalArgumentException("IP_REPUTATION_EQUALS requires at least one listId");
    }
    threshold = ReputationThreshold.parse(config.getKey(), "IP_REPUTATION_EQUALS");
  }

  @Override
  protected String describeKey() {
    return getConfig().getKey() + " in " + listIds;
  }

  @Override
  public MatchResult matches(Message message) throws MessagingException {
    Optional<String> ip = SpfIdentityExtractor.extractClientIp(message, getLogger());
    if (ip.isEmpty()) {
      getLogger().fine(() -> "no connecting IP found on message, no match against " + threshold);
      return notMatched();
    }
    OptionalDouble score = registry.ipScore(ip.get(), listIds);
    if (score.isEmpty()) {
      getLogger().fine(() -> "ip=" + ip.get() + " not present in any referenced reputation list");
      return notMatched();
    }
    boolean matched = threshold.test(score.getAsDouble());
    getLogger().fine(() -> "ip=" + ip.get() + " reputation score=" + score.getAsDouble() + " against " + threshold
        + " -> " + (matched ? "match" : "no match"));
    return matched ? matched("score=" + score.getAsDouble()) : notMatched();
  }
}
