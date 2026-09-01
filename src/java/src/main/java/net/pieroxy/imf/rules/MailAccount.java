package net.pieroxy.imf.rules;

import net.pieroxy.imf.classifier.ClassifierCorpusScanner;
import net.pieroxy.imf.classifier.ClassifierCorpusStore;
import net.pieroxy.imf.classifier.ClassifierScanState;
import net.pieroxy.imf.classifier.ClassifierScanStateStore;
import net.pieroxy.imf.classifier.SubjectClassifierTrainer;
import net.pieroxy.imf.config.MailAccountConfiguration;
import net.pieroxy.imf.learning.LearnedRulesStore;
import net.pieroxy.imf.learning.RuleLearner;
import net.pieroxy.imf.mail.ImapMailbox;
import net.pieroxy.imf.mail.ImapMailboxConnection;
import net.pieroxy.imf.mail.ImapMailboxFactory;
import net.pieroxy.imf.rules.matchers.SubjectClassifierContext;
import net.pieroxy.imf.scheduling.BackoffLoop;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.time.LocalDate;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Orchestre le traitement d'un compte : planifie les cycles (via {@link BackoffLoop}),
 * récupère les nouveaux messages (via {@link ImapMailbox}) et suit la progression
 * (via {@link MailAccountStateStore}). Ne connaît aucun détail de connexion IMAP ni de
 * persistance : chaque responsabilité vit dans sa propre classe, injectable/testable seule.
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
  private final String classifierSpamFolderName;
  private final List<String> classifierExcludedFolders;
  private final ImapMailboxFactory mailboxFactory;
  private LocalDate lastSkeletonEnsureDate;

  public MailAccount(MailAccountConfiguration config, String dataFolder, int classifierCorpusRetentionDays) {
    this(config, dataFolder, classifierCorpusRetentionDays, ImapMailboxConnection::connect);
  }

  /** Visible pour les tests : permet d'injecter une fabrique de mailbox sans IMAPS/TLS réel. */
  MailAccount(MailAccountConfiguration config, String dataFolder, int classifierCorpusRetentionDays, ImapMailboxFactory mailboxFactory) {
    this.config = config;
    this.stateStore = new MailAccountStateStore(dataFolder, config.getDisplayName());
    this.learnedRulesStore = new LearnedRulesStore(dataFolder, config.getDisplayName());
    this.ruleCatalog = new RuleCatalog(config.getRules(), learnedRulesStore);
    this.classifierCorpusRetentionDays = classifierCorpusRetentionDays;
    this.classifierScanStateStore = new ClassifierScanStateStore(dataFolder, config.getDisplayName());
    this.classifierCorpusStore = new ClassifierCorpusStore(dataFolder, config.getDisplayName(), classifierCorpusRetentionDays);
    this.subjectClassifierTrainer = new SubjectClassifierTrainer(classifierCorpusStore);
    String spamFolderName = config.getClassifierSpamFolderName();
    this.classifierSpamFolderName = (spamFolderName == null || spamFolderName.isBlank()) ? "Spam" : spamFolderName;
    this.classifierExcludedFolders = config.getClassifierExcludedFolders() != null
        ? config.getClassifierExcludedFolders() : List.of();
    this.mailboxFactory = mailboxFactory;
  }

  @Override
  public void run() {
    // Une fois pour toutes sur CE thread, avant tout traitement : SubjectClassifierMatcher n'a
    // aucun autre moyen de savoir à quel compte (donc quel modèle) il appartient, puisqu'il est
    // construit sans contexte par MatcherType.getImplementation(). Ça tient parce que ce thread
    // est dédié à ce compte pour toute la durée de vie du process (voir SubjectClassifierContext).
    SubjectClassifierContext.set(classifierCorpusStore.getModelFile());
    LOGGER.info("Starting account " + config.getDisplayName());
    // Construit l'arbre Matcher/Action tout de suite plutôt que d'attendre le premier message :
    // RuleCatalog est normalement paresseux (voir inspect()), mais certains matchers (comme
    // SubjectClassifierMatcher) ont besoin d'être construits pour annoncer leur état dès le
    // démarrage — sinon, sur un compte qui ne reçoit rien tout de suite, on ne saurait jamais
    // si le classifieur est actif ou pas.
    ruleCatalog.get();
    new BackoffLoop(config.getRunEvery() * 1000L, MAX_BACKOFF_MS).run(config.getDisplayName(), this::processMessages);
  }

  /** Applique la première règle qui matche (config manuelle, puis règles apprises). */
  private void inspect(Message message) {
    Rule.applyFirstMatching(ruleCatalog.get(), message, LOGGER, "account " + config.getDisplayName());
  }

  /** Package-private (au lieu de private) : permet à MailAccountTest d'exécuter un cycle sans passer par run()/BackoffLoop. */
  void processMessages() throws MessagingException {
    LOGGER.info("Processing account " + config.getDisplayName());
    try (ImapMailbox mailbox = mailboxFactory.connect(config)) {
      RuleLearner learner = new RuleLearner(mailbox, learnedRulesStore);
      ManualReprocessor reprocessor = new ManualReprocessor(mailbox, ruleCatalog);
      ensureFolderSkeletonsIfDue(learner, reprocessor);

      if (learner.learnFromExamples()) {
        ruleCatalog.invalidate();
        ruleCatalog.get(); // reconstruit tout de suite (voir le commentaire dans run())
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
   * Contrairement au reste de l'arborescence (une fois par jour, voir plus bas), Spam est
   * scanné à chaque cycle : c'est le seul dossier qu'un utilisateur est susceptible de vider
   * lui-même avant le prochain scan quotidien (ex: purge manuelle tous les soirs) — si on
   * attendait le lendemain, tout le spam de la veille aurait disparu avant d'avoir jamais été
   * capturé pour le corpus. Partage le même état (par dossier) que le scan quotidien, donc pas
   * de double comptage entre les deux.
   */
  private void scanSpamFolderForClassifierCorpus(ImapMailbox mailbox) {
    ClassifierScanState state = classifierScanStateStore.load();
    try {
      new ClassifierCorpusScanner(mailbox, classifierCorpusStore, classifierSpamFolderName, classifierExcludedFolders)
          .scanSpamFolderNow(state);
      classifierScanStateStore.save(state);
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Classifier corpus spam scan failed for account " + config.getDisplayName(), e);
    }
  }

  /**
   * Les dossiers "imf-rules/..." ne changent quasiment jamais une fois créés : pas la peine de
   * revérifier leur existence à chaque cycle (potentiellement toutes les minutes selon
   * runEvery). Une fois au démarrage (lastSkeletonEnsureDate vaut encore null) puis une fois
   * par jour civil ensuite suffit à se remettre d'une suppression accidentelle sans attendre un
   * redémarrage.
   */
  private void ensureFolderSkeletonsIfDue(RuleLearner learner, ManualReprocessor reprocessor) throws MessagingException {
    LocalDate today = LocalDate.now();
    if (today.equals(lastSkeletonEnsureDate)) return;
    learner.ensureFolderSkeleton();
    reprocessor.ensureFolderSkeleton();
    lastSkeletonEnsureDate = today;
  }

  /**
   * Scanne au plus une fois par jour civil une fois à jour (pas de scheduler dédié : on
   * profite du cycle déjà en cours, sur la même connexion IMAP). Tant qu'il reste du retard
   * à rattraper (scan() plafonné, voir {@link ClassifierCorpusScanner}), on relance au cycle
   * suivant au lieu d'attendre le lendemain, pour rattraper l'historique en plusieurs cycles
   * rapides plutôt qu'un seul cycle interminable. Une erreur ici n'empêche jamais le
   * traitement normal des messages, qui vient de se terminer avec succès juste au-dessus.
   */
  private void scanClassifierCorpusIfDue(ImapMailbox mailbox) {
    LocalDate today = LocalDate.now();
    ClassifierScanState state = classifierScanStateStore.load();
    if (today.toString().equals(state.getLastScanDate())) return;

    boolean caughtUpToday;
    try {
      boolean moreWorkPending = new ClassifierCorpusScanner(mailbox, classifierCorpusStore, classifierSpamFolderName,
          classifierExcludedFolders).scan(state, today);
      caughtUpToday = !moreWorkPending;
      if (caughtUpToday) {
        state.setLastScanDate(today.toString());
      }
      classifierScanStateStore.save(state);
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Classifier corpus scan failed for account " + config.getDisplayName(), e);
      return;
    }

    // Séparé du try ci-dessus : un échec d'entraînement ne doit pas empêcher le scan (déjà
    // réussi) d'avoir marqué la journée comme traitée, sinon on relance le scan à chaque cycle
    // pour rien alors que lui a fonctionné.
    if (caughtUpToday) {
      try {
        subjectClassifierTrainer.train();
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Subject classifier training failed for account " + config.getDisplayName(), e);
      }
    }
  }

  /**
   * Ne traite que les messages dont l'UID est strictement supérieur au dernier UID connu,
   * afin qu'un message ne soit jamais inspecté deux fois d'un cycle à l'autre.
   */
  private void processNewMessages(ImapMailbox mailbox) throws MessagingException {
    MailAccountState state = stateStore.load();

    long uidValidity = mailbox.getUidValidity();
    if (state.getUidValidity() != uidValidity) {
      // Première exécution pour ce compte, ou UIDVALIDITY changée côté serveur (mailbox recréée) :
      // les anciens UID ne veulent plus rien dire. On repart de "maintenant" plutôt que de rejouer
      // tout l'historique de la boîte.
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
