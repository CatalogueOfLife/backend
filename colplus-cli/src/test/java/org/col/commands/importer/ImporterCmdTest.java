package org.col.commands.importer;

import io.dropwizard.cli.Command;
import org.col.commands.CmdTestBase;
import org.junit.Ignore;
import org.junit.Test;

/**
 *
 */
@Ignore("manual debug tests for entire import commands")
public class ImporterCmdTest extends CmdTestBase {

  @Test
  public void testImporterCmd() throws Exception {
    run(true, "import", "--key", "10");
  }

  @Test
  public void testImportDwcaUrl() throws Exception {
    run(true, "import", "--force", "true", "--url", "http://bdj.pensoft.net/lib/ajax_srv/archive_download.php?archive_type=2&document_id=11760");
  }

  @Override
  public Command registerCommand() {
    return new ImporterCmd();
  }
}