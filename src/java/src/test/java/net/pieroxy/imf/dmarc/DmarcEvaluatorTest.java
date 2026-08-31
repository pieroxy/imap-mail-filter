package net.pieroxy.imf.dmarc;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class DmarcEvaluatorTest {

  @Test
  public void passesViaAlignedSpf() {
    FakeDmarcDnsResolver resolver = new FakeDmarcDnsResolver()
            .withTxt("_dmarc.example.com", "v=DMARC1; p=reject");
    DmarcEvaluator evaluator = new DmarcEvaluator(resolver);

    assertEquals(DmarcResult.PASS, evaluator.evaluate("example.com", true, "example.com", List.of()));
  }

  @Test
  public void passesViaAlignedDkim() {
    FakeDmarcDnsResolver resolver = new FakeDmarcDnsResolver()
            .withTxt("_dmarc.example.com", "v=DMARC1; p=reject");
    DmarcEvaluator evaluator = new DmarcEvaluator(resolver);

    assertEquals(DmarcResult.PASS, evaluator.evaluate("example.com", false, null, List.of("example.com")));
  }

  @Test
  public void failsWhenNeitherAligned() {
    FakeDmarcDnsResolver resolver = new FakeDmarcDnsResolver()
            .withTxt("_dmarc.example.com", "v=DMARC1; p=reject");
    DmarcEvaluator evaluator = new DmarcEvaluator(resolver);

    assertEquals(DmarcResult.FAIL, evaluator.evaluate("example.com", true, "other.com", List.of("another.com")));
  }

  @Test
  public void failsWhenSpfPassedButDomainNotAligned() {
    FakeDmarcDnsResolver resolver = new FakeDmarcDnsResolver()
            .withTxt("_dmarc.example.com", "v=DMARC1; p=reject");
    DmarcEvaluator evaluator = new DmarcEvaluator(resolver);

    // SPF a réussi, mais pour un domaine sans rapport avec le From affiché : pas aligné.
    assertEquals(DmarcResult.FAIL, evaluator.evaluate("example.com", true, "totally-unrelated.net", List.of()));
  }

  @Test
  public void relaxedAlignmentMatchesOrganizationalDomain() {
    FakeDmarcDnsResolver resolver = new FakeDmarcDnsResolver()
            .withTxt("_dmarc.news.example.com", "v=DMARC1; p=reject"); // aspf=r par défaut
    DmarcEvaluator evaluator = new DmarcEvaluator(resolver);

    // spfDomain a un sous-domaine différent, mais le même domaine organisationnel.
    assertEquals(DmarcResult.PASS, evaluator.evaluate("news.example.com", true, "example.com", List.of()));
  }

  @Test
  public void strictAlignmentRequiresExactDomain() {
    FakeDmarcDnsResolver resolver = new FakeDmarcDnsResolver()
            .withTxt("_dmarc.news.example.com", "v=DMARC1; p=reject; aspf=s");
    DmarcEvaluator evaluator = new DmarcEvaluator(resolver);

    assertEquals(DmarcResult.FAIL, evaluator.evaluate("news.example.com", true, "example.com", List.of()));
  }

  @Test
  public void noRecordAnywhereYieldsNone() {
    DmarcEvaluator evaluator = new DmarcEvaluator(new FakeDmarcDnsResolver());

    assertEquals(DmarcResult.NONE, evaluator.evaluate("example.com", true, "example.com", List.of()));
  }

  @Test
  public void fallsBackToOrganizationalDomainRecord() {
    FakeDmarcDnsResolver resolver = new FakeDmarcDnsResolver()
            .withTxt("_dmarc.example.com", "v=DMARC1; p=reject"); // rien à _dmarc.sub.example.com
    DmarcEvaluator evaluator = new DmarcEvaluator(resolver);

    assertEquals(DmarcResult.PASS, evaluator.evaluate("sub.example.com", true, "example.com", List.of()));
  }

  @Test
  public void missingPolicyTagIsPermError() {
    FakeDmarcDnsResolver resolver = new FakeDmarcDnsResolver()
            .withTxt("_dmarc.example.com", "v=DMARC1; adkim=s");
    DmarcEvaluator evaluator = new DmarcEvaluator(resolver);

    assertEquals(DmarcResult.PERMERROR, evaluator.evaluate("example.com", true, "example.com", List.of()));
  }

  @Test
  public void multipleRecordsIsPermError() {
    FakeDmarcDnsResolver resolver = new FakeDmarcDnsResolver()
            .withTxt("_dmarc.example.com", "v=DMARC1; p=none", "v=DMARC1; p=reject");
    DmarcEvaluator evaluator = new DmarcEvaluator(resolver);

    assertEquals(DmarcResult.PERMERROR, evaluator.evaluate("example.com", true, "example.com", List.of()));
  }

  @Test
  public void dnsFailureIsTempError() {
    FakeDmarcDnsResolver resolver = new FakeDmarcDnsResolver().withFailure("_dmarc.example.com");
    DmarcEvaluator evaluator = new DmarcEvaluator(resolver);

    assertEquals(DmarcResult.TEMPERROR, evaluator.evaluate("example.com", true, "example.com", List.of()));
  }

  @Test
  public void blankFromDomainYieldsNone() {
    DmarcEvaluator evaluator = new DmarcEvaluator(new FakeDmarcDnsResolver());

    assertEquals(DmarcResult.NONE, evaluator.evaluate("", true, "example.com", List.of()));
  }
}
