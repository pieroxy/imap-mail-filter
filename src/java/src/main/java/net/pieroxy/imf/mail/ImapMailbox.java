package net.pieroxy.imf.mail;

import javax.mail.Message;
import javax.mail.MessagingException;

/**
 * Abstraction sur l'accès à l'INBOX d'un compte : découple le reste du code des détails
 * de connexion IMAP (javax.mail Session/Store/Folder), et permet de le remplacer par un
 * faux objet dans les tests.
 */
public interface ImapMailbox extends AutoCloseable {
  long getUidValidity() throws MessagingException;

  long getUidNext() throws MessagingException;

  /** Messages dont l'UID est strictement supérieur à lastUid, triés par UID croissant. */
  Message[] getMessagesSince(long lastUid) throws MessagingException;

  long getUid(Message message) throws MessagingException;

  @Override
  void close();
}
