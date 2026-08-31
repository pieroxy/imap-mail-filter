package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.config.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.rules.matchers.Matcher;
import net.pieroxy.imf.rules.matchers.MatcherType;
import org.junit.Test;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Arrays;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Vérifie la construction récursive (Matcher.build) et l'évaluation des matchers
 * composites AND/OR, à partir de vrais matchers FROM_EQUALS.
 */
public class CompositeMatcherTest {
  private final Session session = Session.getDefaultInstance(new Properties());

  private MimeMessage messageFrom(String address) throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress(address));
    return message;
  }

  private static MailFilterRuleMatcherConfiguration leaf(String email) {
    MailFilterRuleMatcherConfiguration c = new MailFilterRuleMatcherConfiguration();
    c.setType(MatcherType.FROM_EQUALS);
    c.setKey(email);
    return c;
  }

  private static MailFilterRuleMatcherConfiguration composite(MatcherType type, MailFilterRuleMatcherConfiguration... children) {
    MailFilterRuleMatcherConfiguration c = new MailFilterRuleMatcherConfiguration();
    c.setType(type);
    c.setChildren(Arrays.asList(children));
    return c;
  }

  @Test
  public void andMatchesOnlyWhenAllChildrenMatch() throws Exception {
    Matcher and = Matcher.build(composite(MatcherType.AND, leaf("alice@example.com"), leaf("alice@example.com")));
    assertTrue(and.matches(messageFrom("alice@example.com")).matched());

    Matcher andMismatch = Matcher.build(composite(MatcherType.AND, leaf("alice@example.com"), leaf("bob@example.com")));
    assertFalse(andMismatch.matches(messageFrom("alice@example.com")).matched());
  }

  @Test
  public void orMatchesWhenAnyChildMatches() throws Exception {
    Matcher or = Matcher.build(composite(MatcherType.OR, leaf("bob@example.com"), leaf("alice@example.com")));
    assertTrue(or.matches(messageFrom("alice@example.com")).matched());

    Matcher orMismatch = Matcher.build(composite(MatcherType.OR, leaf("bob@example.com"), leaf("carol@example.com")));
    assertFalse(orMismatch.matches(messageFrom("alice@example.com")).matched());
  }

  @Test
  public void nestedAndOrCompose() throws Exception {
    // (bob OR alice) AND alice
    Matcher rule = Matcher.build(composite(MatcherType.AND,
            composite(MatcherType.OR, leaf("bob@example.com"), leaf("alice@example.com")),
            leaf("alice@example.com")));

    assertTrue(rule.matches(messageFrom("alice@example.com")).matched());
    assertFalse(rule.matches(messageFrom("carol@example.com")).matched());
  }

  @Test
  public void emptyAndIsVacuouslyTrueEmptyOrIsFalse() throws Exception {
    assertTrue(new AndMatcher().matches(messageFrom("alice@example.com")).matched());
    assertFalse(new OrMatcher().matches(messageFrom("alice@example.com")).matched());
  }

  @Test
  public void andDebugStringListsEveryChildThatMatched() throws Exception {
    Matcher and = Matcher.build(composite(MatcherType.AND, leaf("alice@example.com"), leaf("alice@example.com")));

    assertEquals("AndMatcher(FromExactMatcher(alice@example.com), FromExactMatcher(alice@example.com))",
            and.matches(messageFrom("alice@example.com")).debugString());
  }

  @Test
  public void orDebugStringNamesOnlyTheChildThatMatched() throws Exception {
    // Le premier enfant (bob) ne matche pas et n'apparaît donc pas dans la debug string :
    // seul celui qui a effectivement fait matcher le OR y figure.
    Matcher or = Matcher.build(composite(MatcherType.OR, leaf("bob@example.com"), leaf("alice@example.com")));

    assertEquals("OrMatcher(FromExactMatcher(alice@example.com))",
            or.matches(messageFrom("alice@example.com")).debugString());
  }
}
