package net.pieroxy.imf.rules;

import net.pieroxy.imf.classifier.ClassifierCorpusScanner;
import net.pieroxy.imf.classifier.ClassifierCorpusStore;
import net.pieroxy.imf.classifier.ClassifierScanState;
import net.pieroxy.imf.classifier.ClassifierScanStateStore;
import net.pieroxy.imf.classifier.HeaderClassifierTrainer;
import net.pieroxy.imf.classifier.SubjectClassifierTrainer;
import net.pieroxy.imf.config.MailAccountConfiguration;
import net.pieroxy.imf.learning.LearnedRulesStore;
import net.pieroxy.imf.learning.RuleLearner;
import net.pieroxy.imf.mail.ImapIdleWatcher;
import net.pieroxy.imf.mail.ImapMailbox;
import net.pieroxy.imf.mail.ImapMailboxConnection;
import net.pieroxy.imf.mail.ImapMailboxFactory;
import net.pieroxy.imf.rules.matchers.HeaderClassifierContext;
import net.pieroxy.imf.rules.matchers.SubjectClassifierContext;
import net.pieroxy.imf.scheduling.BackoffLoop;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.time.LocalDate;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Orchestrates processing for an account: schedules cycles (via {@link BackoffLoop}), fetches
 * new messages (via {@link ImapMailbox}) and tracks progress (via {@link MailAccountStateStore}).
 * Knows no detail of IMAP connections or persistence: each responsibility lives in its own
 * class, injectable/testable on its own.
 */
public class MailAccount implements Runnable {
  private final static Logger LOGGER = Logger.getLogger(MailAccount.class.getName());
  private final static long MAX_BACKOFF_MS = 30 * 60 * 1000L; // 30 minutes

  private final MailAccountConfiguration config;
  private final MailAccountStateStore stateStore;
  private final LearnedRulesStore learnedRulesStore;
  private final RuleCatalog ruleCatalog;
  private final int classifierCorpusRetentionDays;
  private final ClassifierScanStateStore classifierScanStateStore;
  private final ClassifierCorpusStore classifierCorpusStore;
  private final SubjectClassifierTrainer subjectClassifierTrainer;
  private final HeaderClassifierTrainer headerClassifierTrainer;
  private final String classifierSpamFolderName;
  private final List<String> classifierExcludedFolders;
  private final ImapMailboxFactory mailboxFactory;
  private LocalDate lastSkeletonEnsureDate;

  public MailAccount(MailAccountConfiguration config, String dataFolder, int classifierCorpusRetentionDays) {
    this(config, dataFolder, classifierCorpusRetentionDays, ImapMailboxConnection::connect);
  }

  /** Visible for tests: lets a mailbox factory be injected without real IMAPS/TLS. */
  MailAccount(MailAccountConfiguration config, String dataFolder, int classifierCorpusRetentionDays, ImapMailboxFactory mailboxFactory) {
    this.config = config;
    this.stateStore = new MailAccountStateStore(dataFolder, config.getDisplayName());
    this.learnedRulesStore = new LearnedRulesStore(dataFolder, config.getDisplayName());
    this.ruleCatalog = new RuleCatalog(config.getRules(), learnedRulesStore);
    this.classifierCorpusRetentionDays = classifierCorpusRetentionDays;
    this.classifierScanStateStore = new ClassifierScanStateStore(dataFolder, config.getDisplayName());
    this.classifierCorpusStore = new ClassifierCorpusStore(dataFolder, config.getDisplayName(), classifierCorpusRetentionDays);
    this.subjectClassifierTrainer = new SubjectClassifierTrainer(classifierCorpusStore);
    this.headerClassifierTrainer = new HeaderClassifierTrainer(classifierCorpusStore);
    String spamFolderName = config.getClassifierSpamFolderName();
    this.classifierSpamFolderName = (spamFolderName == null || spamFolderName.isBlank()) ? "Spam" : spamFolderName;
    this.classifierExcludedFolders = config.getClassifierExcludedFolders() != null
        ? config.getClassifierExcludedFolders() : List.of();
    this.mailboxFactory = mailboxFactory;
  }

