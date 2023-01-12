package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Page;
import life.catalogue.db.TestDataRule;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class NameUsageMapperNidxTest extends MapperTestBase<NameUsageMapper> {

  public NameUsageMapperNidxTest() {
    super(NameUsageMapper.class, TestDataRule.nidx());
  }

  @Test
  public void listByNamesIndexIDGlobal() throws Exception {
    // with author
    var res = mapper().listByNamesIndexIDGlobal( 4, new Page());
    assertEquals(3, res.size());
    Set<DSID<String>> usageIDs = res.stream().map(DSID::copy).collect(Collectors.toSet());
    assertEquals(
      Set.of(DSID.of(100, "u1"), DSID.of(102, "u1x"), DSID.of(102, "u2x")),
      usageIDs
    );

    // canonical +1
    assertEquals(4, mapper().listByNamesIndexIDGlobal( 3, new Page()).size());

    // none
    assertEquals(0, mapper().listByNamesIndexIDGlobal( 1, new Page()).size());
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