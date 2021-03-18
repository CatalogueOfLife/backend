package life.catalogue.resources;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LegacyWebserviceResourceTest {

  @Test
  public void calcLimit() {
    assertEquals(100, LegacyWebserviceResource.calcLimit(false, 100));
    assertEquals(1000, LegacyWebserviceResource.calcLimit(false, 1000));
    assertEquals(1000, LegacyWebserviceResource.calcLimit(false, 10000));
    assertEquals(100, LegacyWebserviceResource.calcLimit(false, null));
    assertEquals(1, LegacyWebserviceResource.calcLimit(false, 1));
    assertEquals(0, LegacyWebserviceResource.calcLimit(false, 0));
    assertEquals(0, LegacyWebserviceResource.calcLimit(false, -10));

    assertEquals(100, LegacyWebserviceResource.calcLimit(true, 100));
    assertEquals(100, LegacyWebserviceResource.calcLimit(true, 1000));
    assertEquals(100, LegacyWebserviceResource.calcLimit(true, 10000));
    assertEquals(10, LegacyWebserviceResource.calcLimit(true, null));
    assertEquals(1, LegacyWebserviceResource.calcLimit(true, 1));
    assertEquals(0, LegacyWebserviceResource.calcLimit(true, 0));
    assertEquals(0, LegacyWebserviceResource.calcLimit(true, -10));
  }
}