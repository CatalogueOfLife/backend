package life.catalogue.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntConsumer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static life.catalogue.concurrent.DatasetLock.Mode.EXCLUSIVE;
import static life.catalogue.concurrent.DatasetLock.Mode.SHARED;
import static org.junit.Assert.*;

public class DatasetLockTest {
  final UUID a = UUID.randomUUID();
  final UUID b = UUID.randomUUID();
  final UUID c = UUID.randomUUID();
  final List<Integer> unlocked = new ArrayList<>();

  // DatasetLock is JVM wide and other tests might use it at the same time, so keep to datasets of our own
  // and only record unlocks of those
  final IntConsumer listener = k -> {
    if (k > 9000) {
      unlocked.add(k - 9000);
    }
  };

  @Before
  public void init() {
    DatasetLock.addListener(listener);
  }

  @After
  public void cleanup() {
    DatasetLock.removeListener(listener);
    for (var job : List.of(a, b, c)) {
      DatasetLock.unlockAll(job);
    }
  }

  @Test
  public void exclusive() {
    assertNull(DatasetLock.tryLock(9001, a, EXCLUSIVE));
    // re-entrant
    assertNull(DatasetLock.tryLock(9001, a, EXCLUSIVE));
    assertNull(DatasetLock.tryLock(9001, a, SHARED));
    assertEquals(a, DatasetLock.tryLock(9001, b, EXCLUSIVE));
    assertEquals(a, DatasetLock.tryLock(9001, b, SHARED));
    // other datasets are not affected
    assertNull(DatasetLock.tryLock(9002, b, EXCLUSIVE));
    assertFalse(DatasetLock.isAvailable(9001, b, SHARED));
    assertTrue(DatasetLock.isAvailable(9001, a, EXCLUSIVE));
  }

  @Test
  public void shared() {
    assertNull(DatasetLock.tryLock(9001, a, SHARED));
    assertNull(DatasetLock.tryLock(9001, b, SHARED));
    assertEquals(Set.of(a, b), DatasetLock.holders(9001));
    assertNotNull(DatasetLock.tryLock(9001, c, EXCLUSIVE));
    // no upgrade while another job shares the lock
    assertEquals(b, DatasetLock.tryLock(9001, a, EXCLUSIVE));
    assertTrue(DatasetLock.isAvailable(9001, c, SHARED));
    assertFalse(DatasetLock.isAvailable(9001, c, EXCLUSIVE));

    assertTrue(DatasetLock.unlock(9001, b));
    // a sole holder can upgrade
    assertNull(DatasetLock.tryLock(9001, a, EXCLUSIVE));
    assertEquals(EXCLUSIVE, DatasetLock.isLocked(9001).orElseThrow());
    assertEquals(a, DatasetLock.tryLock(9001, c, SHARED));
  }

  @Test
  public void unlock() {
    assertNull(DatasetLock.tryLock(9001, a, EXCLUSIVE));
    // a job that never got the lock cannot release it
    assertFalse(DatasetLock.unlock(9001, b));
    assertTrue(unlocked.isEmpty());
    assertEquals(EXCLUSIVE, DatasetLock.isLocked(9001).orElseThrow());

    assertTrue(DatasetLock.unlock(9001, a));
    assertTrue(DatasetLock.isLocked(9001).isEmpty());
    assertEquals(List.of(1), unlocked);
    assertFalse(DatasetLock.unlock(9001, a));
  }

  @Test
  public void unlockAll() {
    assertNull(DatasetLock.tryLock(9001, a, EXCLUSIVE));
    assertNull(DatasetLock.tryLock(9002, a, SHARED));
    assertNull(DatasetLock.tryLock(9002, b, SHARED));
    assertNull(DatasetLock.tryLock(9003, b, SHARED));

    assertEquals(Set.of(9001, 9002), DatasetLock.unlockAll(a));
    assertTrue(DatasetLock.isLocked(9001).isEmpty());
    assertEquals(Set.of(b), DatasetLock.holders(9002));
    assertEquals(Set.of(b), DatasetLock.holders(9003));
    assertEquals(Set.of(1, 2), Set.copyOf(unlocked));
  }
}
