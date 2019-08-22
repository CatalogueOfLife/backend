package org.col.es.ddl;

import org.col.es.name.NameUsageDocument;
import org.junit.Test;

public class MappingFactoryTest {

  @Test
  public void getMapping1() {
    MappingFactory<NameUsageDocument> mf = new MappingFactory<>();
    mf.setMapEnumToInt(true);
    DocumentTypeMapping mapping = mf.getMapping(NameUsageDocument.class);
    System.out.println(SerializationUtil.pretty(mapping));
  }

}
