package net.pieroxy.imf.mail;

import net.pieroxy.imf.config.MailAccountConfiguration;

import javax.mail.MessagingException;

/**
 * Fabrique une {@link ImapMailbox} à partir de la config d'un compte. Permet à {@link
 * net.pieroxy.imf.rules.MailAccount} de dépendre de cette étape par injection plutôt que
 * d'appeler {@link ImapMailboxConnection#connect} en dur, pour pouvoir être testée sans IMAPS/TLS
 * réel (voir {@code GreenMailImapFixture} côté tests).
 */
@FunctionalInterface
public interface ImapMailboxFactory {
  ImapMailbox connect(MailAccountConfiguration config) throws MessagingException;
}
