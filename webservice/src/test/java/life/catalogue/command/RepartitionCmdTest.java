package life.catalogue.command;

import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.TestDataRule;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

@Ignore("With pg17.2 FK constraints are also checked for detached partitions and the CMD is broken !!!")
public class RepartitionCmdTest extends CmdTestBase {

  @ClassRule
  public static final PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public final TestDataRule testDataRule = TestDataRule.apple();

  public RepartitionCmdTest() {
    super(RepartitionCmd::new);
  }

  @Test
  public void testRebuildAll() throws Exception {
    assertTrue(run("repartition", "--prompt", "0", "--num", "3").isEmpty());
  }
}