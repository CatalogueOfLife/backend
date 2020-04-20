package life.catalogue;

import java.io.IOException;
import javax.ws.rs.client.Client;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import life.catalogue.command.updatedb.ExecSqlCmd;
import life.catalogue.dao.*;
import life.catalogue.resources.*;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.Application;
import io.dropwizard.client.DropwizardApacheConnector;
import io.dropwizard.client.HttpClientBuilder;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.forms.MultiPartBundle;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import life.catalogue.api.datapackage.ColdpTerm;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.vocab.ColDwcTerm;
import life.catalogue.assembly.AssemblyCoordinator;
import life.catalogue.command.es.IndexCmd;
import life.catalogue.command.initdb.InitDbCmd;
import life.catalogue.command.updatedb.AddTableCmd;
import life.catalogue.common.csl.CslUtil;
import life.catalogue.common.io.DownloadUtil;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.db.tree.DiffService;
import life.catalogue.dw.ManagedCloseable;
import life.catalogue.dw.auth.AuthBundle;
import life.catalogue.dw.cors.CorsBundle;
import life.catalogue.dw.db.MybatisBundle;
import life.catalogue.dw.es.ManagedEsClient;
import life.catalogue.dw.health.CslUtilsHealthCheck;
import life.catalogue.dw.health.DiffHealthCheck;
import life.catalogue.dw.health.EsHealthCheck;
import life.catalogue.dw.health.NameParserHealthCheck;
import life.catalogue.dw.health.NamesIndexHealthCheck;
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
import life.catalogue.matching.NameIndex;
import life.catalogue.matching.NameIndexFactory;
import life.catalogue.parser.NameParser;
import life.catalogue.release.AcExporter;
import life.catalogue.release.ReleaseManager;
import life.catalogue.resources.parser.NameParserResource;
import life.catalogue.resources.parser.ParserResource;

