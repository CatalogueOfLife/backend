package org.col.admin.task;

import org.col.admin.AdminServer;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Manual test class to run an entire command in your IDE for debugging purposes mostly.
 * This can obviously also achieved by just calling the CliApp main class with the appropriate arguments
 */
@Ignore
public class ExecuteCmd {

  @Test
  public void test() throws Exception {
    // to run a command that needs configs please point the second argument to a matching yaml file
    new AdminServer().run(new String[]{"initdb", "/Users/markus/Desktop/config-admin.yml", "--prompt", "2"});
  }
}
