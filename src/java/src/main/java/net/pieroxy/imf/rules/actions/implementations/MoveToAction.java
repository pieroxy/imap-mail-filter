package net.pieroxy.imf.rules.actions.implementations;

import net.pieroxy.imf.rules.actions.Action;

import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Store;

/**
 * Copie le message dans le dossier cible (créé s'il n'existe pas) puis le marque \Deleted
 * dans le dossier source ; la suppression effective a lieu à la fermeture (expunge) du
 * dossier source par l'appelant.
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
