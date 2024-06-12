package life.catalogue.command;

import life.catalogue.WsServerConfig;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.parser.NameParser;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.dropwizard.core.cli.ConfiguredCommand;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.jackson.Jackson;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

/**
 * Command to execute given SQL statements for each dataset partition.
 * The command executes a SQL template passed into the command for each dataset where data partitions exist.
 * Before execution of the SQL the command replaces all {datasetKey} variables in the template with the actual integer key.
 *
 * The command will look at the existing name partition tables to find the datasets with data.
 */
public abstract class AbstractPromptCmd extends ConfiguredCommand<WsServerConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractPromptCmd.class);
  private static final String ARG_PROMPT = "prompt";

  protected WsServerConfig cfg;
  protected MetricRegistry metrics;

  public AbstractPromptCmd(String name, String description) {
    super(name, description);
  }
  
  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    subparser.addArgument("--"+ARG_PROMPT)
        .setDefault(5)
        .dest(ARG_PROMPT)
        .type(Integer.class)
        .required(false)
        .help("Waiting time in seconds for a user prompt to abort db update. Use zero for no prompt");
  }

  public String describeCmd(Namespace namespace, WsServerConfig cfg) {
    return getDescription();
  }

  public abstract void execute(Bootstrap<WsServerConfig> bootstrap, Namespace namespace, WsServerConfig cfg) throws Exception;

  public void prePromt(Bootstrap<WsServerConfig> bootstrap, Namespace namespace, WsServerConfig cfg){
    // nothing by default
  }

  @Override
  protected void run(Bootstrap<WsServerConfig> bootstrap, Namespace namespace, WsServerConfig cfg) throws Exception {
    System.out.format("This is COL Server version %s%n", cfg.versionString());
    prePromt(bootstrap, namespace, cfg);
    final int prompt = namespace.getInt(ARG_PROMPT);
    if (prompt > 0) {
      System.out.format(describeCmd(namespace, cfg) + "\n");
      System.out.format("You have %s seconds to abort if you did not intend to do so !!!%n", prompt);
      TimeUnit.SECONDS.sleep(prompt);
    }
    this.cfg = cfg;
    // update name parser timeout settings
    NameParser.PARSER.setTimeout(cfg.parserTimeout);
    // use a custom jackson mapper
    ObjectMapper om = ApiModule.configureMapper(Jackson.newMinimalObjectMapper());
    bootstrap.setObjectMapper(om);
    // we dont deal with shortlived requests in commands and rather want to minimize connection usage
    // if we reuse the ws configs we will use too many idle connections doe to the high mimimum setting
    cfg.db.minimumIdle = 0;
    metrics = bootstrap.getMetricRegistry();
    execute(bootstrap, namespace, cfg);
    LOG.info("{} command completed successfully", getClass().getSimpleName());
    System.out.println("Done !!!");
  }

}
