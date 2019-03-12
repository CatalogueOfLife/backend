package org.col.api.search;

import org.junit.Test;

public class FacetValueTest {

  @Test
  public void testForInteger() {
    // Both should work. First one initially broke facet query request b/c it didn't take into account that integers might be stored using
    // the keyword datatype for performance reasons.
    FacetValue.forInteger("1010", 3);
    FacetValue.forInteger(1010, 3);
  }

}
