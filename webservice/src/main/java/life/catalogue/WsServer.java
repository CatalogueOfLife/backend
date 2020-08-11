package life.catalogue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import io.dropwizard.Application;
import io.dropwizard.client.DropwizardApacheConnector;
import io.dropwizard.client.HttpClientBuilder;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.forms.MultiPartBundle;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import life.catalogue.api.datapackage.ColdpTerm;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.vocab.ColDwcTerm;
import life.catalogue.assembly.AssemblyCoordinator;
import life.catalogue.command.es.IndexCmd;
import life.catalogue.command.initdb.InitDbCmd;
import life.catalogue.command.updatedb.AddTableCmd;
import life.catalogue.command.updatedb.ExecSqlCmd;
import life.catalogue.common.csl.CslUtil;
import life.catalogue.common.io.DownloadUtil;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.dao.*;
import life.catalogue.db.LookupTables;
import life.catalogue.db.tree.DatasetDiffService;
import life.catalogue.db.tree.SectorDiffService;
import life.catalogue.dw.ManagedUtils;
import life.catalogue.dw.auth.AuthBundle;
import life.catalogue.dw.cors.CorsBundle;
import life.catalogue.dw.db.MybatisBundle;
import life.catalogue.dw.es.ManagedEsClient;
import life.catalogue.dw.health.*;
import life.catalogue.dw.jersey.ColJerseyBundle;
import life.catalogue.es.EsClientFactory;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.es.NameUsageSearchService;
import life.catalogue.es.NameUsageSuggestionService;
import life.catalogue.es.nu.NameUsageIndexServiceEs;
import life.catalogue.es.nu.search.NameUsageSearchServiceEs;
import life.catalogue.es.nu.suggest.NameUsageSuggestionServiceEs;
import life.catalogue.gbifsync.GbifSync;
import life.catalogue.img.ImageService;
import life.catalogue.img.ImageServiceFS;
import life.catalogue.importer.ContinuousImporter;
import life.catalogue.importer.ImportManager;
import life.catalogue.matching.NameIndexFactory;
import life.catalogue.matching.NameIndexImpl;
import life.catalogue.parser.NameParser;
import life.catalogue.release.AcExporter;
import life.catalogue.release.ReleaseManager;
import life.catalogue.resources.*;
import life.catalogue.resources.parser.NameParserResource;
import life.catalogue.resources.parser.ParserResource;
import life.catalogue.swagger.OpenApiFactory;
import org.apache.commons.io.FileUtils;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.ibatis.session.SqlSessionFactory;
import org.elasticsearch.client.RestClient;
import org.gbif.dwc.terms.TermFactory;
import org.glassfish.jersey.client.spi.ConnectorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import javax.ws.rs.client.Client;
import java.io.IOException;
import java.sql.Connection;

