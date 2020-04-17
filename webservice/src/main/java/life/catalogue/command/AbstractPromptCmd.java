package life.catalogue.command;

import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import life.catalogue.WsServerConfig;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import java.util.concurrent.TimeUnit;

/**
 * Command to execute given SQL statements for each dataset partition.
 * The command executes a SQL template passed into the command for each dataset where data partitions exist.
 * Before execution of the SQL the command replaces all {datasetKey} variables in the template with the actual integer key.
 *
 * The command will look at the existing name partition tables to find the datasets with data.
 */
public abstract class AbstractPromptCmd extends ConfiguredCommand<WsServerConfig> {
  private static final String ARG_PROMPT = "prompt";

  protected WsServerConfig cfg;

  public AbstractPromptCmd(String name, String description) {
    super(name, description);
  }
  
  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    subparser.addArgument("--"+ARG_PROMPT)
        .setDefault(10)
        .dest(ARG_PROMPT)
        .type(Integer.class)
        .required(false)
        .help("Waiting time in seconds for a user prompt to abort db update. Use zero for no prompt");
  }

  public abstract String describeCmd(Namespace namespace, WsServerConfig cfg);

  public abstract void execute(Bootstrap<WsServerConfig> bootstrap, Namespace namespace, WsServerConfig cfg) throws Exception;

  @Override
  protected void run(Bootstrap<WsServerConfig> bootstrap, Namespace namespace, WsServerConfig cfg) throws Exception {
    final int prompt = namespace.getInt(ARG_PROMPT);
    if (prompt > 0) {
      System.out.format(describeCmd(namespace, cfg) + "\n");
      System.out.format("You have %s seconds to abort if you did not intend to do so !!!\n", prompt);
      TimeUnit.SECONDS.sleep(prompt);
    }
    this.cfg = cfg;
    execute(bootstrap, namespace, cfg);
    System.out.println("Done !!!");
  }

}
