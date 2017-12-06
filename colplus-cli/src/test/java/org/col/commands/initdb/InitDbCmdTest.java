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
    return new InitDbCmd();
  }

  @Test
  public void testInitCmd() throws Exception {
    run(false, "initdb");
  }
}