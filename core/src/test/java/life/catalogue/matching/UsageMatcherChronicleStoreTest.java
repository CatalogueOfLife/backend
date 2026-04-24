package life.catalogue.matching;

import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.io.TempFile;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.util.List;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UsageMatcherChronicleStoreTest extends UsageMatcherStoreTestBase {
  TempFile dbFile;

  @Override
  UsageMatcherStore createStore(int datasetKey) throws IOException {
    dbFile = TempFile.directory();
    return UsageMatcherChronicleStore.build(datasetKey, dbFile.file, 1000, List.of(
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
      try (var store = UsageMatcherChronicleStore.build(datasetKey, dir.file, 100, List.of(sn))) {
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

  @After
  public void destroy() {
    if (dbFile != null) dbFile.close();
  }
}