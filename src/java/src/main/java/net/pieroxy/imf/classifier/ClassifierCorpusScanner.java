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
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Parcourt les dossiers du compte, hors INBOX et l'arbre imf-rules/ (interne à l'outil, pas du
 * courrier organisé par l'utilisateur), pour construire le corpus d'entraînement : le dossier
 * Spam d'un côté, tout le reste (Sent/Trash/Archive/...) comme exemples confirmés non-spam de
 * l'autre. Ne fetche que les nouveaux messages depuis le dernier scan (par dossier, via UID),
 * pour ne jamais retélécharger tout l'historique à chaque passage.
 * <p>
 * Deux rythmes de scan différents, exposés par deux méthodes séparées : {@link #scan} (tout
 * l'arbre, une fois par jour — voir MailAccount) et {@link #scanSpamFolderNow} (juste Spam, à
 * chaque cycle). La raison : Spam est le seul dossier qu'un utilisateur peut vider avant le
 * prochain scan quotidien (ex: purge manuelle du soir) — s'il n'était scanné qu'une fois par
 * jour, ce qui y est passé pourrait disparaître avant d'avoir jamais été capturé. Les deux
 * méthodes partagent le même {@link ClassifierScanState} (par dossier), donc pas de double
 * comptage : ce que le scan fréquent a déjà vu, le scan quotidien le retrouve simplement à
 * jour et n'y refait rien.
 * <p>
 * Des dossiers supplémentaires (n'importe où dans l'arbre) peuvent être exclus du scan via
 * excludedFolderNames — ni SPAM ni HAM, complètement ignorés, comme INBOX/imf-rules le sont
 * déjà. Utile notamment pour un dossier dédié aux verdicts du classifieur lui-même (ex: une
 * règle SUBJECT_CLASSIFIER_EQUALS qui déplace vers "SpamML" plutôt que "Spam") : sans
 * exclusion, ce dossier serait scanné comme n'importe quel autre et, ne portant pas le nom
 * configuré pour Spam, étiqueté HAM à tort — pire que ne pas apprendre dessus, ça empoisonnerait
 * le corpus avec du spam classé légitime. L'exclure évite aussi la boucle de rétroaction (le
 * classifieur s'entraînant sur ses propres verdicts passés).
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
  private final Set<String> excludedFolderNames;
  private final String logPrefix;
  private int messagesProcessed;

  /**
   * @param accountLabel displayName (ou login si absent — voir {@link net.pieroxy.imf.rules.MailAccount}) du
   *                      compte scanné, pour préfixer chaque ligne de log ({@code "Classifier corpus [name] ..."})
   *                      et s'y retrouver dans les logs d'une instance qui surveille plusieurs comptes.
   */
  public ClassifierCorpusScanner(ImapMailbox mailbox, ClassifierCorpusStore corpusStore, String spamFolderName,
                                  List<String> excludedFolderNames, String accountLabel) {
    this.mailbox = mailbox;
    this.corpusStore = corpusStore;
    this.spamFolderName = spamFolderName;
    // Set (pas List) : la liste d'exclusion peut compter plusieurs entrées, autant faire de
    // isExcluded() une recherche O(1) plutôt qu'un balayage — pas cher à faire une fois à la
    // construction. Normalisé en minuscules pour garder la comparaison insensible à la casse
    // sans refaire un stream à chaque appel.
    this.excludedFolderNames = excludedFolderNames == null ? Set.of()
        : excludedFolderNames.stream().map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    this.logPrefix = "Classifier corpus [" + accountLabel + "] ";
  }

  /** @return true si le plafond a été atteint (il reste du travail pour le prochain appel). */
  public boolean scan(ClassifierScanState state, LocalDate today) throws MessagingException, IOException {
    LOGGER.info(logPrefix + "scan starting");
    List<ClassifierExample> newExamples = new ArrayList<>();
    messagesProcessed = 0;
    boolean budgetExceeded = walk(mailbox.getRootFolder(), state, newExamples, folder -> true, true);
    corpusStore.append(today, newExamples);
    corpusStore.pruneOlderThan(today);
    LOGGER.info(logPrefix + "scan " + (budgetExceeded ? "paused (budget reached, will resume next cycle): "
        : "complete: ") + newExamples.size() + " new example(s) recorded");
    return budgetExceeded;
  }

  /**
   * Scan ciblé du seul dossier Spam, à appeler à chaque cycle (pas de plafond : un seul
   * dossier, jamais assez volumineux pour justifier d'étaler le travail sur plusieurs cycles).
   * Silencieux quand il n'y a rien de nouveau — appelé potentiellement toutes les minutes, pas
   * la peine de logger "scan starting/complete" à chaque fois.
   */
  public void scanSpamFolderNow(ClassifierScanState state) throws MessagingException, IOException {
    List<ClassifierExample> newExamples = new ArrayList<>();
    messagesProcessed = 0;
    walk(mailbox.getRootFolder(), state, newExamples, folder -> spamFolderName.equalsIgnoreCase(folder.getName()), false);
    if (!newExamples.isEmpty()) {
      corpusStore.append(LocalDate.now(), newExamples);
    }
  }

  private boolean walk(Folder parent, ClassifierScanState state, List<ClassifierExample> newExamples,
                        Predicate<Folder> shouldScan, boolean enforceBudget) throws MessagingException {
    for (Folder folder : mailbox.listSubfolders(parent)) {
      if (enforceBudget && messagesProcessed >= MAX_MESSAGES_PER_SCAN) return true;

      String name = folder.getName();
      if ("INBOX".equalsIgnoreCase(name) || LEARNING_ROOT_FOLDER.equalsIgnoreCase(name) || isExcluded(name)) continue;

      int type = folder.getType();
      if ((type & Folder.HOLDS_MESSAGES) != 0 && shouldScan.test(folder)) {
        scanFolder(folder, state, newExamples);
      }
      if ((type & Folder.HOLDS_FOLDERS) != 0) {
        if (walk(folder, state, newExamples, shouldScan, enforceBudget)) return true;
      }
    }
    return enforceBudget && messagesProcessed >= MAX_MESSAGES_PER_SCAN;
  }

  private boolean isExcluded(String folderName) {
    return excludedFolderNames.contains(folderName.toLowerCase(Locale.ROOT));
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
        LOGGER.info(logPrefix + messages.length + " new message(s) in " + fullName + " (" + label + ")");
      }
      long newLastUid = lastUid;
      Instant fetchDate = Instant.now();
      for (Message message : messages) {
        try {
          newExamples.add(ClassifierExampleExtractor.extract(message, label, fetchDate));
        } catch (Exception e) {
          LOGGER.log(Level.WARNING, logPrefix + "Failed to extract example from " + fullName, e);
        }
        newLastUid = Math.max(newLastUid, mailbox.getUid(folder, message));
      }
      messagesProcessed += messages.length;
      state.setFolderProgress(fullName, uidValidity, newLastUid);
    } catch (MessagingException e) {
      LOGGER.log(Level.WARNING, logPrefix + "Failed to scan folder " + fullName, e);
    } finally {
      try {
        mailbox.closeReadOnly(folder);
      } catch (MessagingException e) {
        LOGGER.log(Level.WARNING, logPrefix + "Failed to close folder " + fullName, e);
      }
    }
  }
}
