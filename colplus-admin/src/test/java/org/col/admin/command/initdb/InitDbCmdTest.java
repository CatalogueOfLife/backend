package org.col.admin.command.initdb;

import org.col.admin.command.CmdTestBase;
import org.junit.Test;

/**
 *
 */
public class InitDbCmdTest extends CmdTestBase {

  public InitDbCmdTest() {
    super(new InitDbCmd());
  }

  @Test
  public void testInitCmd() throws Exception {
    run(false, "initdb", "--prompt", "0");
  }
}