package life.catalogue.command;

import life.catalogue.WsServer;

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
    //new WsServer().run(new String[]{"init", "/Users/markus/code/col/backend/webservice/config-local.yaml", "--prompt", "0", "--num", "4"});
    new WsServer().run(new String[]{"index", "/Users/markus/code/col/backend/webservice/config-dev.yaml", "--prompt", "0", "--key", "1049"});
  }
}
