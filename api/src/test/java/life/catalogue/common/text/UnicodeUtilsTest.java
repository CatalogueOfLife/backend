package life.catalogue.common.text;

import org.apache.commons.lang3.time.StopWatch;
import org.junit.Test;

public class UnicodeUtilsTest {

  @Test
  public void containsHomoglyphs() {
    UnicodeUtils.containsHomoglyphs("rdtrfvgb3we54drtfvgx+ä+.p, …-!§%&\"´`'");

    var watch = StopWatch.createStarted();
    for (int x=0; x<1000; x++) {
      UnicodeUtils.containsHomoglyphs("rfvgb3w\uD835\uDEC3\uD835\uDEFD54d\uD835\uDE08tfvgx+ä+.p, …-!§%&\"´`'" + x);
    }
    watch.stop();
    System.out.println(watch);
  }
}