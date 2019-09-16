package org.col.api.vocab;

import org.junit.Test;

import static org.junit.Assert.*;

public class GeoTimeTest {
  
  @Test
  public void testBuild() {
    assertTrue(GeoTime.TIMES.size() > 100);
    for (GeoTime t : GeoTime.TIMES.values()) {
      assertNotNull(t.getName());
      assertNotNull(t.getUnit());
    }
    // check first and last code exists
    assertNotNull(GeoTime.byName("Aalenian"));
    assertNotNull(GeoTime.byName("Zanclean"));
  }
}