package life.catalogue.cache;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.common.io.TempFile;
import life.catalogue.common.kryo.ApiKryoPool;

import org.apache.commons.lang3.time.StopWatch;
import org.junit.Test;

import static org.junit.Assert.*;

public class ObjectCacheTest {
  final NameUsageWrapper t = TestEntityGenerator.newNameUsageTaxonWrapper();
  final NameUsageWrapper s = TestEntityGenerator.newNameUsageSynonymWrapper();

  @Test
  public void crud() throws Exception {
    final ApiKryoPool pool = new ApiKryoPool(8);
    try (TempFile dir = new TempFile();
         ObjectCache<NameUsageWrapper> cache = new ObjectCacheMapDB<>(NameUsageWrapper.class, dir.file, pool)
    ) {
      t.setId("a");

      assertFalse(cache.contains(t.getId()));

      cache.put(t);
      assertTrue(cache.contains(t.getId()));
      assertEquals(t, cache.get(t.getId()));

      cache.remove(t.getId());
      assertFalse(cache.contains(t.getId()));
    }
  }

  void bench(int size) throws Exception {
    final ApiKryoPool pool = new ApiKryoPool(8);
    try (TempFile dir = new TempFile();
         ObjectCache<NameUsageWrapper> cache = new ObjectCacheMapDB<>(NameUsageWrapper.class, dir.file, pool)
    ) {
      var watch = StopWatch.createStarted();
      for (int i = 0; i < size; i=i+2) {
        t.setId("t"+i);
        cache.put(t);
        s.setId("s"+i);
        cache.put(s);
      }
      watch.stop();
      System.out.printf("Writing %s objects took %s\n", size, watch);

      watch.reset();
      watch.start();
      for (int i = 0; i < size; i=i+2) {
        var x = cache.get("t"+i);
        if (x == null) throw new RuntimeException("Not found: t" + i);

        x = cache.get("s"+i);
        if (x == null) throw new RuntimeException("Not found: s" + i);
      }
      watch.stop();
      System.out.printf("Reading %s objects took %s\n", size, watch);
    }
  }

  public static void main(String[] args) throws Exception {
    ObjectCacheTest test = new ObjectCacheTest();
    test.bench(10_000);
    test.bench(1_000_000);
    test.bench(10_000_000);
  }
}