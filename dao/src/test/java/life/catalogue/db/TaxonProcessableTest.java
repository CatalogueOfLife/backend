package life.catalogue.db;

import life.catalogue.api.model.DSID;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class TaxonProcessableTest {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public final TestDataRule testDataRule = TestDataRule.empty();

  @Test
  public void testImplementations() {
    for (var mc : TaxonProcessable.MAPPERS) {
      var m = testDataRule.getMapper(mc);
      var key = DSID.of(3,"1");
      m.listByTaxon(key);
      m.deleteByTaxon(key);
    }
  }
}