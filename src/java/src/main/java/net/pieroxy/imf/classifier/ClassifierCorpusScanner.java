package net.pieroxy.imf.classifier;

import net.pieroxy.imf.mail.ImapMailbox;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parcourt tous les dossiers du compte, hors INBOX et l'arbre imf-rules/ (interne à l'outil,
 * pas du courrier organisé par l'utilisateur), pour construire le corpus d'entraînement : le
 * dossier Spam d'un côté, tout le reste (Sent/Trash/Archive/...) comme exemples confirmés
 * non-spam de l'autre. Ne fetche que les nouveaux messages depuis le dernier scan (par
 * dossier, via UID), pour ne jamais retélécharger tout l'historique à chaque passage.
 */
public class ClassifierCorpusScanner {
  private final static Logger LOGGER = Logger.getLogger(ClassifierCorpusScanner.class.getName());
  private final static String LEARNING_ROOT_FOLDER = "imf-rules";
  /**
   * Plafond de messages traités par appel à scan(). Sur un compte utilisé depuis des années,
   * le tout premier passage peut représenter des milliers de messages à travers tous les
   * dossiers ; sans plafond, un seul cycle pourrait monopoliser la connexion IMAP du compte
   * (et donc retarder d'autant le traitement normal de l'INBOX) pendant très longtemps.
   * scan() rend la main dès que ce total est atteint ; MailAccount relance alors au cycle
   * suivant (pas le lendemain) tant qu'il reste du retard à rattraper.
   */
  private final static int MAX_MESSAGES_PER_SCAN = 500;

  private final ImapMailbox mailbox;
  private final ClassifierCorpusStore corpusStore;
  private final String spamFolderName;
  private int messagesProcessed;

  public ClassifierCorpusScanner(ImapMailbox mailbox, ClassifierCorpusStore corpusStore, String spamFolderName) {
    this.mailbox = mailbox;
    this.corpusStore = corpusStore;
    this.spamFolderName = spamFolderName;
  }

  /** @return true si le plafond a été atteint (il reste du travail pour le prochain appel). */
  public boolean scan(ClassifierScanState state, LocalDate today) throws MessagingException, IOException {
    LOGGER.info("Classifier corpus scan starting");
    List<ClassifierExample> newExamples = new ArrayList<>();
    messagesProcessed = 0;
    boolean budgetExceeded = walk(mailbox.getRootFolder(), state, newExamples);
    corpusStore.append(today, newExamples);
    corpusStore.pruneOlderThan(today);
    LOGGER.info("Classifier corpus scan " + (budgetExceeded ? "paused (budget reached, will resume next cycle): "
        : "complete: ") + newExamples.size() + " new example(s) recorded");
    return budgetExceeded;
  }

  private boolean walk(Folder parent, ClassifierScanState state, List<ClassifierExample> newExamples) throws MessagingException {
    for (Folder folder : mailbox.listSubfolders(parent)) {
      if (messagesProcessed >= MAX_MESSAGES_PER_SCAN) return true;

      String name = folder.getName();
      if ("INBOX".equalsIgnoreCase(name) || LEARNING_ROOT_FOLDER.equalsIgnoreCase(name)) continue;

      int type = folder.getType();
      if ((type & Folder.HOLDS_MESSAGES) != 0) {
        scanFolder(folder, state, newExamples);
      }
      if ((type & Folder.HOLDS_FOLDERS) != 0) {
        if (walk(folder, state, newExamples)) return true;
      }
    }
    return messagesProcessed >= MAX_MESSAGES_PER_SCAN;
  }

  private void scanFolder(Folder folder, ClassifierScanState state, List<ClassifierExample> newExamples) {
    String fullName = folder.getFullName();
    ClassifierLabel label = spamFolderName.equalsIgnoreCase(folder.getName()) ? ClassifierLabel.SPAM : ClassifierLabel.HAM;
    try {
      long uidValidity = mailbox.getUidValidity(folder);
      ClassifierScanState.FolderProgress progress = state.getFolderProgress(fullName);
      // uidValidity différente de celle stockée (ou jamais scanné) : les anciens UID ne
      // veulent plus rien dire, on repart de 0 pour ce dossier (contrairement à l'INBOX, on
      // veut ici tout l'historique existant, pas seulement ce qui arrive après le scan).
      long lastUid = (progress != null && progress.getUidValidity() == uidValidity) ? progress.getLastUid() : 0;

      Message[] messages = mailbox.getMessagesSince(folder, lastUid);
      if (messages.length > 0) {
        LOGGER.info("Classifier corpus: " + messages.length + " new message(s) in " + fullName + " (" + label + ")");
      }
      long newLastUid = lastUid;
      Instant fetchDate = Instant.now();
      for (Message message : messages) {
        try {
          newExamples.add(ClassifierExampleExtractor.extract(message, label, fetchDate));
        } catch (Exception e) {
          LOGGER.log(Level.WARNING, "Failed to extract classifier example from " + fullName, e);
        }
        newLastUid = Math.max(newLastUid, mailbox.getUid(folder, message));
      }
      messagesProcessed += messages.length;
      state.setFolderProgress(fullName, uidValidity, newLastUid);
    } catch (MessagingException e) {
      LOGGER.log(Level.WARNING, "Failed to scan folder " + fullName + " for the classifier corpus", e);
    } finally {
      try {
        mailbox.closeReadOnly(folder);
      } catch (MessagingException e) {
        LOGGER.log(Level.WARNING, "Failed to close folder " + fullName, e);
      }
    }
  }
}
