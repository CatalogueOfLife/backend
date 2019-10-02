package org.col.api.model;

import org.col.api.TestEntityGenerator;
import org.col.api.jackson.SerdeTestBase;

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