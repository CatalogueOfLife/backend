package org.col.command;

import org.col.WsServer;
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
    new WsServer().run(new String[]{"initdb", "/Users/markus/Desktop/config-admin.yml", "--prompt", "2"});
  }
}
