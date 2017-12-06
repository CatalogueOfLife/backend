package org.col.commands.importer;

import io.dropwizard.cli.Command;
import org.col.commands.CmdTestBase;
import org.junit.Test;

/**
 *
 */
public class ImporterCmdTest extends CmdTestBase {

  @Test
  public void testImporterCmd() throws Exception {
    run(true, "import", "--key", "73");
  }

  @Override
  public Command registerCommand() {
    return new ImporterCmd();
  }
}