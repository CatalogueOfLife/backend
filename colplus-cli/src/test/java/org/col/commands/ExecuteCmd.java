package org.col.commands;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Manual test class to run an entire command in your IDE for debugging purposes mostly.
 */
@Ignore
public class ExecuteCmd {

  @Test
  public void test() throws Exception {
    // to run a command that needs configs please point the second argument to a matching yaml file
    //new CliApp().run(new String[]{"gbifsync", "/Users/markus/Desktop/config.yml"});
    new CliApp().run(new String[]{"import", "-k", "21882", "/Users/markus/Desktop/config.yml"});
    //new CliApp().run(new String[]{"hello"});
  }
}
