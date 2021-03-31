package life.catalogue.command;

import life.catalogue.dao.TreeRepoRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 *
 */
public class UpdReleaseMetricCmdTest extends CmdTestBase {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule(true);

  @Rule
  public final TestDataRule testDataRule = TestDataRule.apple();

  public UpdReleaseMetricCmdTest() {
    super(new UpdReleaseMetricCmd());
  }
  
  @Test
  public void testRebuild() throws Exception {
    assertTrue(run("updReleaseMetrics", "--prompt", "0", "--key", "3").isEmpty());
  }

}