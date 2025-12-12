package life.catalogue;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.db.EmptySqlSessionFactory;
import life.catalogue.dw.cors.CorsBundle;
import life.catalogue.dw.health.NameParserHealthCheck;
import life.catalogue.dw.jersey.exception.IllegalArgumentExceptionMapper;
import life.catalogue.dw.jersey.filter.CreatedResponseFilter;
import life.catalogue.dw.jersey.provider.EnumParamConverterProvider;
import life.catalogue.dw.jersey.writers.BufferedImageBodyWriter;
import life.catalogue.dw.jersey.writers.TsvBodyWriter;
import life.catalogue.dw.mail.MailBundle;
import life.catalogue.dw.managed.Component;
import life.catalogue.dw.managed.ManagedService;
import life.catalogue.dw.managed.ManagedUtils;
import life.catalogue.matching.UsageMatcher;
import life.catalogue.matching.UsageMatcherFactory;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.matching.nidx.NameIndexFactory;
import life.catalogue.metadata.coldp.ColdpMetadataParser;
import life.catalogue.metadata.eml.EmlParser;
import life.catalogue.parser.NameParser;
import life.catalogue.resources.JobResource;
import life.catalogue.resources.VersionResource;
import life.catalogue.resources.matching.FixedNameUsageMatchingResource;
import life.catalogue.resources.parser.NameParserResource;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSessionFactory;

import org.gbif.dwc.terms.TermFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.jersey.setup.JerseyEnvironment;

/**
 * A read-only (RO) server for the most common resources.
 * It does not have any long running routines like exports and
 * is the only app that exposes the portal html rendering!
 *
 * Keeping caches in sync between the read-only and main application is tricky
 * and we rely on the EventBus to message changes relevant to caching between the apps.
 * This is mostly dataset changes (private, new releases) and user changes (permissions).
 */
public class WsMatchingServer extends Application<WsMatchingServerConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(WsMatchingServer.class);

  public static void main(final String[] args) throws Exception {
    SLF4JBridgeHandler.install();
    new WsMatchingServer().run(args);
  }

  @Override
  public void initialize(Bootstrap<WsMatchingServerConfig> bootstrap) {
    // various custom jersey providers
    bootstrap.addBundle(new CorsBundle());
    // register CoLTerms
    TermFactory.instance().registerTermEnum(ColdpTerm.class);
    // use a custom jackson mapper
    ObjectMapper om = ApiModule.configureMapper(Jackson.newMinimalObjectMapper());
    bootstrap.setObjectMapper(om);

  }

  @Override
  public String getName() {
    return "ChecklistBankMatching";
  }

  public String getUserAgent(WsMatchingServerConfig cfg) {
    return getName() + "/" + ObjectUtils.coalesce(cfg.versionString(), "1.0");
  }

  @Override
  public void run(WsMatchingServerConfig cfg, Environment env) throws Exception {
    final JerseyEnvironment j = env.jersey();

    // things which are normaly part of the jersey bundle, but we only want a subset here:
    j.packages(EnumParamConverterProvider.class.getPackage().getName());
    j.packages(CreatedResponseFilter.class.getPackage().getName());
    j.packages(IllegalArgumentExceptionMapper.class.getPackage().getName());
    j.packages(TsvBodyWriter.class.getPackage().getName());

    // name parser
    NameParser.PARSER.setTimeout(cfg.parserTimeout);
    env.lifecycle().manage(ManagedUtils.from(NameParser.PARSER));
    env.lifecycle().addServerLifecycleListener(server -> {
      try {
        NameParser.PARSER.configs().loadFromCLB();
      } catch (Exception e) {
        LOG.error("Failed to load name parser configs", e);
      }
    });

    env.healthChecks().register("name-parser", new NameParserHealthCheck());

    SqlSessionFactory factory = new EmptySqlSessionFactory();
    NameIndex nidx = NameIndexFactory.build(cfg.namesIndex, factory, AuthorshipNormalizer.INSTANCE);
    Dataset dataset = readDataset(cfg.matching.datasetJson(cfg.matchingDatasetKey));
    UsageMatcher matcher = UsageMatcherFactory.buildPersistentMatcher(
      cfg.matchingDatasetKey, List.of(), dataset.getSize()+1, cfg.matching, nidx
    );

    j.register(new NameParserResource());
    j.register(new FixedNameUsageMatchingResource(cfg.matching, dataset, matcher));
    j.register(new VersionResource(cfg.versionString(), LocalDateTime.now()));

    cfg.logDirectories();
    if (cfg.mkdirs()) {
      LOG.info("Created config repository directories");
    }
    clearTmp(cfg);
  }

  private void clearTmp(WsMatchingServerConfig cfg) {
    LOG.info("Clear uploadDir dir {}", cfg.matching.uploadDir);
    if (cfg.matching.uploadDir.exists()) {
      try {
        FileUtils.cleanDirectory(cfg.matching.uploadDir);
      } catch (IOException e) {
        LOG.error("Error cleaning upload dir {}", cfg.matching.uploadDir, e);
      }
    }
  }

  private Dataset readDataset(File file) throws IOException {
    if (!file.exists()) {
      throw new FileNotFoundException("Dataset metadata file does not exist: " + file);
    }
    return ColdpMetadataParser
      .readJSON(new FileInputStream(file))
      .get()
      .getDataset();
  }

  @Override
  protected void onFatalError(Throwable t) {
    LOG.error("Fatal startup error", t);
    System.exit(1);
  }
}
