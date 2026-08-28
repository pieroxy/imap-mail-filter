package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.config.MailFilterRuleMatcherConfiguration;
import org.junit.Test;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FromExactMatcherTest {
  private final Session session = Session.getDefaultInstance(new Properties());

  private FromExactMatcher matcherFor(String key) {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setKey(key);
    FromExactMatcher matcher = new FromExactMatcher();
    matcher.setConfig(config);
    return matcher;
  }

  @Test
  public void matchesExactFromAddress() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("alice@example.com"));

    assertTrue(matcherFor("alice@example.com").matches(message));
  }

  @Test
  public void doesNotMatchDifferentAddress() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("alice@example.com"));

    assertFalse(matcherFor("bob@example.com").matches(message));
  }

  @Test
  public void doesNotMatchWhenNoFromHeader() throws Exception {
    MimeMessage message = new MimeMessage(session);

    assertFalse(matcherFor("alice@example.com").matches(message));
  }

  @Test
  public void doesNotMatchWhenMultipleFromAddresses() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.addFrom(new InternetAddress[]{
            new InternetAddress("alice@example.com"),
            new InternetAddress("bob@example.com")
    });

    assertFalse(matcherFor("alice@example.com").matches(message));
  }

  @Test
  public void extractKeyFromExampleReturnsFromAddress() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("alice@example.com"));

    assertEquals("alice@example.com", new FromExactMatcher().extractKeyFromExample(message));
  }

  @Test
  public void extractKeyFromExampleFailsWhenNoFromHeader() throws Exception {
    MimeMessage message = new MimeMessage(session);

    try {
      new FromExactMatcher().extractKeyFromExample(message);
      fail("should have thrown");
    } catch (MessagingException expected) {
      // ok
    }
  }

  @Test
  public void extractKeyFromExampleFailsWhenMultipleFromAddresses() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.addFrom(new InternetAddress[]{
            new InternetAddress("alice@example.com"),
            new InternetAddress("bob@example.com")
    });

    try {
      new FromExactMatcher().extractKeyFromExample(message);
      fail("should have thrown");
    } catch (MessagingException expected) {
      // ok
    }
  }
}
