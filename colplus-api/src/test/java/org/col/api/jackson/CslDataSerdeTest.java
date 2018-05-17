package org.col.api.jackson;

import org.col.api.TestEntityGenerator;
import org.col.api.model.CslData;

public class CslDataSerdeTest extends SerdeTestBase<CslData> {

  public CslDataSerdeTest() {
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
