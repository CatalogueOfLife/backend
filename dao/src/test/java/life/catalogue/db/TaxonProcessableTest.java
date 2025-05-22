package life.catalogue.db;

import life.catalogue.api.model.DSID;
import life.catalogue.api.vocab.Users;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.TestDataRule;

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

  @Test
  public void updateTaxonID() {
    for (var mc : TaxonProcessable.MAPPERS) {
      var m = testDataRule.getMapper(mc);
      var key = DSID.of(3,"1");
      m.updateTaxonID(key, "2", Users.TESTER);
    }
  }
}