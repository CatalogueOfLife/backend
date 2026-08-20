package life.catalogue.matching;

import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.io.TempFile;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.util.List;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UsageMatcherChronicleStoreTest extends UsageMatcherStoreTestBase {
  TempFile dbFile;

  @Override
  UsageMatcherStore createStore(int datasetKey) throws IOException {
    dbFile = TempFile.directory();
    return UsageMatcherChronicleStore.build(datasetKey, dbFile.file, 1000, 1000, List.of(
      UsageMatcherChronicleStore.sample("DRFTGZH"),
      UsageMatcherChronicleStore.sample("3456G"),
      UsageMatcherChronicleStore.sample("$E$%FGZHZGU"),
      UsageMatcherChronicleStore.sample("http://urn:lsid.org/urn:lsid:ipni.org:names:77166011-1")
    ));
  }

  @Test
  public void reopenPreservesData() throws IOException {
    var dir = TempFile.directory();
    try {
      int datasetKey = 77;
      var sn = snc("abc", "xyz", "Aus bus", "Smith", Rank.SPECIES, 42, 99);
      sn.setStatus(TaxonomicStatus.ACCEPTED);

      // build and populate
      try (var store = UsageMatcherChronicleStore.build(datasetKey, dir.file, 100, 100, List.of(sn))) {
        store.add(sn);
        assertEquals(1, store.size());
      }

      // reopen without any DB-derived params — data must still be present
      try (var store = UsageMatcherChronicleStore.reopen(datasetKey, dir.file)) {
        assertEquals(1, store.size());
        assertEquals(sn, store.get(sn.getId()));
      }
    } finally {
      dir.close();
    }
  }

  /**
   * The canonical inverted index must not be larger than the usages data it indexes.
   * Regression for over-allocating the canonical map with the total usage count and a 5-element
   * average value, which made it ~2x the usages file on disk.
   */
  @Test
  public void canonicalNotLargerThanUsages() throws IOException {
    var dir = TempFile.directory();
    try {
      int datasetKey = 78;
      long count = 100_000;
      long canonCount = 80_000; // fewer distinct canonical ids than usages, as in real data
      try (var store = UsageMatcherChronicleStore.build(datasetKey, dir.file, count, canonCount,
        List.of(UsageMatcherChronicleStore.sample("DRFTGZH")))) {
        // nothing to add - we only inspect the pre-allocated file sizes
      }
      long usagesSize = new java.io.File(dir.file, "usages").length();
      long canonicalSize = new java.io.File(dir.file, "canonical").length();
      assertTrue("canonical (" + canonicalSize + ") must not exceed usages (" + usagesSize + ")",
        canonicalSize <= usagesSize);
    } finally {
      dir.close();
    }
  }

  /**
   * The store must survive a load whose usages are bigger than the samples it was sized from.
   * listSN() orders by id, so the samples handed to build() are the textually first usages of the
   * dataset - in a dataset mixing short numeric ids with long LSIDs those are the shortest ones.
   * Regression for prod matcher builds dying mid-load with
   * "Attempt to allocate #n extra segment tier, m is maximum".
   */
  @Test
  public void loadWithUnrepresentativeSamples() throws IOException {
    var dir = TempFile.directory();
    try {
      final int count = 20_000;
      final int canon = 16_000;
      // the one sample the sizing sees has a 1 char id, the real usages carry 40+ char LSIDs
      try (var store = UsageMatcherChronicleStore.build(79, dir.file, count + 1, canon + canon / 100,
             List.of(UsageMatcherChronicleStore.sample("1")))) {
        for (int i = 0; i < count; i++) {
          var sn = snc("urn:lsid:marinespecies.org:taxname:" + (100000 + i), "urn:lsid:marinespecies.org:taxname:1",
            "Abies alba", "Miller, 1988", Rank.SPECIES, 1 + (i % canon), 1 + (i % canon));
          sn.setStatus(TaxonomicStatus.ACCEPTED);
          store.add(sn);
        }
        assertEquals(count, store.size());
      }
    } finally {
      dir.close();
    }
  }

  @After
  public void destroy() {
    if (dbFile != null) dbFile.close();
  }
}