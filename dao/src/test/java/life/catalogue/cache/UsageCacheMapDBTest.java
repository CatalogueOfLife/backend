package life.catalogue.cache;

import life.catalogue.api.model.DSID;

import life.catalogue.api.model.SimpleNameWithPub;
import life.catalogue.api.vocab.Datasets;

import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;

import org.apache.commons.io.FileUtils;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

public class UsageCacheMapDBTest {
  File dbf;
  UsageCacheMapDB cache;

  @Before
  public void init() throws Exception {
    dbf = File.createTempFile("coltest", "mapdb");
    cache = new UsageCacheMapDB(dbf, false, true, 8);
    cache.start();
  }

  @After
  public void destroy() throws Exception {
    cache.close();
  }

  @Test
  public void crud() throws Exception {
    final SimpleNameWithPub sn = new SimpleNameWithPub();
    sn.setId("a");
    sn.setName("Abies");
    sn.setRank(Rank.GENUS);
    sn.setStatus(TaxonomicStatus.MISAPPLIED);
    sn.setCode(NomCode.BOTANICAL);
    sn.setNamesIndexMatchType(MatchType.EXACT);

    assertFalse(cache.contains(DSID.colID("a")));

    cache.put(Datasets.COL, sn);
    assertTrue(cache.contains(DSID.colID("a")));
    assertEquals(sn, cache.get(DSID.colID("a")));

    cache.put(999, sn);
    cache.remove(DSID.colID("a"));
    assertFalse(cache.contains(DSID.colID("a")));
    assertTrue(cache.contains(DSID.of(999,"a")));

    cache.clear(999);
    assertFalse(cache.contains(DSID.of(999,"a")));
  }
}