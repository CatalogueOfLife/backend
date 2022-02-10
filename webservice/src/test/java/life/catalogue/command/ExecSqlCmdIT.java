package life.catalogue.command;

import java.io.File;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ExecSqlCmdIT extends CmdTestBase {

  public ExecSqlCmdIT() {
    super(ExecSqlCmd::new);
  }

  @Test
  public void execute() throws Exception {
    assertTrue(run("execSql", "--prompt", "0", "--sql", "SELECT id, scientific_name FROM name_{KEY} LIMIT 1").isEmpty());
  }

  @Test
  public void managed() throws Exception {
    File f = new File("target/test-classes/exec-test.sql");
    assertTrue(f.exists());
    assertTrue(run("execSql", "--prompt", "0", "--origin", "MANAGED", "--sqlfile", f.getAbsolutePath()).isEmpty());
  }
}