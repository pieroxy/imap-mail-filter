package net.pieroxy.imf.classifier;

import org.junit.Test;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ClassifierExampleExtractorTest {
  private final Session session = Session.getDefaultInstance(new Properties());

  @Test
  public void extractsSubjectFromToDateAndLabel() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("alice@example.com", "Alice"));
    message.setRecipients(Message.RecipientType.TO, new InternetAddress[]{
        new InternetAddress("bob@example.com"), new InternetAddress("carol@example.com")
    });
    message.setSubject("Buy now!!!");
    Date sentDate = new Date(1_700_000_000_000L);
    message.setSentDate(sentDate);

    Instant fetchDate = Instant.ofEpochMilli(1_800_000_000_000L);
    ClassifierExample example = ClassifierExampleExtractor.extract(message, ClassifierLabel.SPAM, fetchDate);

    assertEquals("Buy now!!!", example.getSubject());
    assertEquals(ClassifierLabel.SPAM, example.getLabel());
    assertEquals(1, example.getFrom().size());
    assertEquals("alice@example.com", example.getFrom().get(0));
    assertEquals(2, example.getTo().size());
    assertTrue(example.getTo().contains("bob@example.com"));
    assertTrue(example.getTo().contains("carol@example.com"));
    assertEquals(sentDate.toInstant().toString(), example.getMailDate());
    assertEquals(fetchDate.toString(), example.getFetchDate());
    assertEquals("Alice", example.getFromDisplayName());
    assertNull(example.getToDisplayName()); // bob/carol n'ont pas de display name
  }

  @Test
  public void joinsMultipleDisplayNamesWithASpace() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("alice@example.com", "Alice"));
    message.setRecipients(Message.RecipientType.TO, new InternetAddress[]{
        new InternetAddress("bob@example.com", "Bob"), new InternetAddress("carol@example.com", "Carol")
    });

    ClassifierExample example = ClassifierExampleExtractor.extract(message, ClassifierLabel.HAM, Instant.now());

    assertEquals("Alice", example.getFromDisplayName());
    assertEquals("Bob Carol", example.getToDisplayName());
  }

  @Test
  public void blankDisplayNameIsTreatedAsAbsent() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("alice@example.com", ""));

    ClassifierExample example = ClassifierExampleExtractor.extract(message, ClassifierLabel.HAM, Instant.now());

    assertNull(example.getFromDisplayName());
  }

  @Test
  public void toleratesMissingFromToSubjectAndDate() throws Exception {
    MimeMessage message = new MimeMessage(session);

    ClassifierExample example = ClassifierExampleExtractor.extract(message, ClassifierLabel.HAM, Instant.now());

    assertTrue(example.getFrom().isEmpty());
    assertTrue(example.getTo().isEmpty());
    assertNull(example.getSubject());
    assertNull(example.getMailDate());
    assertNull(example.getIp());
    assertNull(example.getFromDisplayName());
    assertNull(example.getToDisplayName());
  }

  @Test
  public void extractsIpFromTheLastReceivedHeaderClosestToTheOrigin() throws Exception {
    // Construit depuis un flux brut (comme un vrai fetch IMAP) plutôt qu'avec addHeader(),
    // qui préfixe au lieu de préserver l'ordre du message : le relais le plus récent (le
    // nôtre) apparaît en premier dans un message réel, celui de l'expéditeur d'origine en
    // dernier.
    String raw = "Received: from relay.example.com (relay.example.com [198.51.100.9]) by mx\r\n"
        + "Received: from origin.example.net (origin.example.net [203.0.113.5]) by relay\r\n"
        + "Subject: test\r\n\r\nbody\r\n";
    MimeMessage message = new MimeMessage(session, new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));

    ClassifierExample example = ClassifierExampleExtractor.extract(message, ClassifierLabel.SPAM, Instant.now());

    assertEquals("203.0.113.5", example.getIp());
  }

  @Test
  public void returnsNullIpWhenNoReceivedHeader() throws Exception {
    MimeMessage message = new MimeMessage(session);

    ClassifierExample example = ClassifierExampleExtractor.extract(message, ClassifierLabel.HAM, Instant.now());

    assertNull(example.getIp());
  }
}
