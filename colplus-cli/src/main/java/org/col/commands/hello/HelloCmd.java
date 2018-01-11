package org.col.commands.hello;

import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.col.commands.config.CliConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Basic task to showcase hello world
 */
public class HelloCmd extends ConfiguredCommand<CliConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(HelloCmd.class);
  private static final String NAME_ARG = "name";

  public HelloCmd() {
    super("hello", "Hello World");
    MDC.put("task", getName());
  }

  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    // Add a command line option for the name
    subparser.addArgument("-n", "--name")
        .dest(NAME_ARG)
        .type(String.class)
        .setDefault("stranger")
        .help("The name of the person to greet");
  }

  @Override
  protected void run(Bootstrap<CliConfig> bootstrap, Namespace namespace, CliConfig cfg) throws Exception {
    LOG.info("Hello {}!", namespace.getString(NAME_ARG));
    System.out.println("Hello " + namespace.getString(NAME_ARG) + "!");
  }

}
