package life.catalogue;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.dw.health.NameParserHealthCheck;
import life.catalogue.dw.health.NamesIndexHealthCheck;
import life.catalogue.dw.managed.Component;
import life.catalogue.dw.managed.ManagedService;
import life.catalogue.dw.managed.ManagedUtils;
import life.catalogue.matching.MatchingCmd;
import life.catalogue.matching.MatchingResource;
import life.catalogue.matching.MatchingService;
import life.catalogue.matching.MatchingStorageDisk;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.matching.nidx.NameIndexFactory;
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
  private NameIndex ni;

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

  public NameIndex getNamesIndex() {
    return ni;
  }

  @Override
  public void run(MatchingServerConfig cfg, Environment env) throws Exception {
    final JerseyEnvironment j = env.jersey();

    // create a managed service that controls our startable/stoppable components in sync with the DW lifecycle
    final ManagedService managedService = new ManagedService(env.lifecycle());

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

    // name index
    if (cfg.namesIndex.file == null) {
      throw new IllegalArgumentException("namesIndex.file must be configured");
    }
    // we can start up the index automatically
    ni = NameIndexFactory.build(cfg.namesIndex, AuthorshipNormalizer.INSTANCE).started();
    managedService.manage(Component.NamesIndex, ni);
    env.healthChecks().register("names-index", new NamesIndexHealthCheck(ni));

    // matcher
    final var storage = new MatchingStorageDisk();
    final var matcher = new MatchingService<>(ni, storage);

    // resources
    j.register(new NamesIndexResource(ni));
    j.register(new MatchingResource(cfg.matching, matcher));
  }

  @Override
  protected void onFatalError(Throwable t) {
    if (ni != null) {
      try {
        LOG.error("Fatal startup error, closing names index gracefully", t);
        ni.close();
      } catch (Exception e) {
        LOG.error("Failed to shutdown names index", e);
      }
    } else {
      LOG.error("Fatal startup error", t);
    }
    System.exit(1);
  }

}
