package net.pieroxy.imf.learning;

import net.pieroxy.imf.config.MailFilterRuleActionConfiguration;
import net.pieroxy.imf.config.MailFilterRuleConfiguration;
import net.pieroxy.imf.config.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.mail.ImapMailbox;
import net.pieroxy.imf.rules.actions.Action;
import net.pieroxy.imf.rules.actions.ActionType;
import net.pieroxy.imf.rules.matchers.Matcher;
import net.pieroxy.imf.rules.matchers.MatcherType;
import net.pieroxy.imf.utils.MailTools;

import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Règle par l'exemple : l'utilisateur dépose un message dans
 * imf-rules/&lt;MATCHER_TYPE&gt;/&lt;ACTION_TYPE&gt;/&lt;clé&gt; (ex: imf-rules/FROM_EQUALS/MOVE_TO/Spam)
 * pour créer la règle "si &lt;MATCHER_TYPE&gt; extrait du message matche, alors &lt;ACTION_TYPE&gt;(&lt;clé&gt;)".
 * Les types composites (AND/OR) sont exclus : réservés à la config manuelle.
 */
public class RuleLearner {
  private final static Logger LOGGER = Logger.getLogger(RuleLearner.class.getName());
  private final static String ROOT_FOLDER = "imf-rules";
  private final static String DONE_FOLDER = "Done";

  private final ImapMailbox mailbox;
  private final LearnedRulesStore store;

  public RuleLearner(ImapMailbox mailbox, LearnedRulesStore store) {
    this.mailbox = mailbox;
    this.store = store;
  }

  /** Crée l'arborescence de dossiers "prête à l'emploi" (le niveau clé, ex: "Spam", reste à créer par l'utilisateur). */
  public void ensureFolderSkeleton() throws MessagingException {
    mailbox.getOrCreateFolder(ROOT_FOLDER, DONE_FOLDER);
    for (MatcherType matcherType : MatcherType.learnableValues()) {
      for (ActionType actionType : ActionType.learnableValues()) {
        mailbox.getOrCreateFolder(ROOT_FOLDER, matcherType.name(), actionType.name());
      }
    }
  }

  /** @return true si au moins une nouvelle règle a été apprise pendant cet appel. */
  public boolean learnFromExamples() throws MessagingException {
    boolean learnedSomething = false;
    for (MatcherType matcherType : MatcherType.learnableValues()) {
      for (ActionType actionType : ActionType.learnableValues()) {
        Folder actionFolder = mailbox.getOrCreateFolder(ROOT_FOLDER, matcherType.name(), actionType.name());
        for (Folder keyFolder : mailbox.listSubfolders(actionFolder)) {
          learnedSomething |= learnFromKeyFolder(matcherType, actionType, keyFolder);
        }
      }
    }
    return learnedSomething;
  }

  private boolean learnFromKeyFolder(MatcherType matcherType, ActionType actionType, Folder keyFolder) throws MessagingException {
    String actionKey = keyFolder.getName();
    Message[] examples = mailbox.getAllMessages(keyFolder);
    boolean learnedSomething = false;
    try {
      for (Message example : examples) {
        learnedSomething |= learnFromExample(matcherType, actionType, actionKey, example);
      }
    } finally {
      mailbox.closeAndExpunge(keyFolder);
    }
    return learnedSomething;
  }

  private boolean learnFromExample(MatcherType matcherType, ActionType actionType, String actionKey, Message example) {
    try {
      Matcher matcher = matcherType.getImplementation();
      String matcherKey = matcher.extractKeyFromExample(example);

      MailFilterRuleMatcherConfiguration matcherConfig = new MailFilterRuleMatcherConfiguration();
      matcherConfig.setType(matcherType);
      matcherConfig.setKey(matcherKey);

      MailFilterRuleActionConfiguration actionConfig = new MailFilterRuleActionConfiguration();
      actionConfig.setType(actionType);
      actionConfig.setKey(actionKey);

      MailFilterRuleConfiguration ruleConfig = new MailFilterRuleConfiguration();
      ruleConfig.setMatcher(matcherConfig);
      ruleConfig.setAction(actionConfig);

      boolean learned = store.addIfAbsent(ruleConfig);
      if (learned) {
        LOGGER.info("Learned rule: " + matcherType + "(" + matcherKey + ") -> " + actionType + "(" + actionKey + ")");
      }

      Action action = Action.build(actionConfig);
      try {
        boolean result = action.run(example);
        action.getLogger().info(() -> "Action applied (success=" + result + ") to learning example from "
                + MailTools.describeFromSafely(example));
      } catch (Exception e) {
        action.getLogger().log(Level.WARNING, "Action failed on learning example from "
                + MailTools.describeFromSafely(example), e);
      }

      if (!example.isSet(Flags.Flag.DELETED)) {
        // L'action n'a ni déplacé ni supprimé le message d'exemple : on le range quand même
        // pour ne pas réapprendre la même règle en boucle à chaque cycle.
        moveToDone(example);
      }
      return learned;
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Failed to learn a rule from example message under "
              + ROOT_FOLDER + "/" + matcherType + "/" + actionType + "/" + actionKey, e);
      return false;
    }
  }

  private void moveToDone(Message example) throws MessagingException {
    Folder doneFolder = mailbox.getOrCreateFolder(ROOT_FOLDER, DONE_FOLDER);
    example.getFolder().copyMessages(new Message[]{example}, doneFolder);
    example.setFlag(Flags.Flag.DELETED, true);
  }
}