  @Override
  public void run() {
    // Once and for all on THIS thread, before any processing: SubjectClassifierMatcher has no
    // other way to know which account (hence which model) it belongs to, since it's built
    // without context by MatcherType.getImplementation(). This holds because this thread is
    // dedicated to this account for the whole lifetime of the process (see SubjectClassifierContext).
    SubjectClassifierContext.set(classifierCorpusStore.getModelFile());
    HeaderClassifierContext.set(classifierCorpusStore.getHeaderModelFile());
    LOGGER.info("Starting account " + config.getDisplayName());
    // Builds the Matcher/Action tree right away rather than waiting for the first message:
    // RuleCatalog is normally lazy (see inspect()), but some matchers (like
    // SubjectClassifierMatcher) need to be built to announce their state right at startup —
    // otherwise, on an account that doesn't receive anything right away, we'd never know
    // whether the classifier is active or not.
    ruleCatalog.get();
    ruleCatalog.logRules(LOGGER, accountLabel());

    // Everything in processMessages() but the INBOX scan can tolerate the full runEvery delay
    // (see MailAccountConfiguration.runEvery); only new mail sitting unclassified in the INBOX
    // is time-sensitive. Rather than shrinking runEvery for everything, a dedicated IMAP IDLE
    // connection watches the INBOX and wakes this loop early the instant new mail arrives —
    // runEvery then only bounds the worst case (a server without IDLE support, or a dropped
    // IDLE connection still reconnecting). See ImapIdleWatcher.
    BackoffLoop mainLoop = new BackoffLoop(config.getRunEvery() * 1000L, MAX_BACKOFF_MS);
    ImapIdleWatcher idleWatcher = new ImapIdleWatcher(config, mainLoop::wake);
    Thread idleThread = new Thread(idleWatcher, "mail-account-" + accountLabel() + "-idle");
    idleThread.setDaemon(true);
    idleThread.start();
    try {
      mainLoop.run(config.getDisplayName(), this::processMessages);
    } finally {
      idleWatcher.shutdown();
    }
  }

  /** displayName if set, otherwise falls back to the IMAP login — see {@link RuleCatalog#logRules}. */
  private String accountLabel() {
    String displayName = config.getDisplayName();
    return (displayName == null || displayName.isBlank()) ? config.getUsername() : displayName;
  }

  /** Applies the first matching rule (manual config, then learned rules). */
  private void inspect(Message message) {
    Rule.applyFirstMatching(ruleCatalog.get(), message, LOGGER, "account " + config.getDisplayName());
  }

  /** Package-private (instead of private): lets MailAccountTest run a cycle without going through run()/BackoffLoop. */
  void processMessages() throws MessagingException {
    LOGGER.info("Processing account " + config.getDisplayName());
    try (ImapMailbox mailbox = mailboxFactory.connect(config)) {
      RuleLearner learner = new RuleLearner(mailbox, learnedRulesStore, config.getLearningShortcuts());
      ManualReprocessor reprocessor = new ManualReprocessor(mailbox, ruleCatalog);
      ensureFolderSkeletonsIfDue(learner, reprocessor);

      if (learner.learnFromExamples()) {
        ruleCatalog.invalidate();
        ruleCatalog.get(); // rebuilds right away (see the comment in run())
      }

      processNewMessages(mailbox);

      reprocessor.reprocessPending();

      if (classifierCorpusRetentionDays > 0) {
        scanSpamFolderForClassifierCorpus(mailbox);
        scanClassifierCorpusIfDue(mailbox);
      }
    }
  }

