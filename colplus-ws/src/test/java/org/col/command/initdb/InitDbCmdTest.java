package org.col.command.initdb;

import org.col.command.CmdTestBase;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 *
 */
@Ignore("uses too much memory currently due to texttree print in memory")
public class InitDbCmdTest extends CmdTestBase {
  
  public InitDbCmdTest() {
    super(new InitDbCmd());
  }
  
  @Test
  public void testInitCmd() throws Exception {
    assertTrue(run("initdb", "--prompt", "0"));
  }

}