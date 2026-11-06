package life.catalogue.es.indexing;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * indexDatasets adds the stats of every dataset from the indexing threads, and a failed dataset has none
 */
public class NameUsageIndexStatsTest {

  @Test
  public void concurrentAdd() throws Exception {
    final var total = new NameUsageIndexService.Stats();
    final int threads = 8;
    final int adds = 200_000;
    ExecutorService exec = Executors.newFixedThreadPool(threads);
    try {
      List<CompletableFuture<Void>> futures = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
        futures.add(CompletableFuture.runAsync(() -> {
          var one = new NameUsageIndexService.Stats();
          one.usages = 1;
          one.names = 2;
          for (int i = 0; i < adds; i++) {
            total.add(one);
          }
        }, exec));
      }
      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
    } finally {
      exec.shutdown();
    }
    assertEquals(threads * adds, total.usages);
    assertEquals(2 * threads * adds, total.names);
  }

  @Test
  public void addNothing() {
    final var total = new NameUsageIndexService.Stats();
    total.add(null);
    assertEquals(0, total.total());
  }
}
