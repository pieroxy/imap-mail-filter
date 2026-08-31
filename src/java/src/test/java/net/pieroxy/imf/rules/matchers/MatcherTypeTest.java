package net.pieroxy.imf.rules.matchers;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MatcherTypeTest {

  @Test
  public void fromMatchersAreLearnable() {
    assertTrue(MatcherType.learnableValues().contains(MatcherType.FROM_EQUALS));
    assertTrue(MatcherType.learnableValues().contains(MatcherType.FROM_ADDRESS_EQUALS));
    assertTrue(MatcherType.learnableValues().contains(MatcherType.FROM_DOMAIN_EQUALS));
    assertTrue(MatcherType.learnableValues().contains(MatcherType.SUBJECT_STARTS_WITH));
  }

  @Test
  public void compositeTypesAreNotLearnable() {
    assertFalse(MatcherType.learnableValues().contains(MatcherType.AND));
    assertFalse(MatcherType.learnableValues().contains(MatcherType.OR));
  }

  /**
   * SPF/DKIM/DMARC ne sont pas learnable : leurs clés possibles (pass/fail/softfail/...) sont
   * un ensemble fixe et déjà documenté, pas une valeur spécifique à découvrir par l'exemple —
   * et contrairement à FROM_*, la règle apprise ne serait pas propre à l'exemple déposé, elle
   * s'appliquerait globalement à tout message ayant le même statut.
   */
  @Test
  public void protocolResultMatchersAreNotLearnable() {
    assertFalse(MatcherType.learnableValues().contains(MatcherType.SPF_RESULT_EQUALS));
    assertFalse(MatcherType.learnableValues().contains(MatcherType.DKIM_RESULT_EQUALS));
    assertFalse(MatcherType.learnableValues().contains(MatcherType.DMARC_RESULT_EQUALS));
    assertFalse(MatcherType.learnableValues().contains(MatcherType.FCRDNS_RESULT_EQUALS));
  }

  /**
   * Pas learnable par dépôt d'exemple non plus, mais pour une raison différente des matchers
   * de protocole ci-dessus : l'apprentissage vient du corpus classifieur (voir
   * SubjectClassifierTrainer), pas des dossiers imf-rules/.
   */
  @Test
  public void subjectClassifierIsNotLearnable() {
    assertFalse(MatcherType.learnableValues().contains(MatcherType.SUBJECT_CLASSIFIER_EQUALS));
  }
}
