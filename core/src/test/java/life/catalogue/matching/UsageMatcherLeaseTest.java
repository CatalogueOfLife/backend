package life.catalogue.matching;

import life.catalogue.matching.nidx.NameIndex;

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

  @Test
  public void callerOwnedStoreClosedOnClose() {
    UsageMatcherStore store = mock(UsageMatcherStore.class);
    var m = new UsageMatcher(1, mock(NameIndex.class), store, false); // postgres / in-memory style
    m.close();
    verify(store, times(1)).close();
  }
}
