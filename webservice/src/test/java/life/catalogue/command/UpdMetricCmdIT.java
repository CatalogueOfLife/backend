package life.catalogue.command;

import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.TestDataRule;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 *
 */
public class UpdMetricCmdIT extends CmdTestBase {

  @ClassRule
  public static final PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public final TestDataRule testDataRule = TestDataRule.apple();

  public UpdMetricCmdIT() {
    super(UpdMetricCmd::new);
  }
  
  @Test
  public void testRebuildCol() throws Exception {
    assertTrue(run("updMetrics", "--prompt", "0", "--user", "tester", "--key", "3").isEmpty());
  }

  /**
   * A targeted rebuild of just the given datasets, the way a handful of broken imports gets repaired.
   * The unknown key has to be warned about, not blow up the whole run.
   */
  @Test
  public void testRebuildDatasets() throws Exception {
    assertTrue(run("updMetrics", "--prompt", "0", "--user", "tester", "--datasets", "11, 12,999999,11").isEmpty());
  }

  @Test
  public void testRebuildAll() throws Exception {
    assertTrue(run("updMetrics", "--prompt", "0", "--user", "tester", "--all").isEmpty());
  }
}