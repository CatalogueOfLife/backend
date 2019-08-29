package org.col.es.ddl;

import org.col.es.ddl.DocumentTypeMapping;
import org.col.es.ddl.MappingFactory;
import org.col.es.ddl.SerializationUtil;
import org.col.es.model.NameUsageDocument;
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
