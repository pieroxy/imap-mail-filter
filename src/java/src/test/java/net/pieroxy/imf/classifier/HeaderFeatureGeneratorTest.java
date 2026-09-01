package net.pieroxy.imf.classifier;

import org.junit.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HeaderFeatureGeneratorTest {
  private final HeaderFeatureGenerator generator = new HeaderFeatureGenerator();
  private static final String[] NO_TOKENS = new String[0];

  private Collection<String> featuresFor(ClassifierExample example) {
    return generator.extractFeatures(NO_TOKENS, Map.of(HeaderFeatureGenerator.EXAMPLE_KEY, example));
  }

  private static ClassifierExample blankExample() {
    ClassifierExample e = new ClassifierExample();
    e.setFrom(List.of());
    e.setTo(List.of());
    return e;
  }

  @Test
  public void returnsNoFeaturesWhenExtraInformationHasNoExample() {
    assertTrue(generator.extractFeatures(NO_TOKENS, Map.of()).isEmpty());
    assertTrue(generator.extractFeatures(NO_TOKENS, null).isEmpty());
  }

  @Test
  public void emitsFromDomainWhenExactlyOneFromAddress() {
    ClassifierExample e = blankExample();
    e.setFrom(List.of("alice@Example.com"));

    assertTrue(featuresFor(e).contains("fromDomain=example.com"));
  }

  @Test
  public void fromDomainIsAbsentWhenZeroOrMultipleFromAddresses() {
    ClassifierExample e = blankExample();
    assertTrue(featuresFor(e).contains("fromDomain=(absent)"));

    e.setFrom(List.of("a@x.com", "b@y.com"));
    assertTrue(featuresFor(e).contains("fromDomain=(absent)"));
  }

  @Test
  public void emitsOneToDomainFeaturePerDistinctRecipientDomain() {
    ClassifierExample e = blankExample();
    e.setTo(List.of("bob@example.com", "carol@example.com", "dave@other.example"));

    Set<String> features = Set.copyOf(featuresFor(e));
    assertTrue(features.contains("toDomain=example.com"));
    assertTrue(features.contains("toDomain=other.example"));
    long toDomainCount = features.stream().filter(f -> f.startsWith("toDomain=")).count();
    assertEquals("bob and carol share example.com and dedupe into one feature; dave's domain is distinct",
        2, toDomainCount);
  }

  @Test
  public void toDomainIsAbsentWhenNoRecipients() {
    assertTrue(featuresFor(blankExample()).contains("toDomain=(absent)"));
  }

  @Test
  public void emitsOneWordFeaturePerDisplayNameWordLowercased() {
    ClassifierExample e = blankExample();
    e.setFromDisplayName("Alice Smith");
    e.setToDisplayName("Bob");

    Collection<String> features = featuresFor(e);
    assertTrue(features.contains("fromNameWord=alice"));
    assertTrue(features.contains("fromNameWord=smith"));
    assertTrue(features.contains("toNameWord=bob"));
  }

  @Test
  public void emitsNoNameWordFeaturesWhenDisplayNamesAreAbsent() {
    Collection<String> features = featuresFor(blankExample());
    assertFalse(features.stream().anyMatch(f -> f.startsWith("fromNameWord=") || f.startsWith("toNameWord=")));
  }

  @Test
  public void emitsIpPrefixAsFirstThreeOctets() {
    ClassifierExample e = blankExample();
    e.setIp("203.0.113.42");

    assertTrue(featuresFor(e).contains("ipPrefix=203.0.113"));
  }

  @Test
  public void ipPrefixIsAbsentWhenIpIsMissing() {
    assertTrue(featuresFor(blankExample()).contains("ipPrefix=(absent)"));
  }

  @Test
  public void emitsBooleanFeaturesExplicitlyEvenWhenFalse() {
    ClassifierExample e = blankExample(); // reply and listUnsubscribePresent default to false
    Collection<String> features = featuresFor(e);
    assertTrue("false must still be an explicit feature, not just absent", features.contains("reply=false"));
    assertTrue(features.contains("listUnsubscribe=false"));

    e.setReply(true);
    e.setListUnsubscribePresent(true);
    Collection<String> featuresTrue = featuresFor(e);
    assertTrue(featuresTrue.contains("reply=true"));
    assertTrue(featuresTrue.contains("listUnsubscribe=true"));
  }

  @Test
  public void emitsPrecedenceAndListIdRawValuesOrAbsentSentinel() {
    assertTrue(featuresFor(blankExample()).contains("precedence=(absent)"));
    assertTrue(featuresFor(blankExample()).contains("listId=(absent)"));

    ClassifierExample e = blankExample();
    e.setPrecedence("bulk");
    e.setListId("newsletter.example.com");
    Collection<String> features = featuresFor(e);
    assertTrue(features.contains("precedence=bulk"));
    assertTrue(features.contains("listId=newsletter.example.com"));
  }

  @Test
  public void emitsReturnPathAndReplyToDomainsOrAbsentSentinel() {
    ClassifierExample e = blankExample();
    e.setReturnPathDomain("bounce.example.com");
    e.setReplyToDomain("support.example.com");

    Collection<String> features = featuresFor(e);
    assertTrue(features.contains("returnPathDomain=bounce.example.com"));
    assertTrue(features.contains("replyToDomain=support.example.com"));
    assertTrue(featuresFor(blankExample()).contains("returnPathDomain=(absent)"));
    assertTrue(featuresFor(blankExample()).contains("replyToDomain=(absent)"));
  }

  @Test
  public void mismatchFeaturesAreTrueFalseOrUnknown() {
    ClassifierExample unknown = blankExample();
    assertTrue(featuresFor(unknown).contains("returnPathMismatch=unknown"));
    assertTrue(featuresFor(unknown).contains("replyToMismatch=unknown"));

    ClassifierExample mismatched = blankExample();
    mismatched.setReturnPathMismatch(true);
    mismatched.setReplyToMismatch(false);
    Collection<String> features = featuresFor(mismatched);
    assertTrue(features.contains("returnPathMismatch=true"));
    assertTrue(features.contains("replyToMismatch=false"));
  }
}
