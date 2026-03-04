package life.catalogue.command;

import life.catalogue.junit.TestDataRule;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 *
 */
public class IndexCmdIT extends EsCmdTestBase {

  public IndexCmdIT() {
    super(IndexCmd::new, TestDataRule.apple());
  }

  @Before
  public void init() {
    // file location see config-test.yaml scratch dir
    var tmp = new File("/tmp/col/test/index");
    System.out.println("Clear working directory " + tmp.getAbsolutePath());
    FileUtils.deleteQuietly(tmp);
  }
  @Test
  public void testRebuild() throws Exception {
    assertTrue(run("index", "--prompt", "0", "--all", "true").isEmpty());
  }
}