public class WsServer extends Application<WsServerConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(WsServer.class);

  private final MybatisBundle mybatis = new MybatisBundle();
  private final AuthBundle auth = new AuthBundle();
  private final EventBus bus = new EventBus("bus");
  protected CloseableHttpClient httpClient;
  protected Client jerseyClient;
  private NameIndex ni;
  
  public static void main(final String[] args) throws Exception {
    SLF4JBridgeHandler.install();
    new WsServer().run(args);
  }

  @Override
  public void initialize(Bootstrap<WsServerConfig> bootstrap) {
    // our mybatis classes
    bootstrap.addBundle(mybatis);
    // various custom jersey providers
    bootstrap.addBundle(new ColJerseyBundle());
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

  @Override
  public void run(WsServerConfig cfg, Environment env) throws Exception {
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

    // finally provide the SqlSessionFactory & http client to the auth bundle
    auth.setSqlSessionFactory(mybatis.getSqlSessionFactory());
    auth.setClient(httpClient);

    // name parser
    ParserConfigDao dao = new ParserConfigDao(getSqlSessionFactory());
    dao.loadParserConfigs();
    NameParser.PARSER.register(env.metrics());
    env.healthChecks().register("name-parser", new NameParserHealthCheck());
    env.lifecycle().manage(new ManagedCloseable(NameParser.PARSER));

    // time CSL Util
    CslUtil.register(env.metrics());
    env.healthChecks().register("csl-utils", new CslUtilsHealthCheck());

    // ES
    NameUsageIndexService indexService;
    NameUsageSearchService searchService;
    NameUsageSuggestionService suggestService;
    if (cfg.es.hosts == null) {
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
    env.lifecycle().manage(new ManagedCloseable(ni));
    env.healthChecks().register("names-index", new NamesIndexHealthCheck(ni));

    final DatasetImportDao diDao = new DatasetImportDao(getSqlSessionFactory(), cfg.metricsRepo);

    // exporter
    AcExporter exporter = new AcExporter(cfg, getSqlSessionFactory());

    // release
    final ReleaseManager releaseManager = new ReleaseManager(exporter, diDao, indexService, getSqlSessionFactory());

    // async importer
    final ImportManager importManager = new ImportManager(cfg,
        env.metrics(),
        httpClient,
        getSqlSessionFactory(),
        ni,
        indexService,
        imgService,
        releaseManager);
    env.lifecycle().manage(importManager);
    env.jersey().register(new ImporterResource(importManager, diDao));
    ContinuousImporter cImporter = new ContinuousImporter(cfg.importer, importManager, getSqlSessionFactory());
    env.lifecycle().manage(cImporter);

    // gbif sync
    GbifSync gbifSync = new GbifSync(cfg.gbif, getSqlSessionFactory(), jerseyClient);
    env.lifecycle().manage(gbifSync);

    // assembly
    AssemblyCoordinator assembly = new AssemblyCoordinator(getSqlSessionFactory(), diDao, indexService, env.metrics());
    env.lifecycle().manage(assembly);

    // link assembly and import manager so they are aware of each other
    importManager.setAssemblyCoordinator(assembly);
    assembly.setImportManager(importManager);

    // diff
    DiffService diff = new DiffService(getSqlSessionFactory(), diDao.getTreeDao());
    env.healthChecks().register("diff", new DiffHealthCheck(diff));

    // daos
    NameDao ndao = new NameDao(getSqlSessionFactory());
    TaxonDao tdao = new TaxonDao(getSqlSessionFactory(), ndao, indexService);
    ReferenceDao rdao = new ReferenceDao(getSqlSessionFactory());
    SynonymDao sdao = new SynonymDao(getSqlSessionFactory());
    TreeDao trDao = new TreeDao(getSqlSessionFactory());
    DatasetDao ddao = new DatasetDao(getSqlSessionFactory(), new DownloadUtil(httpClient), imgService, diDao, indexService, cfg.normalizer::scratchFile, bus);
    DecisionDao decdao = new DecisionDao(getSqlSessionFactory(), indexService);
    EstimateDao edao = new EstimateDao(getSqlSessionFactory());
    SectorDao secdao = new SectorDao(getSqlSessionFactory());
    UserDao udao = new UserDao(getSqlSessionFactory(), bus);

    // resources
    env.jersey()
        .register(new AdminResource(getSqlSessionFactory(),
            new DownloadUtil(httpClient),
            cfg,
            imgService,
            ni,
            indexService,
            cImporter,
            gbifSync));
    env.jersey().register(new AssemblyResource(getSqlSessionFactory(), tdao, assembly, exporter, releaseManager));
    env.jersey().register(new DataPackageResource());
    env.jersey().register(new DatasetResource(getSqlSessionFactory(), ddao, imgService, diDao, diff, exporter));
    env.jersey().register(new DecisionResource(decdao));
    env.jersey().register(new DocsResource(cfg));
    env.jersey().register(new DuplicateResource());
    env.jersey().register(new EstimateResource(edao));
    env.jersey().register(new MatchingResource(ni));
    env.jersey().register(new NameResource(ndao));
    env.jersey().register(new NameUsageResource(searchService, suggestService));
    env.jersey().register(new NameUsageSearchResource(searchService, suggestService));
    env.jersey().register(new ReferenceResource(rdao));
    env.jersey().register(new SectorResource(secdao, diDao, diff, assembly));
    env.jersey().register(new SynonymResource(sdao));
    env.jersey().register(new TaxonResource(tdao));
    env.jersey().register(new TreeResource(tdao, trDao));
    env.jersey().register(new UserResource(auth.getJwtCodec(), auth.getIdentityService(), udao));
    env.jersey().register(new VerbatimResource());
    env.jersey().register(new VocabResource());
    env.jersey().register(new LEGACYDecisionResource(getSqlSessionFactory(), decdao));
    env.jersey().register(new LEGACYEstimateResource(getSqlSessionFactory(), edao));
    env.jersey().register(new LEGACYSectorResource(getSqlSessionFactory(), secdao, diDao, diff, assembly));
    // parsers
    env.jersey().register(new NameParserResource(getSqlSessionFactory()));
    env.jersey().register(new ParserResource<>());
  }

  private void setupEventBus(Object... listeners){
    for (Object obj : listeners) {
    }
  }

  @Override
  protected void onFatalError() {
    if (ni != null) {
      try {
        LOG.error("Fatal statup error, closing names index gracefully");
        ni.close();
      } catch (Exception e) {
        LOG.error("Failed to shutdown names index", e);
      }
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
