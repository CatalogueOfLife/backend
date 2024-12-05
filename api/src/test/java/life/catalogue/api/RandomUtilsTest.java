package life.catalogue.api;

import java.net.URI;

import org.junit.Test;

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

  @Test
  public void randomUnicodeString() {
    var x = RandomUtils.randomUnicodeString(75);
    System.out.println(x);
    assertEquals(75, x.length());
  }
}