  /**
   * Unlike the rest of the tree (once a day, see below), Spam is scanned every cycle: it's the
   * one folder a user is likely to empty out themselves before the next daily scan (e.g. a
   * manual purge every evening) — if we waited until the next day, all of yesterday's spam
   * would be gone before ever being captured for the corpus. Shares the same (per-folder) state
   * as the daily scan, so there's no double counting between the two.
   */
  private void scanSpamFolderForClassifierCorpus(ImapMailbox mailbox) {
    ClassifierScanState state = classifierScanStateStore.load();
    try {
      new ClassifierCorpusScanner(mailbox, classifierCorpusStore, classifierSpamFolderName, classifierExcludedFolders, accountLabel())
          .scanSpamFolderNow(state);
      classifierScanStateStore.save(state);
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Classifier corpus [" + accountLabel() + "] spam scan failed", e);
    }
  }

  /**
   * The "imf-rules/..." folders almost never change once created: no need to recheck their
   * existence every cycle (potentially every minute depending on runEvery). Once at startup
   * (lastSkeletonEnsureDate is still null) then once per calendar day afterward is enough to
   * recover from an accidental deletion without waiting for a restart.
   */
  private void ensureFolderSkeletonsIfDue(RuleLearner learner, ManualReprocessor reprocessor) throws MessagingException {
    LocalDate today = LocalDate.now();
    if (today.equals(lastSkeletonEnsureDate)) return;
    learner.ensureFolderSkeleton();
    reprocessor.ensureFolderSkeleton();
    lastSkeletonEnsureDate = today;
  }

  /**
   * Scans at most once per calendar day once caught up (no dedicated scheduler: it piggybacks
   * on the cycle already in progress, over the same IMAP connection). As long as there's
   * backlog left to catch up on (scan() is capped, see {@link ClassifierCorpusScanner}), it
   * relaunches on the next cycle instead of waiting for the next day, to catch up on history
   * over several quick cycles rather than one endless one. An error here never blocks normal
   * message processing, which just finished successfully right above.
   */
  private void scanClassifierCorpusIfDue(ImapMailbox mailbox) {
    LocalDate today = LocalDate.now();
    ClassifierScanState state = classifierScanStateStore.load();
    if (today.toString().equals(state.getLastScanDate())) return;

    boolean caughtUpToday;
    try {
      boolean moreWorkPending = new ClassifierCorpusScanner(mailbox, classifierCorpusStore, classifierSpamFolderName,
          classifierExcludedFolders, accountLabel()).scan(state, today);
      caughtUpToday = !moreWorkPending;
      if (caughtUpToday) {
        state.setLastScanDate(today.toString());
      }
      classifierScanStateStore.save(state);
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Classifier corpus [" + accountLabel() + "] scan failed", e);
      return;
    }

    // Separate from the try above: a training failure must not prevent the (already successful)
    // scan from having marked the day as done, otherwise the scan would be relaunched every
    // cycle for nothing even though it worked fine.
    if (caughtUpToday) {
      try {
        subjectClassifierTrainer.train();
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Subject classifier training failed for account " + config.getDisplayName(), e);
      }
      // Separate try/catch: a failure training one classifier must not skip the other.
      try {
        headerClassifierTrainer.train();
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Header classifier training failed for account " + config.getDisplayName(), e);
      }
    }
  }

  /**
   * Only processes messages whose UID is strictly greater than the last known UID, so a message
   * is never inspected twice from one cycle to the next.
   */
  private void processNewMessages(ImapMailbox mailbox) throws MessagingException {
    MailAccountState state = stateStore.load();

    long uidValidity = mailbox.getUidValidity();
    if (state.getUidValidity() != uidValidity) {
      // First run for this account, or UIDVALIDITY changed server-side (mailbox recreated): the
      // old UIDs no longer mean anything. Start over from "now" rather than replaying the whole
      // mailbox history.
      state.setUidValidity(uidValidity);
      state.setLastUid(mailbox.getUidNext() - 1);
    }

    for (Message message : mailbox.getMessagesSince(state.getLastUid())) {
      long uid = mailbox.getUid(message);
      try {
        inspect(message);
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Failed to inspect message UID " + uid + " on account " + config.getDisplayName(), e);
      }
      state.setLastUid(uid);
    }

    stateStore.save(state);
  }
}
