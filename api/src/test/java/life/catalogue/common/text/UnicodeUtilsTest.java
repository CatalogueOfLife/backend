package life.catalogue.common.text;

import org.apache.commons.lang3.time.StopWatch;
import org.junit.Test;

import static org.junit.Assert.*;

public class UnicodeUtilsTest {
  final String input1 = "rdtrfvgb3weñ54drtfvgxá+ä+.p, …-!§%&\"´`'ꓢᏞᎪ";
  final String input2 = "rfvgb3çw\uD835\uDEC3\uD835\uDEFD54d\uD835\uDE08tfvgx+ä+.p, …-!§%&\"´`'";
  final int iterations = 1000000;

  @Test
  public void containsHomoglyphs() {
    assertTrue(UnicodeUtils.containsHomoglyphs(input1));
    assertEquals(180, UnicodeUtils.findHomoglyph(input1));

    assertTrue(UnicodeUtils.containsHomoglyphs(input2));
    assertEquals(120515, UnicodeUtils.findHomoglyph(input2));

    // hybrid marker is fine in out domain!
    assertFalse(UnicodeUtils.containsHomoglyphs("Abies × Picea"));

    var watch = StopWatch.createStarted();
    for (int x=0; x<iterations; x++) {
      UnicodeUtils.containsHomoglyphs(input2 + x);
    }
    watch.stop();
    System.out.println(watch);
  }

  @Test
  public void replaceHomoglyphs() {
    assertEquals("rdtrfvgb3weñ54drtfvgxá+ä+.p, …-!§%&\"'`'SLA", UnicodeUtils.replaceHomoglyphs(input1));
    assertEquals("rfvgb3çwßß54dAtfvgx+ä+.p, …-!§%&\"'`'", UnicodeUtils.replaceHomoglyphs(input2));
    assertEquals("Abies × Picea", UnicodeUtils.replaceHomoglyphs("Abies × Picea"));
  }

}