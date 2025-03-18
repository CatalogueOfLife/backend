package life.catalogue;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.command.PartitionCmd;
import life.catalogue.dw.health.NameParserHealthCheck;
import life.catalogue.dw.managed.ManagedUtils;
import life.catalogue.matching.MatchingCmd;
import life.catalogue.matching.MatchingResource;
import life.catalogue.matching.MatchingStorageChrononicle;
import life.catalogue.parser.NameParser;
import life.catalogue.resources.NamesIndexResource;

import org.gbif.dwc.terms.TermFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.jersey.setup.JerseyEnvironment;

public class MatchingServer extends Application<MatchingServerConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(MatchingServer.class);

  public static void main(final String[] args) throws Exception {
    SLF4JBridgeHandler.install();
    new MatchingServer().run(args);
  }

  @Override
  public void initialize(Bootstrap<MatchingServerConfig> bootstrap) {
    // register CoLTerms
    TermFactory.instance().registerTermEnum(ColdpTerm.class);
    // use a custom jackson mapper
    ObjectMapper om = ApiModule.configureMapper(Jackson.newMinimalObjectMapper());
    bootstrap.setObjectMapper(om);

    bootstrap.addCommand(new MatchingCmd());
  }

  @Override
  public String getName() {
    return "MatchingServer";
  }

  public String getUserAgent(WsServerConfig cfg) {
    return getName() + "/" + ObjectUtils.coalesce(cfg.versionString(), "1.0");
  }

  @Override
  public void run(MatchingServerConfig cfg, Environment env) throws Exception {
    final JerseyEnvironment j = env.jersey();

    // update name parser timeout settings
    NameParser.PARSER.setTimeout(cfg.parserTimeout);

    // name parser
    NameParser.PARSER.register(env.metrics());
    env.healthChecks().register("name-parser", new NameParserHealthCheck());
    env.lifecycle().manage(ManagedUtils.from(NameParser.PARSER));
    env.lifecycle().addServerLifecycleListener(server -> {
      try {
        NameParser.PARSER.configs().loadFromCLB();
      } catch (Exception e) {
        LOG.error("Failed to load name parser configs", e);
      }
    });

    // matcher storage - to be closed when service stops
    final var storage = MatchingStorageChrononicle.open(cfg.matching.storage, cfg.matching.poolSize);
    env.lifecycle().manage(ManagedUtils.from(storage));

    // resources
    j.register(new NamesIndexResource(storage.getNameIndex()));
    j.register(new MatchingResource(storage));
  }

}
