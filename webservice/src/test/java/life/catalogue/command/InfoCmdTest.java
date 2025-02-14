package life.catalogue.command;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class InfoCmdTest extends CmdTestBase {

  public InfoCmdTest() {
    super(InfoCmd::new);
  }

  @Test
  public void testInitCmd() throws Exception {
    assertTrue(run("info", "--prompt", "0").isEmpty());
  }

}