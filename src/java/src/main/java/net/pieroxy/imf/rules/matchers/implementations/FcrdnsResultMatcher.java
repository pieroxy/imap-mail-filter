package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.fcrdns.DnsJavaFcrdnsDnsResolver;
import net.pieroxy.imf.fcrdns.FcrdnsEvaluator;
import net.pieroxy.imf.fcrdns.FcrdnsResult;
import net.pieroxy.imf.rules.matchers.MatchResult;
import net.pieroxy.imf.rules.matchers.Matcher;
import net.pieroxy.imf.spf.SpfIdentityExtractor;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.Optional;

/**
 * Compare le résultat d'un contrôle FCrDNS (Forward-Confirmed reverse DNS) à la clé configurée :
 * {@code pass}, {@code fail}, {@code none}, ou {@code temperror}. Insensible à la casse.
 * <p>
 * Contrairement à {@link SpfResultMatcher}/{@link DkimResultMatcher}/{@link DmarcResultMatcher},
 * ce n'est pas un standard d'authentification de domaine — il ne dit rien sur le domaine
 * expéditeur ({@code From:}/{@code Return-Path}), seulement si l'IP qui s'est connectée a un
 * reverse DNS cohérent et forward-confirmé. Voir {@link FcrdnsEvaluator} pour le détail et ses
 * limites.
 */
public class FcrdnsResultMatcher extends Matcher {
  private final FcrdnsEvaluator evaluator;

  public FcrdnsResultMatcher() {
    this(new FcrdnsEvaluator(new DnsJavaFcrdnsDnsResolver()));
  }

  /** Visible pour les tests : permet d'injecter un évaluateur sans résolution DNS réelle. */
  FcrdnsResultMatcher(FcrdnsEvaluator evaluator) {
    this.evaluator = evaluator;
  }

  @Override
  public MatchResult matches(Message message) throws MessagingException {
    String result = evaluateFcrdns(message);
    Optional<String> hit = result != null ? matchingKey(result, String::equalsIgnoreCase) : Optional.empty();
    getLogger().fine(() -> "tested fcrdns result=" + result + " against " + describeKey()
            + " -> " + (hit.isPresent() ? "match" : "no match"));
    return hit.map(this::matched).orElseGet(this::notMatched);
  }

  @Override
  public String extractKeyFromExample(Message message) throws MessagingException {
    String result = evaluateFcrdns(message);
    if (result == null) {
      throw new MessagingException("Cannot learn a FCRDNS_RESULT_EQUALS rule: could not determine the connecting IP for this message");
    }
    return result;
  }

  private String evaluateFcrdns(Message message) throws MessagingException {
    Optional<String> ip = SpfIdentityExtractor.extractClientIp(message, getLogger());
    if (ip.isEmpty()) {
      getLogger().fine(() -> "Cannot evaluate FCrDNS: no connecting IP found");
      return null;
    }
    FcrdnsResult result = evaluator.evaluate(ip.get(), getLogger());
    getLogger().fine(() -> "Evaluated FCrDNS for ip=" + ip.get() + " -> " + result.getCode());
    return result.getCode();
  }
}
