package net.pieroxy.imf.rules.actions.implementations;

import net.pieroxy.imf.rules.actions.Action;

import javax.mail.Message;
import javax.mail.MessagingException;

/**
 * Exécute les actions enfants dans l'ordre, en s'arrêtant dès qu'une réussit (fallback chain).
 * Sans enfant, un OR échoue par convention.
 */
public class OrAction extends Action {
  @Override
  public boolean run(Message message) throws MessagingException {
    for (Action child : getChildren()) {
      if (child.run(message)) return true;
    }
    return false;
  }
}
