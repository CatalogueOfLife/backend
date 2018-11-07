package org.col.es.mapping;

import org.col.es.model.EsNameUsage;
import org.junit.Test;

public class MappingFactoryTest {

  @Test
  public void getMapping1() {
    MappingFactory<EsNameUsage> mf = new MappingFactory<>();
    mf.setMapEnumToInt(true);  
    Mapping<EsNameUsage> mapping = mf.getMapping(EsNameUsage.class);
    System.out.println(SerializationUtil.pretty(mapping));
  }

}
