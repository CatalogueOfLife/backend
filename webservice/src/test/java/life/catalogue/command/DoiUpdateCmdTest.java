package life.catalogue.command;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 *
 */
public class DoiUpdateCmdTest extends CmdTestBase {

  public DoiUpdateCmdTest() {
    super(DoiUpdateCmd::new);
  }
  
  @Test
  public void testInitCmd() throws Exception {
    assertTrue(run("doi", "--prompt", "0", "--user", "test", "--key", "3").isEmpty());
  }

}