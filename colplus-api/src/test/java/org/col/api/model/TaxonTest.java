package org.col.api.model;

import org.col.api.TestEntityGenerator;
import org.col.api.jackson.SerdeTestBase;

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