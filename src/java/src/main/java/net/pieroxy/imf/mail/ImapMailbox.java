package net.pieroxy.imf.mail;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.List;

/**
 * Abstraction sur l'accès à un compte IMAP : découple le reste du code des détails de
 * connexion (javax.mail Session/Store), et permet de le remplacer par un faux objet dans
 * les tests. Combine le suivi de l'INBOX par UID et un accès générique à l'arborescence de
 * dossiers, utilisé par l'apprentissage de règles (imf-rules/).
 */
public interface ImapMailbox extends AutoCloseable {
  long getUidValidity() throws MessagingException;

  long getUidNext() throws MessagingException;

  /** Messages de l'INBOX dont l'UID est strictement supérieur à lastUid, triés par UID croissant. */
  Message[] getMessagesSince(long lastUid) throws MessagingException;

  long getUid(Message message) throws MessagingException;

  /** Résout le dossier désigné par ce chemin (un segment par niveau), en le créant si besoin. */
  Folder getOrCreateFolder(String... pathSegments) throws MessagingException;

  /** Sous-dossiers directs de parent, dans l'ordre renvoyé par le serveur. */
  List<Folder> listSubfolders(Folder parent) throws MessagingException;

  /** Tous les messages de folder (l'ouvre en lecture/écriture si nécessaire). */
  Message[] getAllMessages(Folder folder) throws MessagingException;

  /** Ferme folder en purgeant (expunge) les messages marqués \Deleted. */
  void closeAndExpunge(Folder folder) throws MessagingException;

  @Override
  void close();
}
