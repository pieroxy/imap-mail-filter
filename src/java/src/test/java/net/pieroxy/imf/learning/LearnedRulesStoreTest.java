package net.pieroxy.imf.learning;

import net.pieroxy.imf.config.MailFilterRuleActionConfiguration;
import net.pieroxy.imf.config.MailFilterRuleConfiguration;
import net.pieroxy.imf.config.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.rules.actions.ActionType;
import net.pieroxy.imf.rules.matchers.MatcherType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LearnedRulesStoreTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private static MailFilterRuleConfiguration rule(String fromKey, String targetFolder) {
    MailFilterRuleMatcherConfiguration matcher = new MailFilterRuleMatcherConfiguration();
    matcher.setType(MatcherType.FROM_EQUALS);
    matcher.setKey(fromKey);

    MailFilterRuleActionConfiguration action = new MailFilterRuleActionConfiguration();
    action.setType(ActionType.MOVE_TO);
    action.setKey(targetFolder);

    MailFilterRuleConfiguration r = new MailFilterRuleConfiguration();
    r.setMatcher(matcher);
    r.setAction(action);
    return r;
  }

  @Test
  public void loadReturnsEmptyListWhenFileDoesNotExist() {
    LearnedRulesStore store = new LearnedRulesStore(tmp.getRoot().getAbsolutePath(), "account");

    assertTrue(store.load().isEmpty());
  }

  @Test
  public void addIfAbsentPersistsNewRule() {
    LearnedRulesStore store = new LearnedRulesStore(tmp.getRoot().getAbsolutePath(), "account");

    boolean added = store.addIfAbsent(rule("alice@example.com", "Spam"));

    assertTrue(added);
    List<MailFilterRuleConfiguration> rules = store.load();
    assertEquals(1, rules.size());
    assertEquals("alice@example.com", rules.get(0).getMatcher().getKey());
    assertEquals("Spam", rules.get(0).getAction().getKey());
  }

  @Test
  public void addIfAbsentIgnoresExactDuplicate() {
    LearnedRulesStore store = new LearnedRulesStore(tmp.getRoot().getAbsolutePath(), "account");
    store.addIfAbsent(rule("alice@example.com", "Spam"));

    boolean added = store.addIfAbsent(rule("alice@example.com", "Spam"));

    assertFalse(added);
    assertEquals(1, store.load().size());
  }

  @Test
  public void addIfAbsentKeepsRulesWithDifferentTargets() {
    LearnedRulesStore store = new LearnedRulesStore(tmp.getRoot().getAbsolutePath(), "account");
    store.addIfAbsent(rule("alice@example.com", "Spam"));

    boolean added = store.addIfAbsent(rule("alice@example.com", "Archive"));

    assertTrue(added);
    assertEquals(2, store.load().size());
  }

  @Test
  public void twoAccountsDoNotShareTheSameFile() {
    LearnedRulesStore storeA = new LearnedRulesStore(tmp.getRoot().getAbsolutePath(), "account-a");
    LearnedRulesStore storeB = new LearnedRulesStore(tmp.getRoot().getAbsolutePath(), "account-b");

    storeA.addIfAbsent(rule("alice@example.com", "Spam"));

    assertEquals(1, storeA.load().size());
    assertTrue(storeB.load().isEmpty());
  }
}
