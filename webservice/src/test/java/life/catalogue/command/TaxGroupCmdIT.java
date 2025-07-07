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
public class TaxGroupCmdIT extends CmdTestBase {

  @ClassRule
  public static final PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public final TestDataRule testDataRule = TestDataRule.apple();

  public TaxGroupCmdIT() {
    super(TaxGroupCmd::new);
  }
  
  @Test
  public void testRebuildCol() throws Exception {
    assertTrue(run("taxgroup", "--prompt", "0", "--user", "tester", "--update-datasets", "true").isEmpty());
  }
}