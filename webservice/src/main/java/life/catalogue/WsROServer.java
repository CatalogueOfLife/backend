package life.catalogue;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.io.DownloadUtil;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.dao.*;
import life.catalogue.doi.service.DataCiteService;
import life.catalogue.doi.service.DoiService;
import life.catalogue.dw.auth.AuthBundle;
import life.catalogue.dw.cors.CorsBundle;
import life.catalogue.dw.db.MybatisBundle;
import life.catalogue.dw.health.EsHealthCheck;
import life.catalogue.dw.health.EventBrokerHealthCheck;
import life.catalogue.dw.health.NameParserHealthCheck;
import life.catalogue.dw.jersey.ColJerseyBundle;
import life.catalogue.dw.logging.pg.PgLogBundle;
import life.catalogue.dw.managed.ManagedUtils;
import life.catalogue.dw.metrics.HttpClientBuilder;
import life.catalogue.dw.tasks.ClearCachesTask;
import life.catalogue.dw.tasks.EventQueueTask;
import life.catalogue.dw.tasks.ReloadPortalTemplatesTask;
import life.catalogue.es.EsClientFactory;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.es.NameUsageSearchService;
import life.catalogue.es.NameUsageSuggestionService;
import life.catalogue.es.nu.search.NameUsageSearchServiceEs;
import life.catalogue.es.nu.suggest.NameUsageSuggestionServiceEs;
import life.catalogue.event.EventBroker;
import life.catalogue.feedback.FeedbackService;
import life.catalogue.img.ImageService;
import life.catalogue.img.ImageServiceFS;
import life.catalogue.img.ThumborService;
import life.catalogue.interpreter.TxtTreeInterpreter;
import life.catalogue.matching.nidx.NameIndexFactory;
import life.catalogue.metadata.DoiResolver;
import life.catalogue.parser.NameParser;
import life.catalogue.portal.PortalPageRenderer;
import life.catalogue.resources.*;
import life.catalogue.resources.dataset.*;
import life.catalogue.resources.parser.*;

import org.gbif.dwc.terms.TermFactory;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.util.Timeout;
import org.apache.http.client.config.CookieSpecs;
import org.apache.ibatis.session.SqlSessionFactory;
import org.elasticsearch.client.RestClient;
import org.glassfish.jersey.CommonProperties;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.RequestEntityProcessing;
import org.glassfish.jersey.client.spi.ConnectorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.dropwizard.client.DropwizardApacheConnector;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.forms.MultiPartBundle;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import jakarta.ws.rs.client.Client;

/**
 * A read-only (RO) server for the most common resources.
 * It does not have any long running routines like exports and
 * is the only app that exposes the portal html rendering!
 *
 * Keeping caches in sync between the read-only and main application is tricky
 * and we rely on the EventBus to message changes relevant to caching between the apps.
 * This is mostly dataset changes (private, new releases) and user changes (permissions).
 */
