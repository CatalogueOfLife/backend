package life.catalogue.command;

import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 *
 */
public class UpdMetricCmdIT extends CmdTestBase {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule(true);

  @Rule
  public final TestDataRule testDataRule = TestDataRule.apple();

  public UpdMetricCmdIT() {
    super(new UpdMetricCmd());
  }
  
  @Test
  public void testRebuildCol() throws Exception {
    assertTrue(run("updMetrics", "--prompt", "0", "--user", "tester", "--key", "3").isEmpty());
  }

  @Test
  public void testRebuildAll() throws Exception {
    assertTrue(run("updMetrics", "--prompt", "0", "--user", "tester", "--all", "true").isEmpty());
  }
}