package org.col.commands.initdb;

import org.col.commands.CmdTestBase;
import org.junit.Test;

/**
 *
 */
public class InitDbCmdTest extends CmdTestBase {

  @Test
  public void testInitCmd() throws Exception {
    run("initdb");
  }
}