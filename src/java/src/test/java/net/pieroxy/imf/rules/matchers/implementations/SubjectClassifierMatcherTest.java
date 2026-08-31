package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.classifier.ClassifierCorpusStore;
import net.pieroxy.imf.classifier.ClassifierExample;
import net.pieroxy.imf.classifier.ClassifierLabel;
import net.pieroxy.imf.classifier.SubjectClassifierTrainer;
import net.pieroxy.imf.config.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.rules.matchers.SubjectClassifierContext;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SubjectClassifierMatcherTest {
  private final Session session = Session.getDefaultInstance(new Properties());

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  @After
  public void clearContext() {
    // Le contexte est un ThreadLocal (voir SubjectClassifierContext) : le thread de test est
    // réutilisé d'un test à l'autre, donc il faut nettoyer pour ne pas fuiter entre les tests.
    SubjectClassifierContext.set(null);
  }

  private static SubjectClassifierMatcher matcherFor(String threshold) {
    MailFilterRuleMatcherConfiguration config = new MailFilterRuleMatcherConfiguration();
    config.setKey(threshold);
    SubjectClassifierMatcher matcher = new SubjectClassifierMatcher();
    matcher.setConfig(config);
    return matcher;
  }

  private MimeMessage messageWithSubject(String subject) throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setSubject(subject);
    return message;
  }

  @Test
  public void rejectsAMalformedThreshold() {
    try {
      matcherFor("not-a-threshold");
      fail("should have thrown");
    } catch (IllegalArgumentException expected) {
      // ok
    }
  }

  @Test
  public void rejectsAMissingThreshold() {
    try {
      matcherFor(null);
      fail("should have thrown");
    } catch (IllegalArgumentException expected) {
      // ok
    }
  }

  @Test
  public void doesNotMatchWhenMessageHasNoSubject() throws Exception {
    SubjectClassifierMatcher matcher = matcherFor(">0.5");
    // Pas de contexte positionné du tout : si le check du subject ne court-circuitait pas
    // avant, ça planterait plutôt que de renvoyer notMatched().
    MimeMessage message = new MimeMessage(session);

    assertFalse(matcher.matches(message).matched());
  }

  @Test
  public void announcesInactiveStateAssoonAsConfiguredNotAtFirstMessage() throws Exception {
    SubjectClassifierContext.set(tmp.newFile("does-not-exist.bin"));
    tmp.getRoot().listFiles((dir, name) -> name.equals("does-not-exist.bin"))[0].delete(); // le fichier ne doit pas exister

    // setConfig() logge sur SON logger propre (dérivé de la config), pas encore accessible
    // avant construction : on capture donc via la racine, qui reçoit tout par propagation
    // (comportement par défaut tant que rien n'appelle setUseParentHandlers(false)).
    List<LogRecord> records = new ArrayList<>();
    Handler capture = new Handler() {
      @Override public void publish(LogRecord record) { records.add(record); }
      @Override public void flush() {}
      @Override public void close() {}
    };
    Logger root = Logger.getLogger("");
    root.addHandler(capture);
    try {
      matcherFor(">0.5"); // le check + log doit arriver ici, pas au premier matches()
    } finally {
      root.removeHandler(capture);
    }

    assertTrue("l'état doit être annoncé dès la construction, pas au premier message reçu",
        records.stream().anyMatch(r -> r.getMessage() != null && r.getMessage().contains("inactive")));
  }

  @Test
  public void doesNotReannounceInactiveStateOnEveryMessageAfterTheInitialCheck() throws Exception {
    SubjectClassifierContext.set(tmp.newFile("does-not-exist.bin"));
    tmp.getRoot().listFiles((dir, name) -> name.equals("does-not-exist.bin"))[0].delete();
    SubjectClassifierMatcher matcher = matcherFor(">0.5"); // logge déjà "inactive" une fois ici, non capturé

    List<LogRecord> records = new ArrayList<>();
    Handler capture = new Handler() {
      @Override public void publish(LogRecord record) { records.add(record); }
      @Override public void flush() {}
      @Override public void close() {}
    };
    matcher.getLogger().addHandler(capture);
    matcher.getLogger().setUseParentHandlers(false);
    try {
      assertFalse(matcher.matches(messageWithSubject("hello")).matched());
      assertFalse(matcher.matches(messageWithSubject("hello again")).matched());
      assertFalse(matcher.matches(messageWithSubject("and again")).matched());
    } finally {
      matcher.getLogger().removeHandler(capture);
      matcher.getLogger().setUseParentHandlers(true);
    }

    long inactiveLogs = records.stream().filter(r -> r.getMessage().contains("inactive")).count();
    assertEquals("déjà annoncé pendant setConfig() : aucun message inspecté ne doit reloguer", 0, inactiveLogs);
  }

  @Test
  public void classifiesObviouslySpammyAndHammySubjectsOnceTrained() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tmp.getRoot().getAbsolutePath(), "account", 30);
    List<ClassifierExample> examples = new ArrayList<>();
    String[] spamSubjects = {
        "Buy cheap viagra now", "You have WON the lottery, claim your prize",
        "URGENT wire transfer needed today", "Free money click here now",
        "Congratulations you won a prize", "Claim your inheritance now urgent",
        "Hot singles in your area tonight", "Make money fast from home guaranteed",
        "Your account will be suspended click now", "Get rich quick guaranteed returns",
    };
    String[] hamSubjects = {
        "Meeting notes from yesterday", "Your invoice for March is ready",
        "Project status update for the team", "Lunch tomorrow at noon",
        "Weekly team sync agenda attached", "Quarterly report draft for review",
        "Reminder: dentist appointment Friday", "Photos from the weekend trip",
        "Updated schedule for next sprint", "Thanks for the feedback yesterday",
    };
    for (int i = 0; i < 6; i++) { // 60 exemples par classe : confortablement au-dessus du minimum (50)
      for (String s : spamSubjects) examples.add(example(s, ClassifierLabel.SPAM));
      for (String s : hamSubjects) examples.add(example(s, ClassifierLabel.HAM));
    }
    store.append(LocalDate.now(), examples);

    new SubjectClassifierTrainer(store).train();
    SubjectClassifierContext.set(store.getModelFile());

    SubjectClassifierMatcher confidentSpam = matcherFor(">0.5");
    assertTrue("un sujet clairement spam doit dépasser le seuil",
        confidentSpam.matches(messageWithSubject("Buy cheap viagra now, free money")).matched());
    assertFalse("un sujet clairement légitime ne doit pas dépasser le seuil",
        confidentSpam.matches(messageWithSubject("Meeting notes from yesterday's sync")).matched());

    SubjectClassifierMatcher confidentHam = matcherFor("<0.5");
    assertFalse("l'opérateur < s'applique aussi : un sujet spam ne doit pas passer sous le seuil",
        confidentHam.matches(messageWithSubject("Buy cheap viagra now, free money")).matched());
    assertTrue("un sujet légitime doit passer sous le seuil avec <",
        confidentHam.matches(messageWithSubject("Meeting notes from yesterday's sync")).matched());
  }

  private static ClassifierExample example(String subject, ClassifierLabel label) {
    ClassifierExample e = new ClassifierExample();
    e.setSubject(subject);
    e.setLabel(label);
    e.setFrom(Collections.emptyList());
    e.setTo(Collections.emptyList());
    return e;
  }
}
