package life.catalogue.cache;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;

import life.catalogue.common.io.TempFile;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.io.File;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class UsageCacheMapDBTest {
  File dbf;
  UsageCacheMapDB cache;

  @Before
  public void init() throws Exception {
    dbf = File.createTempFile("coltest", "mapdb");
    dbf.delete(); // map db create a new one
    cache = new UsageCacheMapDB(Datasets.COL, dbf, 8);
  }

  @After
  public void destroy() throws Exception {
    cache.close();
  }

  @Test
  public void crud() throws Exception {
    final SimpleNameCached sn = new SimpleNameCached();
    sn.setId("a");
    sn.setName("Abies");
    sn.setRank(Rank.GENUS);
    sn.setStatus(TaxonomicStatus.MISAPPLIED);
    sn.setCode(NomCode.BOTANICAL);
    sn.setNamesIndexMatchType(MatchType.EXACT);

    assertFalse(cache.contains("a"));

    cache.put(sn);
    assertTrue(cache.contains("a"));
    assertEquals(sn, cache.get("a"));

    cache.put(sn);
    cache.remove("a");
    assertFalse(cache.contains("a"));

    cache.clear();
    assertFalse(cache.contains("a"));
  }
}