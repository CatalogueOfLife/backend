package org.col.api.model;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.col.api.jackson.SerdeTestBase;

public class TaxonCountMapTest extends SerdeTestBase<TaxonCountMap> {
  
  public TaxonCountMapTest() {
    super(TaxonCountMap.class);
  }
  
  @Override
  public TaxonCountMap genTestValue() throws Exception {
    TaxonCountMap tcm = new TaxonCountMap();
    tcm.setId("456");
    tcm.setCount(new Int2IntOpenHashMap());
    tcm.getCount().put(124,6534);
    tcm.getCount().put(12,3);
    tcm.getCount().put(678,-3456789);
    tcm.getCount().put(567876,97653);
    tcm.getCount().put(-243,77);
    return tcm;
  }

}
