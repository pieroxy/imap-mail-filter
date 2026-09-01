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
 * Compare le résultat d'une évaluation DMARC (RFC 7489) à la clé configurée : {@code pass},
 * {@code fail}, {@code none}, {@code permerror}, ou {@code temperror}. Insensible à la casse.
 * <p>
 * S'appuie sur SPF et DKIM déjà implémentés ({@link SpfResultMatcher}, {@link DkimResultMatcher}) :
 * les deux sont recalculés en live (même politique de confiance que les autres matchers de ce
 * paquet — jamais un header préexistant n'est lu), puis {@link DmarcEvaluator} détermine si
 * l'un des deux est "aligné" avec le domaine affiché dans {@code From:}.
 * <p>
 * Voir aussi {@link DmarcPolicyMatcher}, qui expose la politique publiée par le domaine
 * ({@code p=}/{@code sp=}) plutôt que le verdict pass/fail — les deux partagent le même calcul
 * via {@link DmarcMessageEvaluator}.
 */
public class DmarcResultMatcher extends Matcher {
  private final DmarcMessageEvaluator evaluator;

  public DmarcResultMatcher() {
    this(new DmarcMessageEvaluator(new SpfEvaluator(new DnsJavaSpfDnsResolver()),
            DkimVerifier.createDefault(),
            new DmarcEvaluator(new DnsJavaDmarcDnsResolver())));
  }

  /** Visible pour les tests : permet d'injecter un évaluateur sans réseau. */
  DmarcResultMatcher(DmarcMessageEvaluator evaluator) {
    this.evaluator = evaluator;
  }

  @Override
  public MatchResult matches(Message message) throws MessagingException {
    Optional<String> result = evaluate(message);
    Optional<String> hit = result.flatMap(r -> matchingKey(r, String::equalsIgnoreCase));
    getLogger().fine(() -> "tested dmarc result=" + result.orElse(null) + " against " + describeKey()
            + " -> " + (hit.isPresent() ? "match" : "no match"));
    return hit.map(this::matched).orElseGet(this::notMatched);
  }

  @Override
  public String extractKeyFromExample(Message message) throws MessagingException {
    return evaluate(message).orElseThrow(() ->
            new MessagingException("Cannot learn a DMARC_RESULT_EQUALS rule: could not determine a From domain for this message"));
  }

  private Optional<String> evaluate(Message message) throws MessagingException {
    return evaluator.evaluate(message, getLogger()).map(e -> e.result().getCode());
  }
}
