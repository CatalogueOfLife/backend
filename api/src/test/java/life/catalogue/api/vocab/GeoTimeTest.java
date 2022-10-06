package life.catalogue.api.vocab;

import org.junit.Test;

import static org.junit.Assert.*;

public class GeoTimeTest {

  @Test
  public void testBuild() {
    assertTrue(GeoTime.TIMES.size() > 170);
    for (GeoTime t : GeoTime.TIMES.values()) {
      assertNotNull(t.getName());
      assertNotNull(t.getType());
      if (t.getStart() == null && t.getEnd() != null  ||  t.getStart()!= null && t.getEnd() == null) {
        fail("Unclosed GeoTime "+t);
      }
      if (t.getParent() == null) {
        if (!t.getName().equals("Precambrian") && !t.getName().equals("Phanerozoic")) {
          fail("Only 2 root times expected, not " + t);
        }
      }
    }
    // check first and last code exists
    assertNotNull(GeoTime.byName("Aalenian"));
    assertNotNull(GeoTime.byName("Zanclean"));

    GeoTime bashkirian = GeoTime.byName("Bashkirian");
    assertEquals((Double) 323.2, bashkirian.getStart());
    assertEquals((Double) 315.2, bashkirian.getEnd());

    GeoTime jura = GeoTime.byName("Jurassic");
    assertEquals((Double) 201.3, jura.getStart());
    assertEquals((Double) 145.0, jura.getEnd());
  }

  @Test
  public void testIncludes() {
    GeoTime gt = new GeoTime("", GeoTimeType.AGE, 12.789, 10.34, null);
    assertTrue(gt.includes(12));
    assertTrue(gt.includes(11));
    assertTrue(gt.includes(11.456789));
    assertTrue(gt.includes(10.34));

    assertFalse(gt.includes(13));
    assertFalse(gt.includes(10));
    assertFalse(gt.includes(0));
    assertFalse(gt.includes(100));

    gt = new GeoTime("", GeoTimeType.AGE, 1.34, null, null);
    assertFalse(gt.includes(13));
    assertFalse(gt.includes(10));
    assertFalse(gt.includes(100));
    assertFalse(gt.includes(1.5));

    assertTrue(gt.includes(1.34));
    assertTrue(gt.includes(1));
    assertTrue(gt.includes(0));

    gt = new GeoTime("", GeoTimeType.AGE, null, 1.34, null);
    assertTrue(gt.includes(13));
    assertTrue(gt.includes(10));
    assertTrue(gt.includes(100));
    assertTrue(gt.includes(1.5));
    assertTrue(gt.includes(1.34));

    assertFalse(gt.includes(1.339));
    assertFalse(gt.includes(0));

    gt = new GeoTime("", GeoTimeType.AGE, null, null, null);
    assertFalse(gt.includes(1));
    assertFalse(gt.includes(13));
    assertFalse(gt.includes(0));

  }
}