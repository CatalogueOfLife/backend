package life.catalogue.api.model;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.jackson.SerdeTestBase;

/**
 *
 */
public class VernacularNameTest extends SerdeTestBase<VernacularName> {
  
  public VernacularNameTest() {
    super(VernacularName.class);
  }
  
  @Override
  public VernacularName genTestValue() throws Exception {
    return TestEntityGenerator.newVernacularName("Grünfleisch");
  }
  
}