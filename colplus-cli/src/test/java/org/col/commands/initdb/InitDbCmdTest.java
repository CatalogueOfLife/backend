package org.col.commands.initdb;

import io.dropwizard.cli.Command;
import org.col.commands.CmdTestBase;
import org.junit.Test;

/**
 *
 */
public class InitDbCmdTest extends CmdTestBase {


  @Override
  public Command registerCommand() {
    // initdb is already a standard command
    return null;
  }

  @Test
  public void testInitCmd() throws Exception {
    run(false, "initdb", "--prompt", "0");
  }
}