package net.pieroxy.imf.dmarc;

import net.pieroxy.imf.dkim.DkimVerification;
import net.pieroxy.imf.dkim.DkimVerifier;
import net.pieroxy.imf.spf.SpfEvaluator;
import net.pieroxy.imf.spf.SpfIdentityExtractor;
import net.pieroxy.imf.spf.SpfResult;
import net.pieroxy.imf.utils.MailTools;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Calcule l'évaluation DMARC complète d'un message (domaine From, SPF et DKIM sous-jacents,
 * puis {@link DmarcEvaluator}) — partagé par {@code DmarcResultMatcher} et
 * {@code DmarcPolicyMatcher}, qui n'ont chacun besoin que d'une moitié du résultat (le
 * pass/fail pour l'un, la politique publiée pour l'autre) mais du même calcul sous-jacent.
 */
public class DmarcMessageEvaluator {
  private final SpfEvaluator spfEvaluator;
  private final DkimVerifier dkimVerifier;
  private final DmarcEvaluator dmarcEvaluator;

  public DmarcMessageEvaluator(SpfEvaluator spfEvaluator, DkimVerifier dkimVerifier, DmarcEvaluator dmarcEvaluator) {
    this.spfEvaluator = spfEvaluator;
    this.dkimVerifier = dkimVerifier;
    this.dmarcEvaluator = dmarcEvaluator;
  }

  /** @return vide si le domaine From du message ne peut pas être déterminé (message sans From unique exploitable). */
  public Optional<DmarcEvaluation> evaluate(Message message, Logger logger) throws MessagingException {
    Optional<String> fromDomain = extractFromDomain(message);
    if (fromDomain.isEmpty()) {
      logger.fine(() -> "Cannot evaluate DMARC: no single usable From address");
      return Optional.empty();
    }

    // DMARC a besoin du domaine que SPF a réellement vérifié (Return-Path, avec repli sur
    // From) — distinct du domaine From lui-même, utilisé pour l'alignment.
    Optional<String> spfIp = SpfIdentityExtractor.extractClientIp(message, logger);
    Optional<String> spfDomain = SpfIdentityExtractor.extractSenderDomain(message, logger);
    boolean spfPassed = spfIp.isPresent() && spfDomain.isPresent()
            && spfEvaluator.evaluate(spfIp.get(), spfDomain.get(), logger) == SpfResult.PASS;

    byte[] raw;
    try {
      raw = MailTools.readRawMessageWithoutMarkingSeen(message);
    } catch (IOException e) {
      throw new MessagingException("Failed to read message for DMARC verification", e);
    }
    DkimVerification dkimVerification = dkimVerifier.verifyDetailed(new ByteArrayInputStream(raw), logger);

    DmarcEvaluation evaluation = dmarcEvaluator.evaluateDetailed(fromDomain.get(), spfPassed, spfDomain.orElse(null),
            dkimVerification.passingDomains(), logger);
    logger.fine(() -> "Evaluated DMARC for from=" + fromDomain.get() + " -> result=" + evaluation.result().getCode()
            + " policy=" + evaluation.policy().getCode());
    return Optional.of(evaluation);
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
