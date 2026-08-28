package net.pieroxy.imf.rules.actions.implementations;

import net.pieroxy.imf.rules.actions.Action;

import javax.mail.Message;
import javax.mail.MessagingException;

/**
 * Exécute les actions enfants dans l'ordre, en s'arrêtant à la première qui échoue.
 * Sans enfant, un AND réussit par convention (vacuous truth).
 */
public class AndAction extends Action {
  @Override
  public boolean run(Message message) throws MessagingException {
    for (Action child : getChildren()) {
      if (!child.run(message)) return false;
    }
    return true;
  }
}
