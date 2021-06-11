package life.catalogue.api.model;

import life.catalogue.api.jackson.SerdeTestBase;

public class CitationSerdeTest extends SerdeTestBase<Citation> {

  public CitationSerdeTest() {
    super(Citation.class);
  }

  @Override
  public Citation genTestValue() throws Exception {
    return CitationTest.create();
  }

}