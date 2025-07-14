package life.catalogue.command;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 *
 */
public class InitCmdIT extends CmdTestBase {
  
  public InitCmdIT() {
    super(InitCmd::new);
  }
  
  @Test
  public void testInitCmd() throws Exception {
    // we need to close all db connections for a db init to work!
    testDataRule.skipAfter();
    pgRule.shutdownDbPool();
    assertTrue(run("init", "--prompt", "0", "--num", "16").isEmpty());
  }

}