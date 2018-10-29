package org.col.es.mapping;

import org.col.es.model.EsNameUsage;
import org.junit.Test;

public class MappingFactoryTest {

  @Test
  public void getMapping1() {
    MappingFactory<EsNameUsage> mf = new MappingFactory<>();
    mf.setMapEnumToInt(true);  
    Mapping<EsNameUsage> mapping = mf.getMapping(EsNameUsage.class);
    MappingSerializer<EsNameUsage> s = new MappingSerializer<>(mapping, true);
    System.out.println(s.serialize());
//    assertEquals("strict", mapping.getDynamic());
//    assertEquals(EsNameUsage.class, mapping.getMappedClass());
//    LinkedHashMap<String, ESField> properties = mapping.getProperties();
//
//    assertTrue(properties.containsKey("accordingToDate"));
//    DateField df = (DateField) properties.get("accordingToDate");
//    assertEquals(ESDataType.DATE, df.getType());
//
//    assertTrue(properties.containsKey("lifezones"));
//    KeywordField kf = (KeywordField) properties.get("lifezones");
//    // enum fields not available for full-text search
//    assertTrue(kf.hasMultiField(MultiField.CASE_INSENSITIVE));
//    assertFalse(kf.hasMultiField(MultiField.DEFAULT));
//    assertFalse(kf.hasMultiField(MultiField.NGRAM));
//
//    assertTrue(properties.containsKey("name"));
//    ComplexField nested = (ComplexField) properties.get("name");
//    assertNull(nested.getType()); // a.k.a. Elasticsearch type "object"
//
//    properties = nested.getProperties();
//
//    assertTrue(properties.containsKey("rank"));
//    kf = (KeywordField) properties.get("rank");
//    // enum fields not available for full-text search
//    assertTrue(kf.hasMultiField(MultiField.CASE_INSENSITIVE));
//    assertFalse(kf.hasMultiField(MultiField.DEFAULT));
//    assertFalse(kf.hasMultiField(MultiField.NGRAM));
  }

}
