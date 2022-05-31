package life.catalogue.command;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 *
 */
public class InitDbCmdTest extends CmdTestBase {
  
  public InitDbCmdTest() {
    super(InitDbCmd::new);
  }
  
  @Test
  public void testInitCmd() throws Exception {
    // we need to close all db connections for a db init to work!
    testDataRule.skipAfter();
    pgSetupRule.after();
    assertTrue(run("initdb", "--prompt", "0", "--num", "3").isEmpty());
  }

}