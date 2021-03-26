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
public class NamesIndexCmdTest extends CmdTestBase {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule(true);

  @Rule
  public final TestDataRule testDataRule = TestDataRule.apple();

  public NamesIndexCmdTest() {
    super(new NamesIndexCmd());
  }
  
  @Test
  public void testRebuild() throws Exception {
    assertTrue(run("nidx", "--prompt", "0").isEmpty());
  }

}