package org.col.api.jackson;

import org.col.api.TestEntityGenerator;
import org.col.api.model.Taxon;
import org.junit.Ignore;

/**
 *
 */
@Ignore("Needs fixing for roundtripping LocalDate")
public class TaxonSerdeTest extends SerdeTestBase<Taxon> {

  public TaxonSerdeTest() {
    super(Taxon.class);
  }

  @Override
  Taxon genTestValue() throws Exception {
    return TestEntityGenerator.newTaxon("alpha7");
  }

}