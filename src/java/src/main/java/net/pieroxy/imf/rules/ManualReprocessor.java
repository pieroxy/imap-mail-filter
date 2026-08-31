package net.pieroxy.imf.rules;

import net.pieroxy.imf.mail.ImapMailbox;
import net.pieroxy.imf.utils.MailTools;

import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Rejoue le catalogue de règles sur des messages déposés à la main dans imf-rules/ToProcess
 * (ex: un mail déjà présent dans l'INBOX qu'on veut refaire passer par les règles, après avoir
 * ajouté ou corrigé une règle qui aurait dû s'en occuper). Chaque message y est traité comme
 * s'il venait d'arriver : la première règle qui matche s'applique normalement. S'il reste dans
 * ToProcess une fois le catalogue épuisé — qu'aucune règle n'ait matché, ou que celle qui a
 * matché ne l'ait ni déplacé ni supprimé — il est rangé dans imf-rules/Done (même dossier que
 * {@link net.pieroxy.imf.learning.RuleLearner}), à charge pour l'utilisateur d'y jeter un oeil
 * puisque c'est lui qui a déposé le message à la main.
 */
public class ManualReprocessor {
  private final static Logger LOGGER = Logger.getLogger(ManualReprocessor.class.getName());
  private final static String ROOT_FOLDER = "imf-rules";
  private final static String TO_PROCESS_FOLDER = "ToProcess";
  private final static String DONE_FOLDER = "Done";

  private final ImapMailbox mailbox;
  private final RuleCatalog ruleCatalog;

  public ManualReprocessor(ImapMailbox mailbox, RuleCatalog ruleCatalog) {
    this.mailbox = mailbox;
    this.ruleCatalog = ruleCatalog;
  }

  /** Crée imf-rules/ToProcess si besoin, prêt à recevoir des messages déposés à la main. */
  public void ensureFolderSkeleton() throws MessagingException {
    mailbox.getOrCreateFolder(ROOT_FOLDER, TO_PROCESS_FOLDER);
  }

  public void reprocessPending() throws MessagingException {
    Folder toProcessFolder = mailbox.getOrCreateFolder(ROOT_FOLDER, TO_PROCESS_FOLDER);
    Message[] pending = mailbox.getAllMessages(toProcessFolder);
    try {
      for (Message message : pending) {
        reprocess(message);
      }
    } finally {
      mailbox.closeAndExpunge(toProcessFolder);
    }
  }

  private void reprocess(Message message) {
    try {
      Rule.applyFirstMatching(ruleCatalog.get(), message, LOGGER, ROOT_FOLDER + "/" + TO_PROCESS_FOLDER);
      if (!message.isSet(Flags.Flag.DELETED)) {
        // Aucune règle n'a matché, ou celle qui a matché n'a pas déplacé/supprimé le message
        // (ex: une action qui se contente de le marquer) : on le range quand même pour ne pas
        // le retraiter en boucle à chaque cycle.
        moveToDone(message);
      }
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Failed to reprocess message from " + MailTools.describeFromSafely(message)
              + " under " + ROOT_FOLDER + "/" + TO_PROCESS_FOLDER, e);
    }
  }

  private void moveToDone(Message message) throws MessagingException {
    Folder doneFolder = mailbox.getOrCreateFolder(ROOT_FOLDER, DONE_FOLDER);
    message.getFolder().copyMessages(new Message[]{message}, doneFolder);
    message.setFlag(Flags.Flag.DELETED, true);
  }
}
