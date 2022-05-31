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
  public void testByNidx() throws Exception {
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

}