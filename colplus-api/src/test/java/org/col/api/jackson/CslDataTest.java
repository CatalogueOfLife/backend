package org.col.api.jackson;

import org.col.api.RandomInstance;
import org.col.api.model.CslData;
import org.col.api.model.CslDate;
import org.col.api.model.CslName;

public class CslDataTest extends SerdeTestBase<CslData> {

  public CslDataTest() {
    super(CslData.class);
  }

  @Override
  public CslData genTestValue() throws Exception {
    RandomInstance random = new RandomInstance();
    CslData csl = (CslData) random.create(CslData.class, CslName.class, CslDate.class);
    csl.getOriginalDate().setDateParts(new int[][] {{1752, 4, 4}, {1752, 8, 4}});
    csl.getSubmitted().setDateParts(new int[][] {{1850, 6, 12}});
    return csl;
  }

  protected void debug(String json, Wrapper<CslData> wrapper, Wrapper<CslData> wrapper2) {
    //System.out.println(json);
  }
}
