package net.pieroxy.imf.rules.actions;

import net.pieroxy.imf.rules.actions.implementations.MoveToAction;

public enum ActionType {
  MOVE_TO(MoveToAction::new);

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
