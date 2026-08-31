package net.pieroxy.imf.rules.matchers.implementations;

import net.pieroxy.imf.rules.matchers.Matcher;

import javax.mail.Message;
import javax.mail.MessagingException;

/**
 * Matche si le {@code Subject:} du message commence par la clé configurée, de façon
 * insensible à la casse.
 * <p>
 * Learnable, mais naïvement : {@link #extractKeyFromExample} apprend le sujet **complet** de
 * l'exemple déposé, pas un préfixe déduit intelligemment (il n'y a aucun moyen de savoir quelle
 * partie du sujet est le préfixe voulu vs. le contenu spécifique à cet exemple précis, ex:
 * "Votre facture n°12345" — faut-il apprendre "Votre facture" ou tout le texte ?). Pour l'instant,
 * il faut éditer à la main le fichier {@code <dataFolder>/<displayName>-learned-rules.json}
 * après coup pour raccourcir la clé apprise au préfixe réellement voulu.
 */
public class SubjectStartsWithMatcher extends Matcher {
  @Override
  public boolean matches(Message message) throws MessagingException {
    String subject = message.getSubject();
    if (subject == null) {
      getLogger().fine(() -> "no Subject header on message, no match against " + describeKey());
      return false;
    }
    boolean matched = matchesKey(subject, SubjectStartsWithMatcher::startsWithIgnoreCase);
    getLogger().fine(() -> "tested subject=" + subject + " against " + describeKey()
            + " -> " + (matched ? "match" : "no match"));
    return matched;
  }

  @Override
  public String extractKeyFromExample(Message message) throws MessagingException {
    String subject = message.getSubject();
    if (subject == null) {
      throw new MessagingException("Cannot learn a SUBJECT_STARTS_WITH rule: message has no Subject header");
    }
    return subject;
  }

  private static boolean startsWithIgnoreCase(String subject, String prefix) {
    return prefix != null && subject.length() >= prefix.length()
            && subject.regionMatches(true, 0, prefix, 0, prefix.length());
  }
}
