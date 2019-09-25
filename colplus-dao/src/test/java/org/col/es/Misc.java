package org.col.es;

import java.util.Arrays;

import static org.col.es.name.NameUsageWrapperConverter.*;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;

public class Misc {

  @Test
  public void testSplit() {
    // Apparently split does a trim-right, but not a trim-left
    String s = "ABCD     ";
    String[] ss = s.split("\\W");
    assertEquals(1, ss.length);
    Arrays.stream(ss).forEach(x -> System.out.println("XXXXX: *" + x + "*"));
    s = "     ABCD";
    ss = s.split("\\W");
    Arrays.stream(ss).forEach(x -> System.out.println("XXXXX: *" + x + "*"));
    assertTrue(ss.length > 1);
  }

  @Test
  public void testNormalize() {
    // Ouch! It matters in what order you lowecase and (strongly) normalize a string. So whatever order is used at index
    // time must also be used at query time!
    String s0 = normalizeStrongly("ABCDEFGHIJK").toLowerCase();
    String s1 = normalizeStrongly("ABCDEFGHIJK".toLowerCase());
    System.out.println(s0);
    System.out.println(s1);
    assertNotEquals(s0, s1);
  }

}
