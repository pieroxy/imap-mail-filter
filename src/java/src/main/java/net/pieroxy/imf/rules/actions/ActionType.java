package net.pieroxy.imf.rules.actions;

import net.pieroxy.imf.config.MailFilterRuleActionConfiguration;
import net.pieroxy.imf.rules.actions.implementations.AndAction;
import net.pieroxy.imf.rules.actions.implementations.MoveToAction;
import net.pieroxy.imf.rules.actions.implementations.OrAction;
import net.pieroxy.imf.rules.actions.implementations.ReadAction;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum ActionType {
  MOVE_TO(MoveToAction::new, true),
  READ(ReadAction::new, false),
  /**
   * Pas de classe dédiée : c'est un AND(MOVE_TO, READ) construit à la volée, pour rester
   * apprenable (les composites eux-mêmes ne le sont pas) sans dupliquer MoveToAction/ReadAction.
   */
  MOVE_TO_AND_READ(() -> new AndAction() {
    @Override
    public void setConfig(MailFilterRuleActionConfiguration config) {
      super.setConfig(config);

      MailFilterRuleActionConfiguration moveToConfig = new MailFilterRuleActionConfiguration();
      moveToConfig.setType(MOVE_TO);
      moveToConfig.setKey(config.getKey());
      moveToConfig.setLogLevel(config.getLogLevel());

      MailFilterRuleActionConfiguration readConfig = new MailFilterRuleActionConfiguration();
      readConfig.setType(READ);
      readConfig.setLogLevel(config.getLogLevel());

      // READ avant MOVE_TO : MoveToAction copie le message avec ses flags actuels, donc \Seen
      // doit déjà être posé au moment de la copie pour se retrouver sur le message dans le
      // dossier cible (le poser après la copie n'affecterait que la source, sur le point
      // d'être supprimée).
      setChildren(Arrays.asList(Action.build(readConfig), Action.build(moveToConfig)));
    }
  }, true),
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
