package net.pieroxy.imf.rules.actions;

import javax.mail.Message;

/**
 * Doublure de test : pas d'implémentation d'Action réelle qui réussit actuellement
 * (MoveToAction est un stub qui renvoie toujours false), donc nécessaire pour tester
 * le court-circuit de AndAction/OrAction.
 */
class StubAction extends Action {
  private final boolean result;
  int callCount = 0;

  StubAction(boolean result) {
    this.result = result;
  }

  @Override
  public boolean run(Message message) {
    callCount++;
    return result;
  }
}
