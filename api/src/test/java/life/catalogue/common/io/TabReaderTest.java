package life.catalogue.common.io;

import java.io.IOException;

import junit.framework.TestCase;

public class TabReaderTest extends TestCase {

  public void testCsv() throws IOException {
    var r = TabReader.csv(Resources.reader("charsets/utf-8.txt"), 0);
    for (var x : r) {
      System.out.println(x);
    }
  }

  public void testTab() {
  }
}