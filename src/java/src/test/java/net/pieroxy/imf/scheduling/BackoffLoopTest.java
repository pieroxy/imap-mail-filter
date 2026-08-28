package net.pieroxy.imf.scheduling;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BackoffLoopTest {

  @Test
  public void stopsPromptlyWhenInterruptedBeforeFirstRun() throws InterruptedException {
    AtomicInteger callCount = new AtomicInteger();
    BackoffLoop loop = new BackoffLoop(10_000, 60_000); // délai initial volontairement énorme

    Thread t = new Thread(() -> loop.run("test", callCount::incrementAndGet));
    t.start();
    t.interrupt();
    t.join(2000);

    assertFalse("le thread doit s'être arrêté", t.isAlive());
    assertEquals("la tâche ne doit jamais avoir tourné", 0, callCount.get());
  }

  @Test
  public void delayGrowsOnFailureAndResetsOnSuccess() throws InterruptedException {
    List<Long> timestamps = Collections.synchronizedList(new ArrayList<>());
    AtomicInteger callCount = new AtomicInteger();
    BackoffLoop loop = new BackoffLoop(50, 2000);

    Thread t = new Thread(() -> loop.run("test", () -> {
      int n = callCount.incrementAndGet();
      timestamps.add(System.currentTimeMillis());
      if (n <= 2) {
        throw new RuntimeException("boom");
      }
      if (n >= 4) {
        Thread.currentThread().interrupt();
      }
    }));
    t.start();
    t.join(5000);

    assertFalse("le thread doit s'être arrêté de lui-même", t.isAlive());
    assertEquals(4, callCount.get());

    long gapAfterFirstFailure = timestamps.get(1) - timestamps.get(0);   // délai initial (50ms)
    long gapAfterSecondFailure = timestamps.get(2) - timestamps.get(1); // délai doublé (100ms)
    long gapAfterSuccess = timestamps.get(3) - timestamps.get(2);       // reset au délai initial (50ms)

    assertTrue("le délai doit augmenter après un échec (" + gapAfterFirstFailure + " -> " + gapAfterSecondFailure + ")",
            gapAfterSecondFailure > gapAfterFirstFailure * 1.5);
    assertTrue("le délai doit revenir au niveau initial après un succès (" + gapAfterSecondFailure + " -> " + gapAfterSuccess + ")",
            gapAfterSuccess < gapAfterSecondFailure / 1.5);
  }
}
