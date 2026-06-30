package life.catalogue.matching;

import life.catalogue.matching.nidx.NameIndex;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Reference-counting of shared (persistent) matcher stores: a store retired by a rebuild/eviction must stay
 * open until the last consumer that holds it (e.g. a long-running MatchingJob) releases it.
 */
public class UsageMatcherLeaseTest {

  @Test
  public void sharedStoreClosedOnlyAfterLastConsumerReleasesPostRetire() {
    UsageMatcherStore store = mock(UsageMatcherStore.class);
    var m = new UsageMatcher(1, mock(NameIndex.class), store, true); // shared/persistent

    assertTrue(m.tryAcquire());        // consumer A (e.g. an HTTP match)
    assertTrue(m.tryAcquire());        // consumer B (e.g. a long-running MatchingJob)
    m.retire();                        // swapped out by a concurrent rebuild
    verify(store, never()).close();    // still in use → not closed

    m.close();                         // A releases
    verify(store, never()).close();    // B still holds it
    m.close();                         // B releases → now safe to unmap
    verify(store, times(1)).close();

    assertFalse(m.tryAcquire());       // a late acquire after close fails → caller re-resolves the matcher
  }

  @Test
  public void retiringIdleSharedStoreClosesImmediately() {
    UsageMatcherStore store = mock(UsageMatcherStore.class);
    var m = new UsageMatcher(1, mock(NameIndex.class), store, true);
    m.retire();                        // no consumer holds it
    verify(store, times(1)).close();
  }

  /**
   * Hammers a lock-free acquirer against a concurrent retire on the same instance: a successful tryAcquire
   * must never hand back a store that has already been closed (the use-after-close race), and the store must
   * be closed exactly once. Deterministically passes with the synchronized handshake; a broken handshake
   * (read leases before publishing closed) would let the acquirer observe a closed store.
   */
  @Test
  public void concurrentAcquireNeverObservesClosedStore() throws Exception {
    for (int i = 0; i < 5000; i++) {
      UsageMatcherStore store = mock(UsageMatcherStore.class);
      AtomicBoolean storeClosed = new AtomicBoolean(false);
      doAnswer(inv -> { storeClosed.set(true); return null; }).when(store).close();
      var m = new UsageMatcher(1, mock(NameIndex.class), store, true);
      AtomicReference<Throwable> err = new AtomicReference<>();

      Thread acquirer = new Thread(() -> {
        try {
          if (m.tryAcquire()) {
            if (storeClosed.get()) {
              err.set(new AssertionError("tryAcquire returned a CLOSED store"));
            }
            m.close();
          }
        } catch (Throwable t) {
          err.set(t);
        }
      });
      Thread retirer = new Thread(m::retire);
      acquirer.start(); retirer.start();
      acquirer.join(); retirer.join();

      if (err.get() != null) throw new AssertionError("iteration " + i, err.get());
      verify(store, times(1)).close(); // closed exactly once: either idle-retire or after the acquirer released
    }
  }

  @Test
  public void callerOwnedStoreClosedOnClose() {
    UsageMatcherStore store = mock(UsageMatcherStore.class);
    var m = new UsageMatcher(1, mock(NameIndex.class), store, false); // postgres / in-memory style
    m.close();
    verify(store, times(1)).close();
  }
}
