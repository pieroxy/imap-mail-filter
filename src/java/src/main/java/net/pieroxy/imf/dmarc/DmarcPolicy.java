package net.pieroxy.imf.dmarc;

/**
 * Politique DMARC effective pour un message donné : celle du domaine exact (tag {@code p=})
 * s'il publie son propre record, ou celle de son domaine organisationnel pour les sous-domaines
 * (tag {@code sp=}, replié sur {@code p=} si absent — RFC 7489 §6.3).
 * <p>
 * {@link #UNPUBLISHED} est délibérément distinct de {@link #NONE} : {@code p=none} veut dire
 * "le domaine a un DMARC et choisit explicitement de ne faire que surveiller", alors que
 * {@code UNPUBLISHED} veut dire "ce domaine n'a aucun DMARC" — deux situations très
 * différentes (l'absence de DMARC est la norme pour la plupart des petits domaines/particuliers
 * et n'est pas en soi suspecte, contrairement à un {@code p=none} qui est un choix actif).
 */
public enum DmarcPolicy {
  NONE,
  QUARANTINE,
  REJECT,
  UNPUBLISHED,
  PERMERROR,
  TEMPERROR;

  public String getCode() {
    return name().toLowerCase();
  }
}
