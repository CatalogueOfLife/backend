package life.catalogue.api.model;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.jackson.SerdeTestBase;

/**
 *
 */
public class TaxonTest extends SerdeTestBase<Taxon> {
  
  public TaxonTest() {
    super(Taxon.class);
  }
  
  @Override
  public Taxon genTestValue() throws Exception {
    return TestEntityGenerator.newTaxon("alpha7");
  }
  
}