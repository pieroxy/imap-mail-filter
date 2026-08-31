package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.config.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.spf.FakeSpfDnsResolver;
import net.pieroxy.imf.spf.SpfEvaluator;
import org.junit.Test;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SpfResultMatcherTest {
  private final Session session = Session.getDefaultInstance(new Properties());

  private SpfResultMatcher matcherFor(String key) {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setKey(key);
    SpfResultMatcher matcher = new SpfResultMatcher();
    matcher.setConfig(config);
    return matcher;
  }

  @Test
  public void matchesSpfResultFromAuthenticationResults() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.addHeader("Authentication-Results",
            "mx.google.com; dkim=pass header.i=@example.com; spf=pass smtp.mailfrom=foo@example.com; dmarc=pass");

    assertTrue(matcherFor("pass").matches(message));
    assertFalse(matcherFor("fail").matches(message));
  }

  @Test
  public void matchesCaseInsensitively() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.addHeader("Authentication-Results", "mx.google.com; spf=PASS smtp.mailfrom=foo@example.com");

    assertTrue(matcherFor("pass").matches(message));
  }

  @Test
  public void matchesFailResult() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.addHeader("Authentication-Results", "mx.google.com; spf=fail smtp.mailfrom=foo@example.com");

    assertTrue(matcherFor("fail").matches(message));
    assertFalse(matcherFor("pass").matches(message));
  }

  @Test
  public void fallsBackToReceivedSpfWhenNoAuthenticationResultsHeader() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.addHeader("Received-SPF", "softfail (domain of example.com does not designate 1.2.3.4 as permitted sender) client-ip=1.2.3.4;");

    assertTrue(matcherFor("softfail").matches(message));
  }

  @Test
  public void fallsBackToReceivedSpfWhenAuthenticationResultsHasNoSpfField() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.addHeader("Authentication-Results", "mx.google.com; dkim=pass header.i=@example.com");
    message.addHeader("Received-SPF", "pass (google.com: domain of foo@example.com designates 1.2.3.4 as permitted sender)");

    assertTrue(matcherFor("pass").matches(message));
  }

  @Test
  public void doesNotMatchWhenNoRelevantHeaders() throws Exception {
    MimeMessage message = new MimeMessage(session);

    assertFalse(matcherFor("pass").matches(message));
  }

  @Test
  public void matchesAnyKeyWhenKeysIsUsedInsteadOfKey() throws Exception {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setKeys(Set.of("fail", "softfail"));
    SpfResultMatcher matcher = new SpfResultMatcher();
    matcher.setConfig(config);

    MimeMessage message = new MimeMessage(session);
    message.addHeader("Authentication-Results", "mx.google.com; spf=SoftFail smtp.mailfrom=foo@example.com");

    assertTrue(matcher.matches(message));
  }

  @Test
  public void extractKeyFromExampleReturnsSpfResult() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.addHeader("Authentication-Results", "mx.google.com; spf=pass smtp.mailfrom=foo@example.com");

    assertEquals("pass", new SpfResultMatcher().extractKeyFromExample(message));
  }

  @Test
  public void extractKeyFromExampleFailsWhenNoRelevantHeaders() throws Exception {
    MimeMessage message = new MimeMessage(session);

    try {
      new SpfResultMatcher().extractKeyFromExample(message);
      fail("should have thrown");
    } catch (MessagingException expected) {
      // ok
    }
  }

  @Test
  public void fallsBackToLiveDnsEvaluationWhenNoAuthenticationHeadersArePresent() throws Exception {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver()
            .withTxt("example.com", "v=spf1 ip4:203.0.113.0/24 -all");
    SpfResultMatcher matcher = matcherWithEvaluator("pass", new SpfEvaluator(resolver));

    MimeMessage message = new MimeMessage(session);
    message.addHeader("Received", "from mail.example.com (mail.example.com [203.0.113.10])\n"
            + "\tby mx.myprovider.com with SMTPS id abc; Mon, 31 Aug 2026 10:00:00 +0000");
    message.setFrom(new InternetAddress("sender@example.com"));

    assertTrue(matcher.matches(message));
  }

  @Test
  public void liveDnsEvaluationCanFail() throws Exception {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver()
            .withTxt("example.com", "v=spf1 ip4:203.0.113.0/24 -all");
    SpfResultMatcher matcher = matcherWithEvaluator("fail", new SpfEvaluator(resolver));

    MimeMessage message = new MimeMessage(session);
    message.addHeader("Received", "from spoofed.example.org (spoofed.example.org [198.51.100.1])\n"
            + "\tby mx.myprovider.com with SMTPS id abc; Mon, 31 Aug 2026 10:00:00 +0000");
    message.setFrom(new InternetAddress("sender@example.com"));

    assertTrue(matcher.matches(message));
  }

  @Test
  public void authenticationResultsHeaderTakesPrecedenceOverLiveEvaluation() throws Exception {
    // Le resolver dirait "fail", mais le header (posé par un relais en amont) dit "pass" :
    // c'est ce dernier qui doit l'emporter, il est gratuit (pas de DNS) et potentiellement
    // plus à jour que ce qu'on recalculerait.
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver()
            .withTxt("example.com", "v=spf1 -all");
    SpfResultMatcher matcher = matcherWithEvaluator("pass", new SpfEvaluator(resolver));

    MimeMessage message = new MimeMessage(session);
    message.addHeader("Authentication-Results", "mx.google.com; spf=pass smtp.mailfrom=sender@example.com");
    message.addHeader("Received", "from mail.example.com (mail.example.com [203.0.113.10])\n"
            + "\tby mx.myprovider.com with SMTPS id abc; Mon, 31 Aug 2026 10:00:00 +0000");
    message.setFrom(new InternetAddress("sender@example.com"));

    assertTrue(matcher.matches(message));
  }

  @Test
  public void liveEvaluationSkippedWhenClientIpCannotBeDetermined() throws Exception {
    FakeSpfDnsResolver resolver = new FakeSpfDnsResolver()
            .withTxt("example.com", "v=spf1 ip4:203.0.113.0/24 -all");
    SpfResultMatcher matcher = matcherWithEvaluator("pass", new SpfEvaluator(resolver));

    MimeMessage message = new MimeMessage(session); // pas de header Received
    message.setFrom(new InternetAddress("sender@example.com"));

    assertFalse(matcher.matches(message));
  }

  private SpfResultMatcher matcherWithEvaluator(String key, SpfEvaluator evaluator) {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setKey(key);
    SpfResultMatcher matcher = new SpfResultMatcher(evaluator);
    matcher.setConfig(config);
    return matcher;
  }
}
