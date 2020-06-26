package life.catalogue.es.nu;

import life.catalogue.api.search.NameUsageSearchParameter;
import org.junit.Test;

import static org.junit.Assert.*;

public class NameUsageFieldLookupTest {

  @Test
  public void lookup() {
    for (NameUsageSearchParameter p : NameUsageSearchParameter.values()) {
      assertNotNull(NameUsageFieldLookup.INSTANCE.lookup(p));
    }
  }
}