package net.pieroxy.imf.learning;

import net.pieroxy.imf.mail.GreenMailImapFixture;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

/**
 * Fixture et helpers partagés par les tests de {@link RuleLearner} contre un vrai serveur IMAP
 * en mémoire (GreenMail). Une sous-classe par scénario plutôt qu'une seule classe à plusieurs
 * {@code @Test} : chaque {@code @Before}/{@code @After} démarre/arrête son propre serveur
 * GreenMail (le vrai coût, quelques secondes), donc les séparer les fait tourner en parallèle
 * avec le reste (voir {@code parallel=classes} dans pom.xml) plutôt que s'empiler dans une
 * seule classe séquentielle — même raisonnement que {@code AbstractMailAccountTest}.
 */
public abstract class AbstractRuleLearnerTest {
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

  protected LearnedRulesStore store() {
    return new LearnedRulesStore(tempFolder.getRoot().getAbsolutePath(), "test-account");
  }

  protected MimeMessage messageFrom(String address) throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress(address));
    message.setSubject("Test");
    message.setText("Hello");
    return message;
  }
}
