package org.col.commands.hello;

import io.dropwizard.cli.Command;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

/**
 * Basic task to showcase hello world
 */
public class HelloCmd extends Command {
  private static final String NAME_ARG = "name";

  public HelloCmd() {
    super("hello", "Hello World");
  }

  @Override
  public void configure(Subparser subparser) {
    // Add a command line option for the name
    subparser.addArgument("-n", "--name")
        .dest(NAME_ARG)
        .type(String.class)
        .setDefault("stranger")
        .help("The name of the person to greet");
  }

  @Override
  public void run(Bootstrap<?> bootstrap, Namespace namespace) throws Exception {
    System.out.println("Hello " + namespace.getString(NAME_ARG) + "!");
  }
}