public class WsServer extends Application<WsServerConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(WsServer.class);

  private final ColJerseyBundle coljersey = new ColJerseyBundle();
  private final MybatisBundle mybatis = new MybatisBundle();
  private final AuthBundle auth = new AuthBundle();
  private final EventBus bus = new EventBus("bus");
  protected CloseableHttpClient httpClient;
  protected Client jerseyClient;
  private NameIndexImpl ni;
  private ImportManager importManager;

  public static void main(final String[] args) throws Exception {
    SLF4JBridgeHandler.install();
    new WsServer().run(args);
  }

  @Override
  public void initialize(Bootstrap<WsServerConfig> bootstrap) {
    // our mybatis classes
    bootstrap.addBundle(mybatis);
    // various custom jersey providers
    bootstrap.addBundle(coljersey);
    bootstrap.addBundle(new MultiPartBundle());
    bootstrap.addBundle(new CorsBundle());
    // authentication which requires the UserMapper from mybatis AFTER the mybatis bundle has run
    bootstrap.addBundle(auth);
    // register CoLTerms
    TermFactory.instance().registerTermEnum(ColDwcTerm.class);
    TermFactory.instance().registerTermEnum(ColdpTerm.class);
    // use a custom jackson mapper
    ObjectMapper om = ApiModule.configureMapper(Jackson.newMinimalObjectMapper());
    bootstrap.setObjectMapper(om);

    // add some cli commands not accessible via the admin interface
    bootstrap.addCommand(new InitDbCmd());
    bootstrap.addCommand(new IndexCmd());
    bootstrap.addCommand(new AddTableCmd());
    bootstrap.addCommand(new ExecSqlCmd());
  }

  @Override
  public String getName() {
    return "ws-server";
  }

  /**
   * Make sure to call this after the app has been bootstrapped, otherwise its null. Methods to add tasks or healthchecks
   * can be sure to use the session factory.
   */
  public SqlSessionFactory getSqlSessionFactory() {
    return mybatis.getSqlSessionFactory();
  }

  @VisibleForTesting
  public AuthBundle getAuthBundle() {
    return auth;
  }

  public EventBus getBus() {
    return bus;
  }

  public NameIndexImpl getNamesIndex() {
    return ni;
  }

  public ImportManager getImportManager() {
    return importManager;
  }

  @Override
  public void run(WsServerConfig cfg, Environment env) throws Exception {
    final JerseyEnvironment j = env.jersey();

    if (cfg.mkdirs()) {
      LOG.info("Created config repository directories");
    }
    clearTmp(cfg);
    
    // http client pool is managed via DW lifecycle already
    httpClient = new HttpClientBuilder(env).using(cfg.client).build(getName());

    // reuse the same http client pool also for jersey clients!
    JerseyClientBuilder builder = new JerseyClientBuilder(env)
        // .withProperty(CommonProperties.FEATURE_AUTO_DISCOVERY_DISABLE, Boolean.TRUE)
        .using(cfg.client)
        .using((ConnectorProvider) (cl,
            runtimeConfig) -> new DropwizardApacheConnector(httpClient, requestConfig(cfg.client), cfg.client.isChunkedEncodingEnabled()));
    // build both syncroneous and reactive clients sharing the same thread pool
  
    jerseyClient = builder.build(getName());

    // finally provide the SqlSessionFactory & http client to the auth and jersey bundle
    coljersey.setSqlSessionFactory(mybatis.getSqlSessionFactory());
    auth.setSqlSessionFactory(mybatis.getSqlSessionFactory());
    auth.setClient(httpClient);

    DatasetInfoCache.CACHE.setFactory(mybatis.getSqlSessionFactory());

    // name parser
    ParserConfigDao dao = new ParserConfigDao(getSqlSessionFactory());
    dao.loadParserConfigs();
    NameParser.PARSER.register(env.metrics());
    env.healthChecks().register("name-parser", new NameParserHealthCheck());
    env.lifecycle().manage(ManagedUtils.from(NameParser.PARSER));

    // time CSL Util
    CslUtil.register(env.metrics());
    env.healthChecks().register("csl-utils", new CslUtilsHealthCheck());

    // ES
    NameUsageIndexService indexService;
    NameUsageSearchService searchService;
    NameUsageSuggestionService suggestService;
    if (cfg.es == null) {
      LOG.warn("No Elastic Search configured, use pass through indexing & searching");
      indexService = NameUsageIndexService.passThru();
      searchService = NameUsageSearchService.passThru();
      suggestService = NameUsageSuggestionService.passThru();
    } else {
      final RestClient esClient = new EsClientFactory(cfg.es).createClient();
      env.lifecycle().manage(new ManagedEsClient(esClient));
      env.healthChecks().register("elastic", new EsHealthCheck(esClient, cfg.es));
      indexService = new NameUsageIndexServiceEs(esClient, cfg.es, getSqlSessionFactory());
      searchService = new NameUsageSearchServiceEs(cfg.es.nameUsage.name, esClient);
      suggestService = new NameUsageSuggestionServiceEs(cfg.es.nameUsage.name, esClient);
    }

    // images
    final ImageService imgService = new ImageServiceFS(cfg.img);

    // name index
    ni = NameIndexFactory.persistentOrMemory(cfg.namesIndexFile, getSqlSessionFactory(), AuthorshipNormalizer.INSTANCE);
    env.lifecycle().manage(ManagedUtils.stopOnly(ni));
    env.healthChecks().register("names-index", new NamesIndexHealthCheck(ni));

    final DatasetImportDao diDao = new DatasetImportDao(getSqlSessionFactory(), cfg.metricsRepo);
    final FileMetricsDatasetDao fmdDao = new FileMetricsDatasetDao(getSqlSessionFactory(), cfg.metricsRepo);
    final FileMetricsSectorDao fmsDao = new FileMetricsSectorDao(getSqlSessionFactory(), cfg.metricsRepo);

    // exporter
    AcExporter exporter = new AcExporter(cfg, getSqlSessionFactory());

    // release
    final ReleaseManager releaseManager = new ReleaseManager(diDao, ni, indexService, imgService, getSqlSessionFactory());

    // importer
    importManager = new ImportManager(cfg,
        env.metrics(),
        httpClient,
        getSqlSessionFactory(),
        ni,
        indexService,
        imgService,
        releaseManager);
    env.lifecycle().manage(ManagedUtils.stopOnly(importManager));
    ContinuousImporter cImporter = new ContinuousImporter(cfg.importer, importManager, getSqlSessionFactory());
    env.lifecycle().manage(ManagedUtils.stopOnly(cImporter));

    // gbif sync
    GbifSync gbifSync = new GbifSync(cfg.gbif, getSqlSessionFactory(), jerseyClient);
    env.lifecycle().manage(ManagedUtils.stopOnly(gbifSync));

    // assembly
    AssemblyCoordinator assembly = new AssemblyCoordinator(getSqlSessionFactory(), fmsDao, indexService, env.metrics());
    env.lifecycle().manage(assembly);

    // link assembly and import manager so they are aware of each other
    importManager.setAssemblyCoordinator(assembly);
    assembly.setImportManager(importManager);

    // diff
    DatasetDiffService dDiff = new DatasetDiffService(getSqlSessionFactory(), fmdDao);
    SectorDiffService sDiff = new SectorDiffService(getSqlSessionFactory(), fmsDao);
    env.healthChecks().register("dataset-diff", new DiffHealthCheck(dDiff));
    env.healthChecks().register("sector-diff", new DiffHealthCheck(sDiff));

    // update db lookups
    try (Connection c = mybatis.getConnection()) {
      LookupTables.recreateTables(c);
    }

    // daos
    DatasetDao ddao = new DatasetDao(getSqlSessionFactory(), new DownloadUtil(httpClient), imgService, diDao, indexService, cfg.normalizer::scratchFile, bus);
    DecisionDao decdao = new DecisionDao(getSqlSessionFactory(), indexService);
    EstimateDao edao = new EstimateDao(getSqlSessionFactory());
    NameDao ndao = new NameDao(getSqlSessionFactory(), indexService, ni);
    ReferenceDao rdao = new ReferenceDao(getSqlSessionFactory());
    SectorDao secdao = new SectorDao(getSqlSessionFactory(), indexService);
    SynonymDao sdao = new SynonymDao(getSqlSessionFactory());
    TaxonDao tdao = new TaxonDao(getSqlSessionFactory(), ndao, indexService);
    TreeDao trDao = new TreeDao(getSqlSessionFactory());
    UserDao udao = new UserDao(getSqlSessionFactory(), bus);

    // resources
    j.register(new AdminResource(getSqlSessionFactory(), assembly, new DownloadUtil(httpClient), cfg, imgService, ni, indexService, cImporter, importManager, gbifSync, ni));
    j.register(new DataPackageResource());
    j.register(new DatasetDiffResource(dDiff));
    j.register(new DatasetImportResource(diDao));
    j.register(new DatasetPatchResource());
    j.register(new DatasetResource(getSqlSessionFactory(), ddao, imgService, diDao, assembly, releaseManager));
    j.register(new DecisionResource(decdao));
    j.register(new DocsResource(cfg, OpenApiFactory.build(cfg, env)));
    j.register(new DuplicateResource());
    j.register(new EstimateResource(edao));
    j.register(new ExportResource(exporter));
    j.register(new ImporterResource(importManager, diDao));
    j.register(new LegacyWebserviceResource(getSqlSessionFactory(), cfg));
    j.register(new MatchingResource(ni));
    j.register(new NameResource(ndao));
    j.register(new NameUsageResource(searchService, suggestService));
    j.register(new NameUsageSearchResource(searchService, suggestService));
    j.register(new ReferenceResource(rdao));
    j.register(new SectorDiffResource(sDiff));
    j.register(new SectorResource(secdao, tdao, fmsDao, assembly));
    j.register(new SynonymResource(sdao));
    j.register(new TaxonResource(tdao));
    j.register(new TreeResource(tdao, trDao));
    j.register(new UserResource(auth.getJwtCodec(), udao));
    j.register(new VerbatimResource());
    j.register(new VocabResource());

    // parsers
    j.register(new NameParserResource(getSqlSessionFactory()));
    j.register(new ParserResource<>());

    // attach listeners to event bus
    bus.register(auth);
    bus.register(coljersey);
    bus.register(DatasetInfoCache.CACHE);
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
  
  private void clearTmp(WsServerConfig cfg) {
    LOG.info("Clear scratch dir {}", cfg.normalizer.scratchDir);
    if (cfg.normalizer.scratchDir != null && cfg.normalizer.scratchDir.exists()) {
      try {
        FileUtils.cleanDirectory(cfg.normalizer.scratchDir);
      } catch (IOException e) {
        LOG.error("Error cleaning scratch dir {}", cfg.normalizer.scratchDir, e);
      }
    }
  }
  
  /**
   * Mostly copied from HttpClientBuilder
   */
  private static RequestConfig requestConfig(JerseyClientConfiguration cfg) {
    final String cookiePolicy =
        cfg.isCookiesEnabled() ? CookieSpecs.DEFAULT : CookieSpecs.IGNORE_COOKIES;
    return RequestConfig.custom()
        .setCookieSpec(cookiePolicy)
        .setSocketTimeout((int) cfg.getTimeout().toMilliseconds())
        .setConnectTimeout((int) cfg.getConnectionTimeout().toMilliseconds())
        .setConnectionRequestTimeout((int) cfg.getConnectionRequestTimeout().toMilliseconds())
        .build();
  }
}
