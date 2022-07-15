package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Page;
import life.catalogue.db.TestDataRule;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
    assertEquals(1, mapper().listByCanonNIDX( 100, 3).size());
    assertEquals(1, mapper().listByCanonNIDX( 101, 3).size());
    assertEquals(2, mapper().listByCanonNIDX( 102, 3).size());
    // not existing dataset
    assertEquals(0, mapper().listByCanonNIDX( 103, 3).size());
    // not a canonical nidx
    assertEquals(0, mapper().listByCanonNIDX( 100, 4).size());
  }

}