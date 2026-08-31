package net.pieroxy.imf.rules;

import net.pieroxy.imf.config.MailAccountConfiguration;
import net.pieroxy.imf.config.MailFilterRuleActionConfiguration;
import net.pieroxy.imf.config.MailFilterRuleConfiguration;
import net.pieroxy.imf.config.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.mail.GreenMailImapFixture;
import net.pieroxy.imf.rules.actions.ActionType;
import net.pieroxy.imf.rules.matchers.MatcherType;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.List;
import java.util.Properties;

/**
 * Fixture et helpers partagés par les tests de {@link MailAccount#processMessages()} contre un
 * vrai serveur IMAP en mémoire (GreenMail). Une sous-classe par scénario (pas une seule classe
 * avec 4 {@code @Test}) : chaque {@code @Before}/{@code @After} démarre/arrête son propre
 * serveur GreenMail, ce qui domine largement le temps d'exécution — les regrouper dans une
 * seule classe les sérialisait, alors qu'en classes séparées {@code parallel=classes} (voir
 * pom.xml) les fait tourner en même temps.
 * <p>
 * Nommée {@code Abstract*} pour rester hors de portée des patterns de découverte de Surefire
 * (elle n'a d'ailleurs aucun {@code @Test} propre à exécuter).
 */
public abstract class AbstractMailAccountTest {
  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  protected final GreenMailImapFixture fixture = new GreenMailImapFixture();
  private final Session session = Session.getDefaultInstance(new Properties());

  @Before
  public void startServer() {
    fixture.start();
  }

  @After
  public void stopServer() {
    fixture.stop();
  }

  protected static MailFilterRuleConfiguration moveToSpamOnDomain(String domain) {
    MailFilterRuleMatcherConfiguration matcher = new MailFilterRuleMatcherConfiguration();
    matcher.setType(MatcherType.FROM_DOMAIN_EQUALS);
    matcher.setKey(domain);
    MailFilterRuleActionConfiguration action = new MailFilterRuleActionConfiguration();
    action.setType(ActionType.MOVE_TO);
    action.setKey("Spam");
    MailFilterRuleConfiguration rule = new MailFilterRuleConfiguration();
    rule.setMatcher(matcher);
    rule.setAction(action);
    return rule;
  }

  protected MimeMessage messageFrom(String address) throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress(address));
    message.setSubject("Test");
    message.setText("Hello");
    return message;
  }

  protected MailAccount accountWith(MailFilterRuleConfiguration... rules) {
    MailAccountConfiguration config = fixture.accountConfig("test-account");
    config.setRules(List.of(rules));
    return new MailAccount(config, tempFolder.getRoot().getAbsolutePath(), 0, c -> fixture.connectAsImapMailbox());
  }
}
