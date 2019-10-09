package org.col.es.mapping;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.col.es.EsModule;
import org.col.es.model.NameUsageDocument;
import org.junit.Test;

public class MappingFactoryTest {

  @Test // Just testing we don't get exceptions
  public void getMapping1() throws JsonProcessingException {
    MappingsFactory mf = new MappingsFactory();
    mf.setMapEnumToInt(true);
    Mappings mapping = mf.getMapping(NameUsageDocument.class);
    System.out.println(EsModule.writeDebug(mapping));
  }

}