public class WsROServer extends Application<WsServerConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(WsROServer.class);

  private final ColJerseyBundle coljersey = new ColJerseyBundle();
  private final MybatisBundle mybatis = new MybatisBundle();
  protected CloseableHttpClient httpClient;
  protected Client jerseyClient;
  private final AuthBundle auth = new AuthBundle();
  private final PgLogBundle log = new PgLogBundle();

  public static void main(final String[] args) throws Exception {
    SLF4JBridgeHandler.install();
    new WsROServer().run(args);
  }

  @Override
  public void initialize(Bootstrap<WsServerConfig> bootstrap) {
    // our mybatis classes
    bootstrap.addBundle(mybatis);
    // various custom jersey providers
    bootstrap.addBundle(coljersey);
    bootstrap.addBundle(new MultiPartBundle());
    bootstrap.addBundle(new CorsBundle());
    bootstrap.addBundle(log);
    // authentication which requires the UserMapper from mybatis AFTER the mybatis bundle has run
    bootstrap.addBundle(auth);

    // register CoLTerms
    TermFactory.instance().registerTermEnum(ColdpTerm.class);
    // use a custom jackson mapper
    ObjectMapper om = ApiModule.configureMapper(Jackson.newMinimalObjectMapper());
    bootstrap.setObjectMapper(om);
  }

  @Override
  public String getName() {
    return "ChecklistBankRO";
  }

  public String getUserAgent(WsServerConfig cfg) {
    return getName() + "/" + ObjectUtils.coalesce(cfg.versionString(), "1.0");
  }

  /**
   * Make sure to call this after the app has been bootstrapped, otherwise its null. Methods to add tasks or healthchecks
   * can be sure to use the session factory.
   */
  public SqlSessionFactory getSqlSessionFactory() {
    return mybatis.getSqlSessionFactory();
  }

  @Override
  public void run(WsServerConfig cfg, Environment env) throws Exception {
    final JerseyEnvironment j = env.jersey();
    LOG.warn("This service runs in read only mode and only responds to GET, HEAD & OPTIONS requests.");

    // this is read only - make sure we don't use a proper auth - whatever the configs say
    //var noUsers = new MapAuthenticationFactory();
    //noUsers.users.clear();
    //cfg.auth = noUsers;

    // remove SSL defaults that prevent correct use of TLS1.3
    if (cfg.client != null && cfg.client.getTlsConfiguration() != null) {
      cfg.client.getTlsConfiguration().setProtocol(null);
    }

    // update name parser timeout settings
    NameParser.PARSER.setTimeout(cfg.parserTimeout);

    // use a custom metrics naming strategy that does not involve the user agent name with a version
    httpClient = new HttpClientBuilder(env)
      .using(cfg.client)
      .build(getUserAgent(cfg)); // http client pool is managed via DW lifecycle inside this build call

    // reuse the same http client pool also for jersey clients!
    JerseyClientBuilder builder = new JerseyClientBuilder(env)
        .withProperty(CommonProperties.FEATURE_AUTO_DISCOVERY_DISABLE, Boolean.TRUE)
        .withProperty(ClientProperties.REQUEST_ENTITY_PROCESSING, RequestEntityProcessing.BUFFERED)
        .using(cfg.client)
        .using((ConnectorProvider) (cl,
            runtimeConfig) -> new DropwizardApacheConnector(httpClient, requestConfig(cfg.client), cfg.client.isChunkedEncodingEnabled()));
    // build both synchroneous and reactive clients sharing the same thread pool
  
    jerseyClient = builder.build(getUserAgent(cfg));
    auth.setSqlSessionFactory(mybatis.getSqlSessionFactory());
    auth.setClient(httpClient);
    log.setSqlSessionFactory(mybatis.getSqlSessionFactory());
    // finally provide the SqlSessionFactory & http client to the auth and jersey bundle
    coljersey.setSqlSessionFactory(mybatis.getSqlSessionFactory());

    DatasetInfoCache.CACHE.setFactory(mybatis.getSqlSessionFactory());

    // event broker
    var broker = new EventBroker(cfg.broker);
    env.lifecycle().manage(ManagedUtils.from(broker));

    // validation
    var validator = env.getValidator();

    // DOI
    DoiResolver doiResolver = new DoiResolver(httpClient);

    // name parser
    NameParser.PARSER.register(env.metrics());
    env.lifecycle().manage(ManagedUtils.from(NameParser.PARSER));
    env.lifecycle().addServerLifecycleListener(server -> {
      try {
        NameParser.PARSER.configs().loadFromCLB();
      } catch (Exception e) {
        LOG.error("Failed to load name parser configs", e);
      }
    });

    // ES
    NameUsageIndexService indexService = NameUsageIndexService.passThru();
    NameUsageSearchService searchService;
    NameUsageSuggestionService suggestService;
    final RestClient esClient;
    if (cfg.es == null || cfg.es.isEmpty()) {
      esClient = null;
      LOG.warn("No Elastic Search configured, use pass through indexing & searching");
      searchService = NameUsageSearchService.passThru();
      suggestService = NameUsageSuggestionService.passThru();
    } else {
      esClient = new EsClientFactory(cfg.es).createClient();
      env.lifecycle().manage(ManagedUtils.from(esClient));
      searchService = new NameUsageSearchServiceEs(cfg.es.nameUsage.name, esClient);
      suggestService = new NameUsageSuggestionServiceEs(cfg.es.nameUsage.name, esClient);
    }

    // images
    final ImageService imgService = new ImageServiceFS(cfg.img, broker);
    final ThumborService thumborService = new ThumborService(cfg.thumbor);

    // daos
    final DatasetImportDao diDao = new DatasetImportDao(getSqlSessionFactory(), cfg.metricsRepo);

    DatasetDao ddao = new DatasetDao(getSqlSessionFactory(), cfg.normalizer, cfg.release, cfg.gbif, cfg.doi, new DownloadUtil(httpClient),
      ImageService.passThru(), diDao, null, indexService, cfg.normalizer::scratchFile, broker, validator
    );
    DatasetExportDao exdao = new DatasetExportDao(cfg.job, getSqlSessionFactory(), validator);
    DatasetSourceDao dsdao = new DatasetSourceDao(getSqlSessionFactory());
    DecisionDao decdao = new DecisionDao(getSqlSessionFactory(), indexService, validator);
    DuplicateDao dupeDao = new DuplicateDao(getSqlSessionFactory());
    EstimateDao edao = new EstimateDao(getSqlSessionFactory(), validator);
    MetricsDao mdao = new MetricsDao(getSqlSessionFactory());
    NameDao ndao = new NameDao(getSqlSessionFactory(), indexService, NameIndexFactory.passThru(), validator);
    PublisherDao pdao = new PublisherDao(getSqlSessionFactory(), validator);
    SectorPublisherDao spdao = new SectorPublisherDao(getSqlSessionFactory(), broker, validator);
    ReferenceDao rdao = new ReferenceDao(getSqlSessionFactory(), doiResolver, validator);
    SynonymDao sdao = new SynonymDao(getSqlSessionFactory(), ndao, indexService, validator);
    TaxonDao tdao = new TaxonDao(getSqlSessionFactory(), ndao, mdao, thumborService, indexService, searchService, validator);
    SectorDao secdao = new SectorDao(getSqlSessionFactory(), indexService, tdao, validator);
    tdao.setSectorDao(secdao);
    TreeDao trDao = new TreeDao(getSqlSessionFactory());
    TxtTreeDao txtrDao = new TxtTreeDao(getSqlSessionFactory(), tdao, sdao, indexService, new TxtTreeInterpreter());

    // portal html page renderer - only in ROServer !!!
    PortalPageRenderer renderer = new PortalPageRenderer(ddao, dsdao, tdao, cfg.portalTemplateDir.toPath(), true);
    j.register(new PortalResource(renderer));

    // shared read only resources
    registerReadOnlyResources(j, cfg, getSqlSessionFactory(), null,
      ddao, dsdao, new AtomicBoolean(),
      diDao, dupeDao, edao, exdao, ndao, pdao, spdao, rdao, tdao, sdao, decdao, trDao, txtrDao,
      searchService, suggestService, indexService, imgService,
      FeedbackService.passThru(), doiResolver, coljersey
    );

    // healthchecks
    registerReadOnlyHealthChecks(env, broker, esClient, cfg);

    // tasks
    env.admin().addTask(new ClearCachesTask(auth, coljersey.getCache()));
    env.admin().addTask(new EventQueueTask(broker));
    env.admin().addTask(new ReloadPortalTemplatesTask(renderer));

    // attach listeners to event broker
    broker.register(auth);
    broker.register(coljersey);
    broker.register(DatasetInfoCache.CACHE);
  }

  static void registerReadOnlyHealthChecks(Environment env, EventBroker broker, @Nullable RestClient esClient, WsServerConfig cfg) {
    env.healthChecks().register("event-broker", new EventBrokerHealthCheck(broker));
    env.healthChecks().register("name-parser", new NameParserHealthCheck());
    if (esClient != null) {
      env.healthChecks().register("elastic", new EsHealthCheck(esClient, cfg.es));
    }
  }

  static void registerReadOnlyResources(JerseyEnvironment j, WsServerConfig cfg, SqlSessionFactory factory,
                                        @Nullable JobExecutor exec, DatasetDao ddao, DatasetSourceDao dsdao, AtomicBoolean exportBlocker,
                                        DatasetImportDao diDao, DuplicateDao dupeDao, EstimateDao edao, DatasetExportDao exdao, NameDao ndao, PublisherDao pdao, SectorPublisherDao spdao, ReferenceDao rdao, TaxonDao tdao, SynonymDao sdao, DecisionDao decdao, TreeDao trDao, TxtTreeDao txtrDao,
                                        NameUsageSearchService searchService, NameUsageSuggestionService suggestService, NameUsageIndexService indexService,
                                        ImageService imgService, FeedbackService feedbackService, DoiResolver doiResolver, ColJerseyBundle coljersey) {
    // dataset scoped resources
    j.register(new DatasetArchiveResource(cfg));
    j.register(new DatasetImportResource(diDao));
    j.register(new DatasetIssuesResource(factory));
    j.register(new DatasetPatchResource());
    j.register(new DatasetResource(factory, exec, ddao));
    j.register(new DatasetSourceResource(factory, dsdao));
    j.register(new DecisionResource(decdao));
    j.register(new DuplicateResource(dupeDao));
    j.register(new EstimateResource(edao));
    j.register(new ImageResource(imgService, factory));
    j.register(new NameResource(ndao));
    j.register(new NameUsageResource(searchService, suggestService, indexService, coljersey.getCache(), tdao, feedbackService));
    j.register(new SectorPublisherResource(spdao));
    j.register(new ReferenceResource(rdao));
    j.register(new SynonymResource(sdao));
    j.register(new TaxonResource(factory, tdao, txtrDao));
    j.register(new TreeResource(tdao, trDao));
    j.register(new VerbatimResource(factory));
    j.register(new VerbatimSourceResource(factory));
    j.register(new VernacularResource());

    // global resources
    j.register(new ExportResource(exdao, exportBlocker, cfg));
    j.register(new NameUsageSearchResource(searchService));
    j.register(new PublisherResource(pdao));
    j.register(new RobotsResource());
    j.register(new VernacularGlobalResource());
    j.register(new VersionResource(cfg.versionString(), LocalDateTime.now()));
    j.register(new VocabResource());

    // global parsers
    j.register(new HomotypicGroupingResource());
    j.register(new HomoglyphParserResource());
    j.register(new NameParserResource());
    j.register(new MetadataParserResource());
    j.register(new ParserResource<>());
    j.register(new ReferenceParserResource(doiResolver));
    j.register(new TaxGroupResource());
    j.register(new IdEncoderResource());
  }

  @Override
  protected void onFatalError(Throwable t) {
    LOG.error("Fatal startup error", t);
    System.exit(1);
  }
  
  /**
   * Mostly copied from HttpClientBuilder
   */
  public static RequestConfig requestConfig(JerseyClientConfiguration cfg) {
    final String cookiePolicy =
        cfg.isCookiesEnabled() ? CookieSpecs.DEFAULT : CookieSpecs.IGNORE_COOKIES;

    return RequestConfig.custom()
        .setCookieSpec(cookiePolicy)
        .setConnectTimeout(Timeout.of(cfg.getConnectionTimeout().toMilliseconds(), TimeUnit.MILLISECONDS))
        .setConnectionRequestTimeout(Timeout.of(cfg.getConnectionRequestTimeout().toMilliseconds(), TimeUnit.MILLISECONDS))
        .build();
  }
}
