package life.catalogue.matching;

import io.dropwizard.core.setup.Bootstrap;

import life.catalogue.MatchingServerConfig;

import net.sourceforge.argparse4j.inf.Namespace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.core.cli.ConfiguredCommand;

public class MatchingCmd extends ConfiguredCommand<MatchingServerConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(MatchingCmd.class);

  public MatchingCmd() {
    super("name", "description");
  }

  @Override
  protected void run(Bootstrap<MatchingServerConfig> bootstrap, Namespace namespace, MatchingServerConfig configuration) throws Exception {

  }
}
