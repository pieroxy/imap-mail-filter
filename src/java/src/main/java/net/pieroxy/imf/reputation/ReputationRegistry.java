package net.pieroxy.imf.reputation;

import net.pieroxy.imf.config.ReputationListConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Point d'accès partagé par tout le process (pas par compte, contrairement au classifieur) aux
 * listes de réputation configurées. Une liste est téléchargée et rafraîchie dès lors qu'elle
 * est présente dans {@code reputationLists}, indépendamment de tout matcher qui la référence :
 * plus simple à raisonner ("configuré = à jour"), et permet de pré-charger une liste avant de
 * câbler une règle dessus. Jamais de requête par message — tout est en mémoire, voir
 * {@link IpReputationList}/{@link DomainReputationList}.
 * <p>
 * Utilisée en base de deux façons : construite (et son cache disque préchargé) tout de suite au
 * démarrage du process pour qu'un signal soit disponible dès le premier message, puis
 * {@link #start()} lance le rafraîchissement périodique en tâche de fond. Voir
 * {@code net.pieroxy.imf.standalone.Runner} et {@link ReputationRegistryHolder} (comment les
 * matchers, construits sans contexte, retrouvent l'instance).
 */
public final class ReputationRegistry {
  private static final Logger LOGGER = Logger.getLogger(ReputationRegistry.class.getName());

  private final Map<String, ReputationListConfig> configsById;
  private final ConcurrentHashMap<String, ReputationList> listsById = new ConcurrentHashMap<>();
  private final ReputationListStore store;
  private ScheduledExecutorService scheduler;

  public ReputationRegistry(List<ReputationListConfig> lists, String dataFolder) {
    Map<String, ReputationListConfig> configs = new HashMap<>();
    if (lists != null) {
      for (ReputationListConfig cfg : lists) {
        configs.put(cfg.getId(), cfg);
      }
    }
    this.configsById = Map.copyOf(configs);
    this.store = configsById.isEmpty() ? null : new ReputationListStore(dataFolder);

    for (ReputationListConfig cfg : configsById.values()) {
      long start = System.nanoTime();
      String cached = store.load(cfg.getId());
      if (cached == null) continue;
      try {
        ReputationListParser.ParseResult result = ReputationListParser.parse(cfg.getId(), cfg.getType(), cached);
        listsById.put(cfg.getId(), result.list());
        LOGGER.info("Reputation list [" + cfg.getId() + "]: loaded " + result.validCount() + " valid / "
            + result.invalidCount() + " invalid entries from disk cache in " + elapsedMs(start) + "ms");
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Reputation list [" + cfg.getId() + "]: could not parse cached copy", e);
      }
    }
  }

  /** Registre sans aucune liste configurée : ipScore/domainScore renvoient toujours vide. */
  public static ReputationRegistry empty() {
    return new ReputationRegistry(List.of(), null);
  }

  /**
   * Démarre le rafraîchissement périodique (une tâche par liste, à son propre refreshHours).
   * Sans effet si aucune liste n'est configurée, ou déjà démarré.
   * <p>
   * Le premier téléchargement de chaque liste est différé jusqu'à ce que son cache disque soit
   * effectivement dû pour un refresh (âge du fichier &ge; refreshHours), pas déclenché
   * systématiquement au démarrage du process : sinon, un service qui redémarre en boucle
   * (crash loop, mauvaise config...) retéléchargerait à chaque redémarrage, potentiellement
   * bien plus vite que refreshHours, jusqu'à se faire bannir de la source distante — exactement
   * ce que refreshHours est censé éviter.
   */
  public synchronized void start() {
    if (configsById.isEmpty() || scheduler != null) return;
    scheduler = Executors.newScheduledThreadPool(1, r -> {
      Thread t = new Thread(r, "reputation-refresh");
      t.setDaemon(true);
      return t;
    });
    long now = System.currentTimeMillis();
    for (ReputationListConfig cfg : configsById.values()) {
      long refreshMs = TimeUnit.HOURS.toMillis(Math.max(1, cfg.getRefreshHours()));
      long initialDelayMs = initialDelayMs(store.lastModified(cfg.getId()), now, refreshMs);
      if (initialDelayMs > 0) {
        LOGGER.info("Reputation list [" + cfg.getId() + "]: cached copy is " + formatDuration(refreshMs - initialDelayMs)
            + " old, next download in " + formatDuration(initialDelayMs));
      }
      scheduler.scheduleAtFixedRate(() -> refresh(cfg), initialDelayMs, refreshMs, TimeUnit.MILLISECONDS);
    }
  }

  /** @return le délai avant le prochain téléchargement dû : 0 si pas de cache, ou si son âge dépasse déjà refreshMs. */
  static long initialDelayMs(long cacheLastModifiedMillis, long nowMillis, long refreshMs) {
    if (cacheLastModifiedMillis <= 0) return 0;
    long age = nowMillis - cacheLastModifiedMillis;
    return age >= refreshMs ? 0 : refreshMs - age;
  }

  private static String formatDuration(long ms) {
    long minutes = TimeUnit.MILLISECONDS.toMinutes(ms);
    return minutes < 60 ? minutes + "min" : TimeUnit.MILLISECONDS.toHours(ms) + "h";
  }

  public synchronized void stop() {
    if (scheduler != null) {
      scheduler.shutdownNow();
      scheduler = null;
    }
  }

  private void refresh(ReputationListConfig cfg) {
    long overallStart = System.nanoTime();
    try {
      long fetchStart = System.nanoTime();
      String content = ReputationListFetcher.fetch(cfg.getUrl());
      long fetchMs = elapsedMs(fetchStart);

      long parseStart = System.nanoTime();
      ReputationListParser.ParseResult result = ReputationListParser.parse(cfg.getId(), cfg.getType(), content);
      long parseMs = elapsedMs(parseStart);

      listsById.put(cfg.getId(), result.list());
      store.save(cfg.getId(), content);

      LOGGER.info("Reputation list [" + cfg.getId() + "] refreshed from " + cfg.getUrl() + ": "
          + result.validCount() + " valid / " + result.invalidCount() + " invalid entries "
          + "(fetch " + fetchMs + "ms, parse " + parseMs + "ms, total " + elapsedMs(overallStart) + "ms)");
    } catch (Exception e) {
      // On garde la dernière version qui a marché (cache disque déjà chargé au démarrage, ou
      // précédent succès en mémoire) : un refresh raté ne doit jamais vider le signal existant.
      LOGGER.log(Level.WARNING, "Reputation list [" + cfg.getId() + "]: refresh failed after "
          + elapsedMs(overallStart) + "ms, keeping last known copy", e);
    }
  }

  private static long elapsedMs(long startNanos) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
  }

  /** @return le pire (max) score parmi les listes IP_CIDR de listIds qui contiennent ip ; vide si aucune ne matche. */
  public OptionalDouble ipScore(String ip, Set<String> listIds) {
    return score(ReputationListType.IP_CIDR, ip, listIds);
  }

  /** @return le pire (max) score parmi les listes DOMAIN de listIds qui contiennent domain ; vide si aucune ne matche. */
  public OptionalDouble domainScore(String domain, Set<String> listIds) {
    return score(ReputationListType.DOMAIN, domain, listIds);
  }

  private OptionalDouble score(ReputationListType expectedType, String value, Set<String> listIds) {
    double best = Double.NaN;
    for (String id : listIds) {
      ReputationListConfig cfg = configsById.get(id);
      if (cfg == null) {
        LOGGER.warning("Reputation list [" + id + "] referenced by a matcher but not declared in reputationLists");
        continue;
      }
      if (cfg.getType() != expectedType) {
        LOGGER.warning("Reputation list [" + id + "] is of type " + cfg.getType() + ", not usable from this matcher");
        continue;
      }
      ReputationList list = listsById.get(id);
      if (list == null || !list.contains(value)) continue;
      if (Double.isNaN(best) || cfg.getScore() > best) {
        best = cfg.getScore();
      }
    }
    return Double.isNaN(best) ? OptionalDouble.empty() : OptionalDouble.of(best);
  }
}
