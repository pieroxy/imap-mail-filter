package net.pieroxy.imf.reputation;

import net.pieroxy.imf.config.ReputationListConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReputationRegistryTest {

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  private ReputationListConfig list(String id, ReputationListType type, double score) {
    ReputationListConfig cfg = new ReputationListConfig();
    cfg.setId(id);
    cfg.setType(type);
    cfg.setUrl("file:///unused-in-this-test");
    cfg.setRefreshHours(24);
    cfg.setScore(score);
    return cfg;
  }

  @Test
  public void loadsFromDiskCacheAtConstructionTimeWithoutNetwork() throws Exception {
    String dataFolder = tempFolder.getRoot().getAbsolutePath();
    new ReputationListStore(dataFolder).save("blocklist", "1.2.3.0/24\n");

    ReputationRegistry registry = new ReputationRegistry(
        List.of(list("blocklist", ReputationListType.IP_CIDR, 1.0)), dataFolder);

    OptionalDouble score = registry.ipScore("1.2.3.4", Set.of("blocklist"));
    assertTrue(score.isPresent());
    assertEquals(1.0, score.getAsDouble(), 0.0001);
  }

  @Test
  public void unmatchedValueYieldsEmptyScore() throws Exception {
    String dataFolder = tempFolder.getRoot().getAbsolutePath();
    new ReputationListStore(dataFolder).save("blocklist", "1.2.3.0/24\n");

    ReputationRegistry registry = new ReputationRegistry(
        List.of(list("blocklist", ReputationListType.IP_CIDR, 1.0)), dataFolder);

    assertFalse(registry.ipScore("9.9.9.9", Set.of("blocklist")).isPresent());
  }

  @Test
  public void worstScoreWinsAcrossMultipleReferencedLists() throws Exception {
    String dataFolder = tempFolder.getRoot().getAbsolutePath();
    ReputationListStore store = new ReputationListStore(dataFolder);
    store.save("mild", "1.2.3.0/24\n");
    store.save("severe", "1.2.3.0/24\n");

    ReputationRegistry registry = new ReputationRegistry(List.of(
        list("mild", ReputationListType.IP_CIDR, 0.3),
        list("severe", ReputationListType.IP_CIDR, 0.9)), dataFolder);

    OptionalDouble score = registry.ipScore("1.2.3.4", Set.of("mild", "severe"));
    assertEquals(0.9, score.getAsDouble(), 0.0001);
  }

  @Test
  public void unknownListIdIsIgnoredRatherThanFailing() throws Exception {
    String dataFolder = tempFolder.getRoot().getAbsolutePath();
    new ReputationListStore(dataFolder).save("blocklist", "1.2.3.0/24\n");

    ReputationRegistry registry = new ReputationRegistry(
        List.of(list("blocklist", ReputationListType.IP_CIDR, 1.0)), dataFolder);

    assertFalse(registry.ipScore("1.2.3.4", Set.of("does-not-exist")).isPresent());
  }

  @Test
  public void wrongTypeReferenceIsIgnoredRatherThanFailing() throws Exception {
    String dataFolder = tempFolder.getRoot().getAbsolutePath();
    new ReputationListStore(dataFolder).save("domain-list", "bad.example.com\n");

    ReputationRegistry registry = new ReputationRegistry(
        List.of(list("domain-list", ReputationListType.DOMAIN, 1.0)), dataFolder);

    // domain-list est de type DOMAIN : la référencer depuis ipScore() ne doit jamais planter.
    assertFalse(registry.ipScore("1.2.3.4", Set.of("domain-list")).isPresent());
  }

  @Test
  public void domainScoreMatchesExactDomainCaseInsensitively() throws Exception {
    String dataFolder = tempFolder.getRoot().getAbsolutePath();
    new ReputationListStore(dataFolder).save("domain-list", "bad.example.com\n");

    ReputationRegistry registry = new ReputationRegistry(
        List.of(list("domain-list", ReputationListType.DOMAIN, 0.8)), dataFolder);

    OptionalDouble score = registry.domainScore("Bad.Example.com", Set.of("domain-list"));
    assertEquals(0.8, score.getAsDouble(), 0.0001);
  }

  @Test
  public void emptyRegistryAlwaysYieldsEmptyScore() {
    ReputationRegistry registry = ReputationRegistry.empty();
    assertFalse(registry.ipScore("1.2.3.4", Set.of("anything")).isPresent());
  }

  @Test
  public void noPriorCacheYieldsEmptyScoreUntilFirstRefresh() {
    String dataFolder = tempFolder.getRoot().getAbsolutePath();
    ReputationRegistry registry = new ReputationRegistry(
        List.of(list("blocklist", ReputationListType.IP_CIDR, 1.0)), dataFolder);

    assertFalse(registry.ipScore("1.2.3.4", Set.of("blocklist")).isPresent());
  }

  // --- initialDelayMs(...) : ce qui empêche un service en crash loop de retélécharger à
  // chaque redémarrage (voir start()) ---

  @Test
  public void noCacheAtAllMeansDownloadRightAway() {
    assertEquals(0L, ReputationRegistry.initialDelayMs(0, System.currentTimeMillis(), TimeUnit.HOURS.toMillis(24)));
  }

  @Test
  public void freshCacheDefersTheFirstDownload() {
    long now = System.currentTimeMillis();
    long oneHourAgo = now - TimeUnit.HOURS.toMillis(1);
    long refreshMs = TimeUnit.HOURS.toMillis(24);

    long delay = ReputationRegistry.initialDelayMs(oneHourAgo, now, refreshMs);

    assertEquals(refreshMs - TimeUnit.HOURS.toMillis(1), delay);
  }

  @Test
  public void staleCacheMeansDownloadRightAway() {
    long now = System.currentTimeMillis();
    long thirtyHoursAgo = now - TimeUnit.HOURS.toMillis(30);

    assertEquals(0L, ReputationRegistry.initialDelayMs(thirtyHoursAgo, now, TimeUnit.HOURS.toMillis(24)));
  }

  // --- start() de bout en bout : le comportement décrit ci-dessus tient vraiment sur un
  // registre réel, pas seulement dans le calcul isolé ---

  @Test
  public void startDoesNotRedownloadAFreshCache() throws Exception {
    String dataFolder = tempFolder.getRoot().getAbsolutePath();
    File sourceFile = tempFolder.newFile("source.txt");
    Files.writeString(sourceFile.toPath(), "9.9.9.0/24\n"); // différent du cache : prouve qu'il n'est pas relu

    new ReputationListStore(dataFolder).save("blocklist", "1.2.3.0/24\n"); // cache tout frais (mtime = maintenant)

    ReputationListConfig cfg = list("blocklist", ReputationListType.IP_CIDR, 1.0);
    cfg.setUrl(sourceFile.toURI().toString());
    cfg.setRefreshHours(24);

    ReputationRegistry registry = new ReputationRegistry(List.of(cfg), dataFolder);
    registry.start();
    try {
      Thread.sleep(300);
      assertTrue("le cache frais doit rester utilisé", registry.ipScore("1.2.3.4", Set.of("blocklist")).isPresent());
      assertFalse("la source ne doit pas avoir été retéléchargée", registry.ipScore("9.9.9.9", Set.of("blocklist")).isPresent());
    } finally {
      registry.stop();
    }
  }

  @Test
  public void startRedownloadsAStaleCacheRightAway() throws Exception {
    String dataFolder = tempFolder.getRoot().getAbsolutePath();
    File sourceFile = tempFolder.newFile("source.txt");
    Files.writeString(sourceFile.toPath(), "9.9.9.0/24\n");

    ReputationListStore store = new ReputationListStore(dataFolder);
    store.save("blocklist", "1.2.3.0/24\n");
    File cacheFile = new File(new File(dataFolder, "reputation"), "blocklist.txt.lz4");
    assertTrue(cacheFile.setLastModified(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(30)));

    ReputationListConfig cfg = list("blocklist", ReputationListType.IP_CIDR, 1.0);
    cfg.setUrl(sourceFile.toURI().toString());
    cfg.setRefreshHours(24);

    ReputationRegistry registry = new ReputationRegistry(List.of(cfg), dataFolder);
    registry.start();
    try {
      Thread.sleep(300);
      assertTrue("un cache périmé doit être retéléchargé dès le démarrage",
          registry.ipScore("9.9.9.9", Set.of("blocklist")).isPresent());
    } finally {
      registry.stop();
    }
  }
}
