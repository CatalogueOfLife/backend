package org.col.es.mapping;

import static org.junit.Assert.*;
import java.util.LinkedHashMap;
import org.col.api.model.Taxon;
import org.col.es.EsReadTestBase;
import org.junit.Test;

public class MappingFactoryTest extends EsReadTestBase {

  @Test
  public void getMapping1() {
    Mapping<Taxon> mapping = new MappingFactory<Taxon>().getMapping(Taxon.class);
    assertEquals("strict", mapping.getDynamic());
    assertEquals(Taxon.class, mapping.getMappedClass());
    LinkedHashMap<String, ESField> properties = mapping.getProperties();

    assertTrue(properties.containsKey("accordingToDate"));
    DateField df = (DateField) properties.get("accordingToDate");
    assertEquals(ESDataType.DATE, df.getType());

    assertTrue(properties.containsKey("lifezones"));
    KeywordField kf = (KeywordField) properties.get("lifezones");
    // enum fields not available for full-text search
    assertTrue(kf.hasMultiField(MultiField.CI_MULTIFIELD));
    assertFalse(kf.hasMultiField(MultiField.DEFAULT_MULTIFIELD));
    assertFalse(kf.hasMultiField(MultiField.NGRAM0_MULTIFIELD));

    assertTrue(properties.containsKey("name"));
    ComplexField nested = (ComplexField) properties.get("name");
    assertNull(nested.getType()); // a.k.a. Elasticsearch type "object"

    properties = nested.getProperties();
    assertTrue(properties.containsKey("key"));
    SimpleField sf = (SimpleField) properties.get("key");
    assertEquals(ESDataType.INTEGER, sf.getType());

    assertTrue(properties.containsKey("rank"));
    kf = (KeywordField) properties.get("rank");
    // enum fields not available for full-text search
    assertTrue(kf.hasMultiField(MultiField.CI_MULTIFIELD));
    assertFalse(kf.hasMultiField(MultiField.DEFAULT_MULTIFIELD));
    assertFalse(kf.hasMultiField(MultiField.NGRAM0_MULTIFIELD));
  }

}
