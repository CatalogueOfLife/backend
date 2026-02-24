package life.catalogue.es.query;

import life.catalogue.api.search.NameUsageSearchParameter;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class FieldLookupTest {

  @Test
  public void lookup() {
    for (NameUsageSearchParameter p : NameUsageSearchParameter.values()) {
      assertNotNull(FieldLookup.INSTANCE.lookupSingle(p));
      assertEquals(1, FieldLookup.INSTANCE.lookup(p).length);
    }
  }

  @Test
  public void printFilterFields() {
    for (var p : NameUsageSearchParameter.values()) {
      System.out.println(p.name() + " -> " + FieldLookup.INSTANCE.lookupSingle(p));
    }
  }

}