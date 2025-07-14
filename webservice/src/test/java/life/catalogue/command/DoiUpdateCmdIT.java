package life.catalogue.command;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 *
 */
public class DoiUpdateCmdIT extends CmdTestBase {

  public DoiUpdateCmdIT() {
    super(DoiUpdateCmd::new);
  }
  
  @Test
  public void testInitCmd() throws Exception {
    assertTrue(run("doi", "--prompt", "0", "--user", "tester", "--key", "3").isEmpty());
  }

}