package org.col.api;

import java.net.URI;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class RandomUtilsTest {
  
  @Test
  public void testRandomString() {
    assertEquals(10, RandomUtils.randomString(10).length());
    
    // all upper case
    String rnd = RandomUtils.randomString(22);
    assertEquals(rnd, rnd.toUpperCase());
  
    URI uri = RandomUtils.randomUri();
    assertNotNull(uri);
    assertTrue(uri.isAbsolute());
    
  }
}