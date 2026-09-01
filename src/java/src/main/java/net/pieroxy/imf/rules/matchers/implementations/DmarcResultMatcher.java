package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.dkim.DkimVerification;
import net.pieroxy.imf.dkim.DkimVerifier;
import net.pieroxy.imf.dmarc.DmarcEvaluator;
import net.pieroxy.imf.dmarc.DmarcResult;
import net.pieroxy.imf.dmarc.DnsJavaDmarcDnsResolver;
import net.pieroxy.imf.rules.matchers.MatchResult;
import net.pieroxy.imf.rules.matchers.Matcher;
import net.pieroxy.imf.spf.DnsJavaSpfDnsResolver;
import net.pieroxy.imf.spf.SpfEvaluator;
import net.pieroxy.imf.spf.SpfIdentityExtractor;
import net.pieroxy.imf.spf.SpfResult;
import net.pieroxy.imf.utils.MailTools;
import org.apache.james.jdkim.api.PublicKeyRecordRetriever;
import org.apache.james.jdkim.impl.DNSPublicKeyRecordRetriever;
import org.xbill.DNS.SimpleResolver;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

/**
 * Compare le résultat d'une évaluation DMARC (RFC 7489) à la clé configurée : {@code pass},
 * {@code fail}, {@code none}, {@code permerror}, ou {@code temperror}. Insensible à la casse.
 * <p>
 * S'appuie sur SPF et DKIM déjà implémentés ({@link SpfResultMatcher}, {@link DkimResultMatcher}) :
 * les deux sont recalculés en live (même politique de confiance que les autres matchers de ce
 * paquet — jamais un header préexistant n'est lu), puis {@link DmarcEvaluator} détermine si
 * l'un des deux est "aligné" avec le domaine affiché dans {@code From:}.
 */
public class DmarcResultMatcher extends Matcher {
  private final SpfEvaluator spfEvaluator;
  private final DkimVerifier dkimVerifier;
  private final DmarcEvaluator dmarcEvaluator;

  public DmarcResultMatcher() {
    this(new SpfEvaluator(new DnsJavaSpfDnsResolver()),
            new DkimVerifier(defaultPublicKeyRecordRetriever()),
            new DmarcEvaluator(new DnsJavaDmarcDnsResolver()));
  }

  /** Visible pour les tests : permet d'injecter des évaluateurs sans réseau. */
  DmarcResultMatcher(SpfEvaluator spfEvaluator, DkimVerifier dkimVerifier, DmarcEvaluator dmarcEvaluator) {
    this.spfEvaluator = spfEvaluator;
    this.dkimVerifier = dkimVerifier;
    this.dmarcEvaluator = dmarcEvaluator;
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
    String result = evaluateDmarc(message);
    Optional<String> hit = result != null ? matchingKey(result, String::equalsIgnoreCase) : Optional.empty();
    getLogger().fine(() -> "tested dmarc result=" + result + " against " + describeKey()
            + " -> " + (hit.isPresent() ? "match" : "no match"));
    return hit.map(this::matched).orElseGet(this::notMatched);
  }

  @Override
  public String extractKeyFromExample(Message message) throws MessagingException {
    String result = evaluateDmarc(message);
    if (result == null) {
      throw new MessagingException("Cannot learn a DMARC_RESULT_EQUALS rule: could not determine a From domain for this message");
    }
    return result;
  }

  private String evaluateDmarc(Message message) throws MessagingException {
    Optional<String> fromDomain = extractFromDomain(message);
    if (fromDomain.isEmpty()) {
      getLogger().fine(() -> "Cannot evaluate DMARC: no single usable From address");
      return null;
    }

    // DMARC a besoin du domaine que SPF a réellement vérifié (Return-Path, avec repli sur
    // From) — distinct du domaine From lui-même, utilisé plus bas pour l'alignment.
    Optional<String> spfIp = SpfIdentityExtractor.extractClientIp(message, getLogger());
    Optional<String> spfDomain = SpfIdentityExtractor.extractSenderDomain(message, getLogger());
    boolean spfPassed = spfIp.isPresent() && spfDomain.isPresent()
            && spfEvaluator.evaluate(spfIp.get(), spfDomain.get(), getLogger()) == SpfResult.PASS;

    byte[] raw;
    try {
      raw = MailTools.readRawMessageWithoutMarkingSeen(message);
    } catch (IOException e) {
      throw new MessagingException("Failed to read message for DMARC verification", e);
    }
    DkimVerification dkimVerification = dkimVerifier.verifyDetailed(new ByteArrayInputStream(raw), getLogger());

    DmarcResult result = dmarcEvaluator.evaluate(fromDomain.get(), spfPassed, spfDomain.orElse(null),
            dkimVerification.passingDomains(), getLogger());
    getLogger().fine(() -> "Evaluated DMARC for from=" + fromDomain.get() + " -> " + result.getCode());
    return result.getCode();
  }

  private static Optional<String> extractFromDomain(Message message) throws MessagingException {
    Address[] froms = message.getFrom();
    if (froms == null || froms.length != 1) return Optional.empty();
    String raw = froms[0] instanceof InternetAddress ? ((InternetAddress) froms[0]).getAddress() : froms[0].toString();
    if (raw == null) return Optional.empty();
    int at = raw.lastIndexOf('@');
    if (at < 0 || at >= raw.length() - 1) return Optional.empty();
    return Optional.of(raw.substring(at + 1));
  }
}
