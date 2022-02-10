package life.catalogue.api.search;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class FacetValueTest {

  @Test
  public void testForInteger() {
    // Both should work. First one initially broke facet query request b/c it didn't take into account that integers might be stored using
    // the keyword datatype for performance reasons.
    var f = FacetValue.forInteger("1010", 3, null);
    assertNull(f.getLabel());
    assertEquals(1010, (int) f.getValue());
    assertEquals(3, f.getCount());

    f = FacetValue.forInteger(1010, 3, k -> "title #"+k);
    assertEquals("title #1010", f.getLabel());
    assertEquals(1010, (int) f.getValue());
    assertEquals(3, f.getCount());
  }

}
