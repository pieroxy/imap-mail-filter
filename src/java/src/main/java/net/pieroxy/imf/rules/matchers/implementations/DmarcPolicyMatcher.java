package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.dkim.DkimVerifier;
import net.pieroxy.imf.dmarc.DmarcEvaluator;
import net.pieroxy.imf.dmarc.DmarcMessageEvaluator;
import net.pieroxy.imf.dmarc.DnsJavaDmarcDnsResolver;
import net.pieroxy.imf.rules.matchers.MatchResult;
import net.pieroxy.imf.rules.matchers.Matcher;
import net.pieroxy.imf.spf.DnsJavaSpfDnsResolver;
import net.pieroxy.imf.spf.SpfEvaluator;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.Optional;

/**
 * Compare la politique DMARC effective du domaine expéditeur à la clé configurée : {@code none},
 * {@code quarantine}, {@code reject} (valeurs RFC 7489 du tag {@code p=}/{@code sp=}),
 * {@code unpublished} (le domaine n'a aucun DMARC), {@code permerror}, ou {@code temperror}.
 * Insensible à la casse.
 * <p>
 * Contrairement à {@link DmarcResultMatcher}, ce matcher ne dit rien sur le message précis
 * évalué — juste sur ce que le domaine expéditeur a lui-même publié comme politique. Utile pour
 * graduer la confiance : un {@code DMARC_RESULT_EQUALS: fail} venant d'un domaine en
 * {@code p=reject} est un signal quasi certain de spoofing (le domaine s'est engagé à être
 * strict), alors que le même {@code fail} venant d'un domaine en {@code p=none} peut juste
 * refléter un déploiement SPF/DKIM incomplet côté expéditeur — combinez les deux via {@code AND}
 * pour distinguer les deux cas plutôt que de traiter tous les {@code fail} pareil.
 * <p>
 * {@code unpublished} n'a volontairement pas la même signification que {@code none} : un
 * domaine sans DMARC du tout n'est pas en soi suspect (c'est la norme pour la plupart des
 * petits domaines), alors que {@code p=none} est un choix actif du domaine.
 */
public class DmarcPolicyMatcher extends Matcher {
  private final DmarcMessageEvaluator evaluator;

  public DmarcPolicyMatcher() {
    this(new DmarcMessageEvaluator(new SpfEvaluator(new DnsJavaSpfDnsResolver()),
            DkimVerifier.createDefault(),
            new DmarcEvaluator(new DnsJavaDmarcDnsResolver())));
  }

  /** Visible pour les tests : permet d'injecter un évaluateur sans réseau. */
  DmarcPolicyMatcher(DmarcMessageEvaluator evaluator) {
    this.evaluator = evaluator;
  }

  @Override
  public MatchResult matches(Message message) throws MessagingException {
    Optional<String> policy = evaluate(message);
    Optional<String> hit = policy.flatMap(p -> matchingKey(p, String::equalsIgnoreCase));
    getLogger().fine(() -> "tested dmarc policy=" + policy.orElse(null) + " against " + describeKey()
            + " -> " + (hit.isPresent() ? "match" : "no match"));
    return hit.map(this::matched).orElseGet(this::notMatched);
  }

  @Override
  public String extractKeyFromExample(Message message) throws MessagingException {
    return evaluate(message).orElseThrow(() ->
            new MessagingException("Cannot learn a DMARC_POLICY_EQUALS rule: could not determine a From domain for this message"));
  }

  private Optional<String> evaluate(Message message) throws MessagingException {
    return evaluator.evaluate(message, getLogger()).map(e -> e.policy().getCode());
  }
}
