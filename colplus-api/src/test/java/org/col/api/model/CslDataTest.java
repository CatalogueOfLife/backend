package org.col.api.model;

import org.col.api.TestEntityGenerator;
import org.col.api.jackson.SerdeTestBase;

public class CslDataTest extends SerdeTestBase<CslData> {
  
  public CslDataTest() {
    super(CslData.class);
  }
  
  @Override
  public CslData genTestValue() throws Exception {
    return TestEntityGenerator.createCsl();
  }
  
  protected void debug(String json, Wrapper<CslData> wrapper, Wrapper<CslData> wrapper2) {
    //System.out.println(json);
  }
}
