package org.col.api.jackson;

import org.col.api.TestEntityGenerator;
import org.col.api.model.Taxon;

/**
 *
 */
public class TaxonSerdeTest extends SerdeTestBase<Taxon> {
  
  public TaxonSerdeTest() {
    super(Taxon.class);
  }
  
  @Override
  public Taxon genTestValue() throws Exception {
    return TestEntityGenerator.newTaxon("alpha7");
  }
  
}