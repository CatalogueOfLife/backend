package life.catalogue.common.date;

import life.catalogue.common.io.DownloadUtil;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

public class DateUtilsTest {
  @Test
  public void parseDateTime() throws IOException {
    assertWorks("Wed, 14 Aug 2024 13:40:34 GMT");
    assertWorks("Wed, 14 Aug 2024 13:40:34 +1000");
    assertWorks("Wed, 14 Aug 2024 13:40:34 +0100 (CET)");
    assertWorks("Wed, 14 Aug 2024 13:40:34 +0100 (anything really)");
    assertWorks("Wed, 14 Aug 2024 13:40:34 CET");
    assertWorks("Wed, 14 Aug 2024 13:40:34 WST");
    assertWorks("Wed, 14 Aug 2024 13:40:34 CST");
    assertWorks("Wed, 14 Aug 2024 13:40:34 AEST");
    assertWorks("Wed, 14 Aug 2024 13:40:34 GMT+0000");

    assertFails("Wed, 14 Aug 2024 13:40:34 awsedrfgh");
  }


  void assertFails(String x) {
    Assert.assertTrue(DateUtils.parseRFC1123(x).isEmpty());
  }

  void assertWorks(String x) {
    Assert.assertTrue(DateUtils.parseRFC1123(x).isPresent());
  }
}