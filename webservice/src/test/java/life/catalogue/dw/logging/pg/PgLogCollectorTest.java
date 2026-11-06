package life.catalogue.dw.logging.pg;

import life.catalogue.api.model.ApiLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PgLogCollectorTest {

  /**
   * Collects persisted batches instead of writing them to the db, synchronously.
   */
  static class RecordingCollector extends PgLogCollector {
    final ConcurrentLinkedQueue<List<ApiLog>> batches = new ConcurrentLinkedQueue<>();

    RecordingCollector(PgLogConfig cfg) {
      super(cfg);
    }

    @Override
    void persistAsync(List<ApiLog> logs) {
      persist(logs);
    }

    @Override
    void persist(List<ApiLog> logs) {
      if (!logs.isEmpty()) {
        batches.add(logs);
      }
    }
  }

  /**
   * Every log added by concurrent request threads is persisted exactly once, in batches of maxSize.
   */
  @Test
  public void concurrentAdds() throws Exception {
    var cfg = new PgLogConfig();
    cfg.maxSize = 100;
    cfg.maxTime = 0; // only the size matters
    var coll = new RecordingCollector(cfg);

    final int threads = 16;
    final int perThread = 5_000;
    final var start = new CountDownLatch(1);
    final var error = new AtomicReference<Throwable>();
    List<Thread> workers = new ArrayList<>();
    for (int t = 0; t < threads; t++) {
      var w = new Thread(() -> {
        try {
          start.await();
          for (int i = 0; i < perThread; i++) {
            coll.add(new ApiLog());
          }
        } catch (Throwable e) {
          error.compareAndSet(null, e);
        }
      });
      w.start();
      workers.add(w);
    }
    start.countDown();
    for (var w : workers) {
      w.join(30_000);
    }
    assertNull(error.get());
    coll.close();

    // every batch is persisted once and is full, nothing is lost or duplicated
    Set<ApiLog> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    int total = 0;
    for (var batch : coll.batches) {
      assertEquals(cfg.maxSize, batch.size());
      for (var log : batch) {
        assertTrue("log persisted twice", seen.add(log));
      }
      total += batch.size();
    }
    assertEquals(threads * perThread, total);
    assertEquals(0, coll.size());
  }

  @Test
  public void closePersistsTheRest() throws Exception {
    var cfg = new PgLogConfig();
    cfg.maxSize = 100;
    var coll = new RecordingCollector(cfg);
    for (int i = 0; i < 150; i++) {
      coll.add(new ApiLog());
    }
    assertEquals(1, coll.batches.size());
    assertEquals(50, coll.size());
    coll.close();
    assertEquals(2, coll.batches.size());
    assertEquals(0, coll.size());
  }
}
