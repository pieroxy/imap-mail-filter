package net.pieroxy.imf.rules.actions.implementations;

import net.pieroxy.imf.rules.actions.Action;

import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Store;

/**
 * Copies the message into the target folder (created if it doesn't exist) then marks it
 * \Deleted in the source folder; the actual deletion happens when the caller closes (expunges)
 * the source folder.
 */
public class MoveToAction extends Action {
  @Override
  public boolean run(Message message) throws MessagingException {
    Folder source = message.getFolder();
    Store store = source.getStore();
    Folder target = store.getFolder(getConfig().getKey());
    getLogger().fine(() -> "Moving message from " + source.getFullName() + " to " + getConfig().getKey());
    if (!target.exists()) {
      target.create(Folder.HOLDS_MESSAGES);
      getLogger().fine(() -> "Created missing target folder " + getConfig().getKey());
    }
    source.copyMessages(new Message[]{message}, target);
    message.setFlag(Flags.Flag.DELETED, true);
    return true;
  }
}
