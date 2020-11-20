package life.catalogue.command;

import life.catalogue.command.InitDbCmd;
import life.catalogue.command.CmdTestBase;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 *
 */
public class InitDbCmdTest extends CmdTestBase {
  
  public InitDbCmdTest() {
    super(new InitDbCmd());
  }
  
  @Test
  public void testInitCmd() throws Exception {
    assertTrue(run("initdb", "--prompt", "0").isEmpty());
  }

}