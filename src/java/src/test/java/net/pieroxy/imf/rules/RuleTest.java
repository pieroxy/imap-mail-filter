package net.pieroxy.imf.rules;

import net.pieroxy.imf.config.MailFilterRuleActionConfiguration;
import net.pieroxy.imf.config.MailFilterRuleConfiguration;
import net.pieroxy.imf.config.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.rules.actions.ActionType;
import net.pieroxy.imf.rules.matchers.MatcherType;
import org.junit.Test;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Arrays;
import java.util.Properties;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Teste Rule de bout en bout : construction de l'arbre matcher/action via les factories
 * (Matcher.build / Action.build) à partir d'une config, puis évaluation sur un message.
 */
public class RuleTest {
  private final Session session = Session.getDefaultInstance(new Properties());

  private MimeMessage messageFrom(String address) throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress(address));
    return message;
  }

  private static MailFilterRuleMatcherConfiguration fromEquals(String email) {
    MailFilterRuleMatcherConfiguration c = new MailFilterRuleMatcherConfiguration();
    c.setType(MatcherType.FROM_EQUALS);
    c.setKey(email);
    return c;
  }

  /**
   * Action AND sans enfant : réussit vacuously sans toucher au message. Utilisée ici pour
   * tester le déclenchement par le matcher sans dépendre de la mécanique IMAP réelle de
   * MoveToAction (qui a besoin d'un Folder/Store réels, absents d'un MimeMessage de test).
   */
  private static MailFilterRuleActionConfiguration noopAction() {
    MailFilterRuleActionConfiguration c = new MailFilterRuleActionConfiguration();
    c.setType(ActionType.AND);
    return c;
  }

  @Test
  public void appliesWhenMatcherMatches() throws Exception {
    MailFilterRuleConfiguration config = new MailFilterRuleConfiguration();
    config.setMatcher(fromEquals("alice@example.com"));
    config.setAction(noopAction());

    Rule rule = new Rule(config);

    assertTrue(rule.apply(messageFrom("alice@example.com")));
  }

  @Test
  public void doesNotApplyWhenMatcherDoesNotMatch() throws Exception {
    MailFilterRuleConfiguration config = new MailFilterRuleConfiguration();
    config.setMatcher(fromEquals("alice@example.com"));
    config.setAction(noopAction());

    Rule rule = new Rule(config);

    assertFalse(rule.apply(messageFrom("carol@example.com")));
  }

  @Test
  public void appliesWithCompositeAndMatcher() throws Exception {
    MailFilterRuleMatcherConfiguration and = new MailFilterRuleMatcherConfiguration();
    and.setType(MatcherType.AND);
    and.setChildren(Arrays.asList(fromEquals("alice@example.com"), fromEquals("alice@example.com")));

    MailFilterRuleConfiguration config = new MailFilterRuleConfiguration();
    config.setMatcher(and);
    config.setAction(noopAction());

    Rule rule = new Rule(config);

    assertTrue(rule.apply(messageFrom("alice@example.com")));
    assertFalse(rule.apply(messageFrom("bob@example.com")));
  }
}
