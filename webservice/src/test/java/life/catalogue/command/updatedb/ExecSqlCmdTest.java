package life.catalogue.command.updatedb;

import life.catalogue.command.CmdTestBase;
import org.junit.Test;

import static org.junit.Assert.*;

public class ExecSqlCmdTest extends CmdTestBase {

  public ExecSqlCmdTest() {
    super(new ExecSqlCmd());
  }

  @Test
  public void execute() throws Exception {
    assertTrue(run("execSql", "--prompt", "0", "--sql", "SELECT id, scientific_name FROM name_{KEY} LIMIT 1").isEmpty());
  }

}