package org.col.commands.importer;

import io.dropwizard.cli.Command;
import org.col.commands.CmdTestBase;
import org.junit.Ignore;
import org.junit.Test;

/**
 *
 */
public class ImporterCmdTest extends CmdTestBase {

  @Test
  public void testImporterCmd() throws Exception {
    run(true, "import", "--key", "73");
  }

  @Test
  @Ignore("manual debug test")
  public void testImportDwcaUrl() throws Exception {
    run(true, "import", "--url", "http://data.canadensys.net/ipt/archive.do?r=vascan");
  }

  @Override
  public Command registerCommand() {
    return new ImporterCmd();
  }
}