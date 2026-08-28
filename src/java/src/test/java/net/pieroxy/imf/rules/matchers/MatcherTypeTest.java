package net.pieroxy.imf.rules.matchers;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MatcherTypeTest {

  @Test
  public void onlyLeafTypesAreLearnable() {
    assertTrue(MatcherType.learnableValues().contains(MatcherType.FROM_EQUALS));
    assertFalse(MatcherType.learnableValues().contains(MatcherType.AND));
    assertFalse(MatcherType.learnableValues().contains(MatcherType.OR));
  }
}
