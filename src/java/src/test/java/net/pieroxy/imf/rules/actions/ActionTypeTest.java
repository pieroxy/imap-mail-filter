package net.pieroxy.imf.rules.actions;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ActionTypeTest {

  @Test
  public void onlyLeafTypesAreLearnable() {
    assertTrue(ActionType.learnableValues().contains(ActionType.MOVE_TO));
    assertFalse(ActionType.learnableValues().contains(ActionType.AND));
    assertFalse(ActionType.learnableValues().contains(ActionType.OR));
  }
}
