package org.col.es.mapping;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.col.es.EsModule;
import org.col.es.model.NameUsageDocument;
import org.junit.Test;

/**
 * Only tests that we don't get exceptions while generating the document type mappings. Plus peek at what's being
 * generated.
 */
public class MappingFactoryTest {

//  @Test
//  public void getMapping1a() throws JsonProcessingException {
//    MappingsFactory mf = MappingsFactory.usingFields();
//    mf.setMapEnumToInt(true);
//    Mappings mapping = mf.getMapping(NameUsageDocument.class);
//    System.out.println(EsModule.writeDebug(mapping));
//  }
//
//  @Test
//  public void getMapping1b() throws JsonProcessingException {
//    MappingsFactory mf = MappingsFactory.usingFields();
//    mf.setMapEnumToInt(false);
//    Mappings mapping = mf.getMapping(NameUsageDocument.class);
//    System.out.println(EsModule.writeDebug(mapping));
//  }
//
  @Test
  public void getMapping2a() throws JsonProcessingException {
    MappingsFactory mf = MappingsFactory.usingGetters();
    mf.setMapEnumToInt(true);
    Mappings mapping = mf.getMapping(NameUsageDocument.class);
    System.out.println(EsModule.writeDebug(mapping));
  }

//  @Test
//  public void getMapping2b() throws JsonProcessingException {
//    MappingsFactory mf = MappingsFactory.usingGetters();
//    mf.setMapEnumToInt(true);
//    Mappings mapping = mf.getMapping(NameUsageDocument.class);
//    System.out.println(EsModule.writeDebug(mapping));
//  }

}
