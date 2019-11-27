package life.catalogue.es;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Misc {

  @Test // Apparently String.split does a trim-right, but not a trim-left
  public void testSplit() {
    String s = "ABCD     ";
    String[] ss = s.split("\\W");
    assertEquals(1, ss.length);
    s = "     ABCD";
    ss = s.split("\\W");
    assertTrue(ss.length > 1);
  }

}
