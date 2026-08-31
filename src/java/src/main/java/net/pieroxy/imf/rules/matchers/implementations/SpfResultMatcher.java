package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.rules.matchers.Matcher;
import net.pieroxy.imf.spf.DnsJavaSpfDnsResolver;
import net.pieroxy.imf.spf.SpfEvaluator;
import net.pieroxy.imf.spf.SpfIdentityExtractor;
import net.pieroxy.imf.spf.SpfResult;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.Optional;

/**
 * Compare le résultat d'une vérification SPF (ex: "pass", "fail", "softfail", "neutral",
 * "none") à la clé configurée, de façon insensible à la casse.
 * <p>
 * La vérification est toujours refaite nous-mêmes, en live, à partir de l'IP connectée (lue
 * dans le {@code Received} le plus récent) et du domaine expéditeur (voir
 * {@link SpfIdentityExtractor}) — jamais lue depuis un header {@code Authentication-Results}
 * ou {@code Received-SPF} préexistant sur le message. Un tel header peut avoir été ajouté par
 * l'expéditeur lui-même (rien ne l'en empêche), et rien ne garantit que l'infrastructure de
 * réception le filtre avant livraison : on ne lui fait donc jamais confiance, même présent.
 */
public class SpfResultMatcher extends Matcher {
  private final SpfEvaluator evaluator;

  public SpfResultMatcher() {
    this(new SpfEvaluator(new DnsJavaSpfDnsResolver()));
  }

  /** Visible pour les tests : permet d'injecter un évaluateur sans résolution DNS réelle. */
  SpfResultMatcher(SpfEvaluator evaluator) {
    this.evaluator = evaluator;
  }

  @Override
  public boolean matches(Message message) throws MessagingException {
    String result = evaluateLive(message);
    boolean matched = result != null && matchesKey(result, String::equalsIgnoreCase);
    getLogger().fine(() -> "tested spf result=" + result + " against " + describeKey()
            + " -> " + (matched ? "match" : "no match"));
    return matched;
  }

  @Override
  public String extractKeyFromExample(Message message) throws MessagingException {
    String result = evaluateLive(message);
    if (result == null) {
      throw new MessagingException("Cannot learn a SPF_RESULT_EQUALS rule: could not determine an SPF result for this message");
    }
    return result;
  }

  private String evaluateLive(Message message) throws MessagingException {
    // getLogger() (niveau piloté par le "logLevel" de CETTE règle dans le JSON) est passé
    // jusqu'au fond de l'extraction et de l'évaluation, pour que "logLevel": "DEBUG" sur la
    // règle suffise à voir toute la trace (header Received examiné, record SPF trouvé,
    // mécanisme par mécanisme) sans toucher à une configuration de logging globale.
    Optional<String> ip = SpfIdentityExtractor.extractClientIp(message, getLogger());
    Optional<String> domain = SpfIdentityExtractor.extractSenderDomain(message, getLogger());
    if (ip.isEmpty() || domain.isEmpty()) {
      getLogger().fine(() -> "Cannot evaluate SPF live: missing client IP or sender domain");
      return null;
    }
    SpfResult result = evaluator.evaluate(ip.get(), domain.get(), getLogger());
    getLogger().fine(() -> "Evaluated SPF live for ip=" + ip.get() + " domain=" + domain.get() + " -> " + result.getCode());
    return result.getCode();
  }
}
