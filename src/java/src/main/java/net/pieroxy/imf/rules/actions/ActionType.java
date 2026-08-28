package net.pieroxy.imf.rules.actions;

import net.pieroxy.imf.rules.actions.implementations.AndAction;
import net.pieroxy.imf.rules.actions.implementations.MoveToAction;
import net.pieroxy.imf.rules.actions.implementations.OrAction;

public enum ActionType {
  MOVE_TO(MoveToAction::new),
  AND(AndAction::new),
  OR(OrAction::new);

  private final ActionProvider provider;
  ActionType(ActionProvider provider) {
    this.provider = provider;
  }

  public Action getImplementation() {
    return provider.getAction();
  }
}

interface ActionProvider {
  Action getAction();
}
