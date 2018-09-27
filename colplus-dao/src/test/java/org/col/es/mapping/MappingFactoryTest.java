package org.col.es.mapping;

import java.util.LinkedHashMap;

import org.col.es.EsReadTestBase;
import org.col.es.model.EsNameUsage;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Ignore("Embedded ES not working on jenkins yet")
public class MappingFactoryTest extends EsReadTestBase {

  @Test
  public void getMapping1() {
    Mapping<EsNameUsage> mapping = new MappingFactory<EsNameUsage>().getMapping(EsNameUsage.class);
    assertEquals("strict", mapping.getDynamic());
    assertEquals(EsNameUsage.class, mapping.getMappedClass());
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

    assertTrue(properties.containsKey("rank"));
    kf = (KeywordField) properties.get("rank");
    // enum fields not available for full-text search
    assertTrue(kf.hasMultiField(MultiField.CI_MULTIFIELD));
    assertFalse(kf.hasMultiField(MultiField.DEFAULT_MULTIFIELD));
    assertFalse(kf.hasMultiField(MultiField.NGRAM0_MULTIFIELD));
  }

}
