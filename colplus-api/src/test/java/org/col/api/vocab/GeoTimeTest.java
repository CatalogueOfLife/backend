package org.col.api.vocab;

import org.junit.Test;

import static org.junit.Assert.*;

public class GeoTimeTest {
  
  @Test
  public void testBuild() {
    assertTrue(GeoTime.TIMES.size() > 170);
    for (GeoTime t : GeoTime.TIMES.values()) {
      assertNotNull(t.getName());
      assertNotNull(t.getType());
    }
    // check first and last code exists
    assertNotNull(GeoTime.byName("Aalenian"));
    assertNotNull(GeoTime.byName("Zanclean"));
    
    GeoTime bashkirian = GeoTime.byName("Bashkirian");
    assertEquals((Double) 323.2, bashkirian.getStart());
    assertEquals((Double) 315.2, bashkirian.getEnd());
  }
}