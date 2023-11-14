package life.catalogue.common.io;

import java.io.File;

import org.junit.Test;

public class UnixCmdUtilsTest {
  File testFile = new File("/Users/markus/Downloads/names.tsv");

  @Test
  public void testSortC() {
    UnixCmdUtils.sortC(testFile, 0);
  }

  @Test
  public void testSplit() {
    UnixCmdUtils.split(testFile, 1000, 3);
  }
}