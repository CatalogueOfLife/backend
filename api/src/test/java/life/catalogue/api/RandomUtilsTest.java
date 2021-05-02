package life.catalogue.api;

import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.*;

/**
 *
 */
public class RandomUtilsTest {
  
  @Test
  public void testRandomString() {
    assertEquals(10, RandomUtils.randomLatinString(10).length());
    
    // all upper case
    String rnd = RandomUtils.randomLatinString(22);
    assertEquals(rnd, rnd.toUpperCase());
  
    URI uri = RandomUtils.randomUri();
    assertNotNull(uri);
    assertTrue(uri.isAbsolute());
    
  }
}