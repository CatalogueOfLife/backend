package life.catalogue.command;

import life.catalogue.db.PgSetupRule;

import life.catalogue.db.TestDataRule;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class RepartitionCmdTest extends CmdTestBase {

  @ClassRule
  public static final PgSetupRule pgSetupRule = new PgSetupRule(true);

  @Rule
  public final TestDataRule testDataRule = TestDataRule.apple();

  public RepartitionCmdTest() {
    super(RepartitionCmd::new);
  }

  @Test
  public void testRebuildAll() throws Exception {
    assertTrue(run("repartition", "--prompt", "0", "--num", "10").isEmpty());
  }
}