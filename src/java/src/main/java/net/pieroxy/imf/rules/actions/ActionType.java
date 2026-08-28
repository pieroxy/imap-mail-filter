package net.pieroxy.imf.rules.actions;

import net.pieroxy.imf.rules.actions.implementations.AndAction;
import net.pieroxy.imf.rules.actions.implementations.MoveToAction;
import net.pieroxy.imf.rules.actions.implementations.OrAction;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum ActionType {
  MOVE_TO(MoveToAction::new, true),
  AND(AndAction::new, false),
  OR(OrAction::new, false);

  private final ActionProvider provider;
  private final boolean learnable;

  ActionType(ActionProvider provider, boolean learnable) {
    this.provider = provider;
    this.learnable = learnable;
  }

  public Action getImplementation() {
    return provider.getAction();
  }

  /**
   * Types "feuille" pour lesquels l'apprentissage de règle par l'exemple (dossiers imf-rules/)
   * a un sens. Les composites (AND/OR) en sont exclus : réservés à la config manuelle.
   */
  public static List<ActionType> learnableValues() {
    return Arrays.stream(values()).filter(t -> t.learnable).collect(Collectors.toList());
  }
}

interface ActionProvider {
  Action getAction();
}
