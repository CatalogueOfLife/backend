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


}
