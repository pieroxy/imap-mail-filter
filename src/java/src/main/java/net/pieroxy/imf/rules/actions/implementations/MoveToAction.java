package net.pieroxy.imf.rules.actions.implementations;

import net.pieroxy.imf.rules.actions.Action;

import javax.mail.Message;
import javax.mail.MessagingException;

public class MoveToAction extends Action {
  @Override
  public boolean run(Message message) throws MessagingException {
    return false;
  }
}
