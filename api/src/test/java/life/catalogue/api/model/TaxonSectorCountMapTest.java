package life.catalogue.api.model;

import life.catalogue.api.jackson.SerdeTestBase;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

public class TaxonSectorCountMapTest extends SerdeTestBase<TaxonSectorCountMap> {
  
  public TaxonSectorCountMapTest() {
    super(TaxonSectorCountMap.class);
  }
  
  @Override
  public TaxonSectorCountMap genTestValue() throws Exception {
    TaxonSectorCountMap tcm = new TaxonSectorCountMap();
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
