package life.catalogue.common.io;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LineReaderTest {

  @Test
  public void iterator() {
    try (LineReader lr = new LineReader(Resources.stream("line-reader-test.txt"))) {
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

  /**
   * Keeping blank lines but skipping comments used to fail at the end of the file
   */
  @Test
  public void keepBlankSkipComments() {
    try (LineReader lr = new LineReader(new BufferedReader(new StringReader("a\n\n# comment\nb\n")), false, true)) {
      List<String> lines = new ArrayList<>();
      lr.forEach(lines::add);
      assertEquals(List.of("a", "", "b"), lines);
    }
  }
}