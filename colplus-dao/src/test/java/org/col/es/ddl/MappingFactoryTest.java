package org.col.es.ddl;

import org.col.es.mapping.Mappings;
import org.col.es.mapping.MappingsFactory;
import org.col.es.model.NameUsageDocument;
import org.junit.Test;

public class MappingFactoryTest {

  @Test // Just testing we don't get exceptions
  public void getMapping1() {
    MappingsFactory mf = new MappingsFactory();
    mf.setMapEnumToInt(true);
    Mappings mapping = mf.getMapping(NameUsageDocument.class);
    System.out.println(JsonUtil.pretty(mapping));
  }

}
