package net.pieroxy.imf.rules;

import net.pieroxy.imf.config.MailAccountConfiguration;
import net.pieroxy.imf.config.MailFilterRuleConfiguration;
import net.pieroxy.imf.learning.LearnedRulesStore;
import net.pieroxy.imf.learning.RuleLearner;
import net.pieroxy.imf.mail.ImapMailbox;
import net.pieroxy.imf.mail.ImapMailboxConnection;
import net.pieroxy.imf.scheduling.BackoffLoop;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.ArrayList;
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

  public MailAccount(MailAccountConfiguration config, String dataFolder) {
    this.config = config;
    this.stateStore = new MailAccountStateStore(dataFolder, config.getDisplayName());
    this.learnedRulesStore = new LearnedRulesStore(dataFolder, config.getDisplayName());
  }

  @Override
  public void run() {
    LOGGER.info("Starting account " + config.getDisplayName());
    new BackoffLoop(config.getRunEvery(), MAX_BACKOFF_MS).run(config.getDisplayName(), this::processMessages);
  }

  /** Applique la première règle qui matche (config manuelle, puis règles apprises). */
  private void inspect(Message message) {
    for (MailFilterRuleConfiguration ruleConfig : buildRuleConfigs()) {
      try {
        if (new Rule(ruleConfig).apply(message)) {
          return;
        }
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Rule failed on account " + config.getDisplayName(), e);
      }
    }
  }

  private List<MailFilterRuleConfiguration> buildRuleConfigs() {
    List<MailFilterRuleConfiguration> rules = new ArrayList<>();
    if (config.getRules() != null) {
      rules.addAll(config.getRules());
    }
    rules.addAll(learnedRulesStore.load());
    return rules;
  }

  private void processMessages() throws MessagingException {
    LOGGER.info("Processing account " + config.getDisplayName());
    try (ImapMailbox mailbox = ImapMailboxConnection.connect(config)) {
      RuleLearner learner = new RuleLearner(mailbox, learnedRulesStore);
      learner.ensureFolderSkeleton();
      learner.learnFromExamples();

      processNewMessages(mailbox);
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
