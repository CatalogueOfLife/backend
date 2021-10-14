package life.catalogue.common.io;

import org.junit.Test;

import static org.junit.Assert.*;

public class LineReaderTest {

  @Test
  public void iterator() {
    LineReader lr = new LineReader(Resources.stream("line-reader-test.txt"));
    int count = 0;
    for (String x : lr) {
      if (count==0) {
        assertEquals(1, lr.getRow());
      }
      System.out.println(x);
      count++;
    }
    assertEquals(3, count);
    assertEquals(9, lr.getRow());
  }
}