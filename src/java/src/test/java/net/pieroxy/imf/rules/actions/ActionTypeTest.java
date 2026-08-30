package net.pieroxy.imf.rules.actions;

import net.pieroxy.imf.config.MailFilterRuleActionConfiguration;
import net.pieroxy.imf.rules.actions.implementations.AndAction;
import net.pieroxy.imf.rules.actions.implementations.MoveToAction;
import net.pieroxy.imf.rules.actions.implementations.ReadAction;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ActionTypeTest {

  @Test
  public void onlyLeafTypesAreLearnable() {
    assertTrue(ActionType.learnableValues().contains(ActionType.MOVE_TO));
    assertFalse(ActionType.learnableValues().contains(ActionType.READ));
    assertTrue(ActionType.learnableValues().contains(ActionType.MOVE_TO_AND_READ));
    assertFalse(ActionType.learnableValues().contains(ActionType.AND));
    assertFalse(ActionType.learnableValues().contains(ActionType.OR));
  }

  @Test
  public void moveToAndReadRunsReadBeforeMoveTo() {
    // Ordre important : MoveToAction copie le message avec ses flags actuels, donc READ doit
    // s'exécuter avant, pour que \Seen soit déjà posé au moment de la copie vers la cible.
    MailFilterRuleActionConfiguration config = new MailFilterRuleActionConfiguration();
    config.setType(ActionType.MOVE_TO_AND_READ);
    config.setKey("Target");

    Action action = Action.build(config);

    assertTrue(action instanceof AndAction);
    List<Action> children = action.getChildren();
    assertEquals(2, children.size());
    assertTrue("READ doit s'exécuter en premier", children.get(0) instanceof ReadAction);
    assertTrue("MOVE_TO doit s'exécuter en second", children.get(1) instanceof MoveToAction);
    assertEquals("Target", children.get(1).getConfig().getKey());
  }
}
