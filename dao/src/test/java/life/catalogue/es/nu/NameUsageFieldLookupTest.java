package life.catalogue.es.nu;

import life.catalogue.api.search.NameUsageSearchParameter;
import org.junit.Test;

import static org.junit.Assert.*;

public class NameUsageFieldLookupTest {

  @Test
  public void lookup() {
    for (NameUsageSearchParameter p : NameUsageSearchParameter.values()) {
      if (p == NameUsageSearchParameter.NAME_INDEX_ID) continue;
      assertNotNull(NameUsageFieldLookup.INSTANCE.lookupSingle(p));
      assertEquals(1, NameUsageFieldLookup.INSTANCE.lookup(p).length);
    }
    assertEquals(2, NameUsageFieldLookup.INSTANCE.lookup(NameUsageSearchParameter.NAME_INDEX_ID).length);
  }
}