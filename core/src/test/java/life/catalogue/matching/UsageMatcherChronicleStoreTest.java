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

  @After
  public void destroy() {
    if (dbFile != null) dbFile.close();
  }
}