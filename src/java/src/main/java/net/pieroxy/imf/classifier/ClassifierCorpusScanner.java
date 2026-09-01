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
 * Walks the account's folders, excluding INBOX and the imf-rules/ tree (internal to the tool,
 * not mail organized by the user), to build the training corpus: the Spam folder on one side,
 * everything else (Sent/Trash/Archive/...) as confirmed non-spam examples on the other. Only
 * fetches new messages since the last scan (per folder, via UID), so the whole history is never
 * re-downloaded on every pass.
 * <p>
 * Two different scan rhythms, exposed as two separate methods: {@link #scan} (the whole tree,
 * once a day — see MailAccount) and {@link #scanSpamFolderNow} (just Spam, every cycle). The
 * reason: Spam is the one folder a user might empty out before the next daily scan (e.g. a
 * manual purge every evening) — if it were only scanned once a day, whatever passed through it
 * could disappear before ever being captured. Both methods share the same
 * {@link ClassifierScanState} (per folder), so there's no double counting: what the frequent
 * scan already saw, the daily scan simply finds up to date and does nothing further with.
 * <p>
 * Additional folders (anywhere in the tree) can be excluded from the scan via
 * excludedFolderNames — neither SPAM nor HAM, entirely ignored, just like INBOX/imf-rules
 * already are. Useful in particular for a folder dedicated to the classifier's own verdicts
 * (e.g. a SUBJECT_CLASSIFIER_EQUALS rule that moves mail to "SpamML" rather than "Spam"):
 * without exclusion, that folder would be scanned like any other and, not carrying the name
 * configured for Spam, wrongly labeled HAM — worse than not learning from it at all, it would
 * poison the corpus with spam classified as legitimate. Excluding it also avoids the feedback
 * loop (the classifier training on its own past verdicts).
 */
public class ClassifierCorpusScanner {
  private final static Logger LOGGER = Logger.getLogger(ClassifierCorpusScanner.class.getName());
  private final static String LEARNING_ROOT_FOLDER = "imf-rules";
  /**
   * Cap on messages processed per call to scan(). On an account used for years, the very first
   * pass can represent thousands of messages across all folders; without a cap, a single cycle
   * could monopolize the account's IMAP connection (and thus delay normal INBOX processing by
   * as much) for a very long time. scan() returns as soon as this total is reached; MailAccount
   * then relaunches it on the next cycle (not the next day) as long as there's backlog left to
   * catch up on.
   */
  private final static int MAX_MESSAGES_PER_SCAN = 500;

  private final ImapMailbox mailbox;
  private final ClassifierCorpusStore corpusStore;
  private final String spamFolderName;
  private final Set<String> excludedFolderNames;
  private final String logPrefix;
  private int messagesProcessed;

  /**
   * @param accountLabel displayName (or login if absent — see {@link net.pieroxy.imf.rules.MailAccount}) of
   *                      the scanned account, used to prefix every log line ({@code "Classifier corpus [name] ..."})
   *                      so they're easy to tell apart in the logs of an instance watching several accounts.
   */
  public ClassifierCorpusScanner(ImapMailbox mailbox, ClassifierCorpusStore corpusStore, String spamFolderName,
                                  List<String> excludedFolderNames, String accountLabel) {
    this.mailbox = mailbox;
    this.corpusStore = corpusStore;
    this.spamFolderName = spamFolderName;
    // Set (not List): the exclusion list can hold several entries, so isExcluded() may as well
    // be an O(1) lookup rather than a scan — cheap to do once at construction. Normalized to
    // lowercase so the comparison stays case-insensitive without re-doing a stream on every call.
    this.excludedFolderNames = excludedFolderNames == null ? Set.of()
        : excludedFolderNames.stream().map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    this.logPrefix = "Classifier corpus [" + accountLabel + "] ";
  }

  /** @return true if the cap was reached (there's leftover work for the next call). */
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
   * Targeted scan of just the Spam folder, to be called every cycle (no cap: a single folder,
   * never large enough to justify spreading the work over several cycles). Silent when there's
   * nothing new — potentially called every minute, no need to log "scan starting/complete"
   * every time.
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
      // uidValidity differs from the stored one (or never scanned): the old UIDs no longer mean
      // anything, so start over from 0 for this folder (unlike INBOX, here we want the whole
      // existing history, not just what arrives after the scan).
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
