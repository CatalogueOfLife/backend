package life.catalogue.db;

import life.catalogue.api.model.DSID;

import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.TestDataRule;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * We just make sure all implementations have valid SQL - no data checks here.
 */
public class SectorProcessableTest {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public final TestDataRule testDataRule = TestDataRule.empty();

  @Test
  public void testImplementations() {
    for (var mc : SectorProcessable.MAPPERS) {
      var m = testDataRule.getMapper(mc);
      var sectorKey = DSID.of(3,1);
      m.processSector(sectorKey).forEach(System.out::println);
      m.removeSectorKey(sectorKey);
      m.deleteBySector(sectorKey);
    }
  }
}