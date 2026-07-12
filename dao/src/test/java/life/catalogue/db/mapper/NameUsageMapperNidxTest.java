package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Page;
import life.catalogue.junit.TestDataRule;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;

import static org.junit.Assert.*;

public class NameUsageMapperNidxTest extends MapperTestBase<NameUsageMapper> {

  public NameUsageMapperNidxTest() {
    super(NameUsageMapper.class, TestDataRule.nidx());
  }

  @Test
  public void listByNamesIndexIDGlobal() throws Exception {
    // with author: index 4 matches usages in datasets 100 (PROJECT, excluded), 102, 102
    var res = mapper().listByNamesIndexIDGlobal( 4, new Page());
    assertEquals(2, res.size());
    Set<DSID<String>> usageIDs = res.stream().map(DSID::copy).collect(Collectors.toSet());
    assertEquals(
      Set.of(DSID.of(102, "u1x"), DSID.of(102, "u2x")),
      usageIDs
    );

    // canonical +1: indexes 3 & 4 match usages in datasets 100 (excluded), 101, 102, 102
    assertEquals(3, mapper().listByNamesIndexIDGlobal( 3, new Page()).size());

    // none
    assertEquals(0, mapper().listByNamesIndexIDGlobal( 1, new Page()).size());

    // global count (datasetKey=null) excludes the PROJECT dataset, matching the filtered list
    assertEquals(2, (int) mapper().countByNamesIndexID(4, null));
    assertEquals(0, (int) mapper().countByNamesIndexID(1, null));
    // per-dataset count is NOT public-filtered: the PROJECT dataset 100 still counts its own usages
    assertEquals(1, (int) mapper().countByNamesIndexID(4, 100));
    assertEquals(2, (int) mapper().countByNamesIndexID(4, 102));
  }

  @Test
  public void listByNamesIndexIDGlobalClassified() throws Exception {
    // dataset 100 has origin=PROJECT and is excluded, only external datasets 101 & 102 remain
    // with author: index 4 matches usages in datasets 100 (excluded), 102, 102
    var res = mapper().listByNamesIndexIDGlobalClassified( 4, new Page());
    assertEquals(2, res.size());
    assertTrue(res.stream().noneMatch(u -> u.getDatasetKey() == 100));
    // canonical +1: indexes 3 & 4 match usages in datasets 100 (excluded), 101, 102, 102
    assertEquals(3, mapper().listByNamesIndexIDGlobalClassified( 3, new Page()).size());
    // none
    assertEquals(0, mapper().listByNamesIndexIDGlobalClassified( 1, new Page()).size());

    // the paging count must stay consistent with the filtered list, also excluding project usages
    assertEquals(2, (int) mapper().countByNamesIndexIDGlobalClassified(4));
    assertEquals(3, (int) mapper().countByNamesIndexIDGlobalClassified(3));
    assertEquals(0, (int) mapper().countByNamesIndexIDGlobalClassified(1));
  }

  @Test
  public void listByCanonNIDX() throws Exception {
    assertCanonNIDX(1, 100, 3);
    assertCanonNIDX(1, 101, 3);
    assertCanonNIDX(2, 102, 3);
    // not existing dataset
    assertCanonNIDX(0, 103, 3);
    // not a canonical nidx
    assertCanonNIDX(0, 100, 4);
  }

  void assertCanonNIDX(int expectedNum, int datasetKey, int canonNidx) {
    var res = mapper().listByCanonNIDX( datasetKey, canonNidx);
    assertEquals(expectedNum, res.size());
    for (var sn : res) {
      assertEquals((Integer)canonNidx, sn.getCanonicalId());
      assertNotNull(sn.getNamesIndexId());
      assertNotNull(sn.getNamesIndexMatchType());
      assertNotNull(sn.getRank());
      assertNotNull(sn.getName());
    }
  }

}