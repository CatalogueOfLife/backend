package org.col.command.initdb;

import org.col.command.CmdTestBase;
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
    assertTrue(run("initdb", "--prompt", "0"));
  }

}