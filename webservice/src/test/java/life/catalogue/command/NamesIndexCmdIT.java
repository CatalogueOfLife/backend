package life.catalogue.command;

import de.bytefish.pgbulkinsert.pgsql.PgBinaryWriter;

import life.catalogue.db.TestDataRule;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertTrue;

/**
 *
 */
public class NamesIndexCmdIT extends CmdTestBase {

  public NamesIndexCmdIT() {
    super(NamesIndexCmd::new, TestDataRule.apple());
  }

  @Before
  public void init() {
    // file location see config-test.yaml scratch dir
    var tmp = new File("/tmp/colplus/scratch/nidx-build");
    System.out.println("Clear working directory " + tmp.getAbsolutePath());
    FileUtils.deleteQuietly(tmp);
    PgBinaryWriter pgw = null;
  }
  @Test
  public void testRebuild() throws Exception {
    assertTrue(run("nidx", "--prompt", "0").isEmpty());
  }

}