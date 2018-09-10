package org.col.es.mapping;

import static org.junit.Assert.*;
import java.util.LinkedHashMap;
import org.col.api.model.Taxon;
import org.col.es.EsReadTestBase;
import org.junit.Test;

@SuppressWarnings("static-method")
public class MappingFactoryTest extends EsReadTestBase {

  @Test
  public void getMapping1() {
    Mapping<Taxon> mapping = new MappingFactory<Taxon>().getMapping(Taxon.class);
    assertEquals("strict", mapping.getDynamic());
    assertEquals(Taxon.class, mapping.getMappedClass());
    LinkedHashMap<String, ESField> properties = mapping.getProperties();
    assertTrue(properties.containsKey("name"));
    ComplexField nested = (ComplexField) properties.get("name");
    properties = nested.getProperties();
    assertTrue(properties.containsKey("key"));
    assertTrue(properties.containsKey("rank"));
    // SimpleField sf = 
  }

}
