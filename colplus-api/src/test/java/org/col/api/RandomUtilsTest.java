package org.col.api;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
  }
}