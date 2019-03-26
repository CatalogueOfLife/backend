package org.col.command.neoshell;

import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import org.col.config.WsServerConfig;

/**
 * Basic task to showcase hello world
 */
public class ShellCmd extends ConfiguredCommand<WsServerConfig> {
  private static final int PORT = 1337;
  
  public ShellCmd() {
    super("shell", "Open a neo4j shell to a given datasource");
  }
  
  @Override
  protected void run(Bootstrap<WsServerConfig> bootstrap, Namespace namespace, WsServerConfig configuration) throws Exception {
    System.out.format("Opening neo4j shell on port %s to dataset %s.\n" +
            "Open another dataset or post with key=null to close the shell.\n",
        PORT, 1234);
  }
}
