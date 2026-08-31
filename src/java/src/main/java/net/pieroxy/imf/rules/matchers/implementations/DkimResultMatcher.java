package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.dkim.DkimResult;
import net.pieroxy.imf.dkim.DkimVerifier;
import net.pieroxy.imf.rules.matchers.MatchResult;
import net.pieroxy.imf.rules.matchers.Matcher;
import org.apache.james.jdkim.api.PublicKeyRecordRetriever;
import org.apache.james.jdkim.impl.DNSPublicKeyRecordRetriever;
import org.xbill.DNS.SimpleResolver;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

/**
 * Compare le résultat d'une vérification DKIM (ex: "pass", "fail", "none", "permerror") à la
 * clé configurée, de façon insensible à la casse.
 * <p>
 * Comme pour {@link SpfResultMatcher}, la vérification est toujours refaite nous-mêmes, en
 * live, sur le message brut (headers + corps tels que reçus) — jamais lue depuis un header
 * {@code Authentication-Results} préexistant, pour la même raison : rien n'empêche l'expéditeur
 * d'en avoir inséré un lui-même. La cryptographie (canonicalisation RFC 6376, vérification de
 * signature RSA/Ed25519) est déléguée à {@code org.apache.james.jdkim} via {@link DkimVerifier}
 * plutôt que réimplémentée : contrairement au SPF, une seule divergence d'implémentation ferait
 * échouer silencieusement des signatures pourtant valides.
 */
public class DkimResultMatcher extends Matcher {
  private final DkimVerifier verifier;

  public DkimResultMatcher() {
    this(new DkimVerifier(defaultPublicKeyRecordRetriever()));
  }

  /** Visible pour les tests : permet d'injecter un vérificateur sans résolution DNS réelle. */
  DkimResultMatcher(DkimVerifier verifier) {
    this.verifier = verifier;
  }

  private static PublicKeyRecordRetriever defaultPublicKeyRecordRetriever() {
    try {
      SimpleResolver resolver = new SimpleResolver();
      resolver.setTimeout(Duration.ofSeconds(5));
      return new DNSPublicKeyRecordRetriever(resolver);
    } catch (IOException e) {
      throw new IllegalStateException("Could not initialize DNS resolver for DKIM", e);
    }
  }

  @Override
  public MatchResult matches(Message message) throws MessagingException {
    String result = evaluateDkim(message);
    Optional<String> hit = result != null ? matchingKey(result, String::equalsIgnoreCase) : Optional.empty();
    getLogger().fine(() -> "tested dkim result=" + result + " against " + describeKey()
            + " -> " + (hit.isPresent() ? "match" : "no match"));
    return hit.map(this::matched).orElseGet(this::notMatched);
  }

  @Override
  public String extractKeyFromExample(Message message) throws MessagingException {
    String result = evaluateDkim(message);
    if (result == null) {
      throw new MessagingException("Cannot learn a DKIM_RESULT_EQUALS rule: could not determine a DKIM result for this message");
    }
    return result;
  }

  private String evaluateDkim(Message message) throws MessagingException {
    byte[] raw;
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      message.writeTo(out);
      raw = out.toByteArray();
    } catch (IOException e) {
      throw new MessagingException("Failed to read message for DKIM verification", e);
    }
    // getLogger() (niveau piloté par le "logLevel" de CETTE règle dans le JSON) : même
    // convention que SpfResultMatcher, voir sa javadoc.
    DkimResult result = verifier.verify(new ByteArrayInputStream(raw), getLogger());
    getLogger().fine(() -> "Evaluated DKIM -> " + result.getCode());
    return result.getCode();
  }
}
