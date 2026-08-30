package net.pieroxy.imf.rules;

import net.pieroxy.imf.config.MailFilterRuleActionConfiguration;
import net.pieroxy.imf.config.MailFilterRuleConfiguration;
import net.pieroxy.imf.config.MailFilterRuleMatcherConfiguration;
import net.pieroxy.imf.learning.LearnedRulesStore;
import net.pieroxy.imf.rules.actions.ActionType;
import net.pieroxy.imf.rules.matchers.MatcherType;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class RuleCatalogTest {

  @org.junit.Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private final Session session = Session.getDefaultInstance(new Properties());

  private static MailFilterRuleConfiguration rule(String fromKey) {
    MailFilterRuleMatcherConfiguration matcher = new MailFilterRuleMatcherConfiguration();
    matcher.setType(MatcherType.FROM_ADDRESS_EQUALS);
    matcher.setKey(fromKey);

    MailFilterRuleActionConfiguration action = new MailFilterRuleActionConfiguration();
    action.setType(ActionType.READ);

    MailFilterRuleConfiguration r = new MailFilterRuleConfiguration();
    r.setMatcher(matcher);
    r.setAction(action);
    return r;
  }

  @Test
  public void combinesManualAndLearnedRules() throws Exception {
    LearnedRulesStore store = new LearnedRulesStore(tmp.getRoot().getAbsolutePath(), "account");
    store.addIfAbsent(rule("learned@example.com"));
    RuleCatalog catalog = new RuleCatalog(Arrays.asList(rule("manual@example.com")), store);

    List<Rule> rules = catalog.get();

    assertEquals(2, rules.size());
    MimeMessage manualMatch = new MimeMessage(session);
    manualMatch.setFrom(new InternetAddress("manual@example.com"));
    MimeMessage learnedMatch = new MimeMessage(session);
    learnedMatch.setFrom(new InternetAddress("learned@example.com"));

    assertTrue(rules.stream().anyMatch(r -> r.apply(manualMatch)));
    assertTrue(rules.stream().anyMatch(r -> r.apply(learnedMatch)));
  }

  @Test
  public void cachesTheBuiltListUntilInvalidated() {
    LearnedRulesStore store = new LearnedRulesStore(tmp.getRoot().getAbsolutePath(), "account");
    RuleCatalog catalog = new RuleCatalog(null, store);

    List<Rule> first = catalog.get();
    store.addIfAbsent(rule("new@example.com")); // change externe au cache

    assertSame("get() ne doit pas relire tant qu'on n'a pas invalidé", first, catalog.get());
  }

  @Test
  public void rebuildsAfterInvalidate() {
    LearnedRulesStore store = new LearnedRulesStore(tmp.getRoot().getAbsolutePath(), "account");
    RuleCatalog catalog = new RuleCatalog(null, store);

    assertTrue(catalog.get().isEmpty());
    store.addIfAbsent(rule("new@example.com"));
    catalog.invalidate();

    assertEquals(1, catalog.get().size());
  }
}
