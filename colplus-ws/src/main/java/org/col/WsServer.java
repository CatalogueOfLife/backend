package org.col;

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
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.datapackage.ColdpTerm;
import org.col.api.jackson.ApiModule;
import org.col.api.vocab.ColDwcTerm;
import org.col.assembly.AssemblyCoordinator;
import org.col.command.es.IndexAllCmd;
import org.col.command.export.AcExporter;
import org.col.command.export.ExportCmd;
import org.col.command.initdb.InitDbCmd;
import org.col.command.neoshell.ShellCmd;
import org.col.common.io.DownloadUtil;
import org.col.common.tax.AuthorshipNormalizer;
import org.col.dao.*;
import org.col.db.tree.DiffService;
import org.col.dw.ManagedCloseable;
import org.col.dw.auth.AuthBundle;
import org.col.dw.cors.CorsBundle;
import org.col.dw.db.MybatisBundle;
import org.col.dw.es.ManagedEsClient;
import org.col.dw.health.DiffHealthCheck;
import org.col.dw.health.NameParserHealthCheck;
import org.col.dw.health.NamesIndexHealthCheck;
import org.col.dw.jersey.ColJerseyBundle;
import org.col.es.EsClientFactory;
import org.col.es.NameUsageIndexService;
import org.col.es.NameUsageIndexServiceEs;
import org.col.es.NameUsageSearchService;
import org.col.gbifsync.GbifSync;
import org.col.img.ImageService;
import org.col.img.ImageServiceFS;
import org.col.importer.ContinuousImporter;
import org.col.importer.ImportManager;
import org.col.matching.NameIndex;
import org.col.matching.NameIndexFactory;
import org.col.parser.NameParser;
import org.col.resources.*;
import org.elasticsearch.client.RestClient;
import org.gbif.dwc.terms.TermFactory;
import org.glassfish.jersey.client.rx.RxClient;
import org.glassfish.jersey.client.rx.java8.RxCompletionStageInvoker;
import org.glassfish.jersey.client.spi.ConnectorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import static org.col.es.EsConfig.ES_INDEX_NAME_USAGE;

public class WsServer extends Application<WsServerConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(WsServer.class);
  // milliseconds to wait during shutdown before forcing a shutdown
  public static final int MILLIS_TO_DIE = 12000;
  
  private final MybatisBundle mybatis = new MybatisBundle();
  private final AuthBundle auth = new AuthBundle();
  protected CloseableHttpClient httpClient;
  protected RxClient<RxCompletionStageInvoker> jerseyRxClient;
  
  
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
    bootstrap.addCommand(new ShellCmd());
    bootstrap.addCommand(new IndexAllCmd());
    bootstrap.addCommand(new ExportCmd());
  }
  
  @Override
  public String getName() {
    return "ws-server";
  }
  
  /**
   * Make sure to call this after the app has been bootstrapped, otherwise its null.
   * Methods to add tasks or healthchecks can be sure to use the session factory.
   */
  public SqlSessionFactory getSqlSessionFactory() {
    return mybatis.getSqlSessionFactory();
  }
  
  @Override
  public void run(WsServerConfig cfg, Environment env) throws Exception {
    // http client pool is managed via DW lifecycle already
    httpClient = new HttpClientBuilder(env).using(cfg.client).build(getName());
  
    // reuse the same http client pool also for jersey clients!
    JerseyClientBuilder builder = new JerseyClientBuilder(env)
        //.withProperty(CommonProperties.FEATURE_AUTO_DISCOVERY_DISABLE, Boolean.TRUE)
        .using(cfg.client)
        .using((ConnectorProvider) (cl, runtimeConfig) ->
            new DropwizardApacheConnector(httpClient, requestConfig(cfg.client), cfg.client.isChunkedEncodingEnabled())
        );
    // build both syncroneous and reactive clients sharing the same thread pool
  
  
    jerseyRxClient = builder.buildRx(getName(), RxCompletionStageInvoker.class);
  
    // finally provide the SqlSessionFactory & http client
    auth.getIdentityService().setSqlSessionFactory(mybatis.getSqlSessionFactory());
    auth.getIdentityService().setClient(httpClient);
  
    // name parser
    NameParser.PARSER.register(env.metrics());
    env.healthChecks().register("name-parser", new NameParserHealthCheck());
    
    // authorship lookup & norm
    AuthorshipNormalizer aNormalizer = AuthorshipNormalizer.createWithAuthormap();
  
    // ES
    final RestClient esClient = new EsClientFactory(cfg.es).createClient();

    NameUsageIndexService indexService;
    if (esClient == null) {
      LOG.warn("No Elastic Search configured, use pass through indexing");
      indexService = NameUsageIndexService.passThru();
    } else {
      env.lifecycle().manage(new ManagedEsClient(esClient));
      indexService = new NameUsageIndexServiceEs(esClient, cfg.es, getSqlSessionFactory());
    }
    NameUsageSearchService nuss = new NameUsageSearchService(cfg.es.indexName(ES_INDEX_NAME_USAGE), esClient);
    
    // images
    final ImageService imgService = new ImageServiceFS(cfg.img);
  
    // name index
    NameIndex ni = NameIndexFactory.persistentOrMemory(cfg.namesIndexFile, getSqlSessionFactory(), aNormalizer);
    env.lifecycle().manage(new ManagedCloseable(ni));
    env.healthChecks().register("names-index", new NamesIndexHealthCheck(ni));
    
    final DatasetImportDao diDao = new DatasetImportDao(getSqlSessionFactory(), cfg.textTreeRepo);
    
    // async importer
    final ImportManager importManager = new ImportManager(cfg, env.metrics(), httpClient, getSqlSessionFactory(),
        aNormalizer, ni, indexService, imgService);
    env.lifecycle().manage(importManager);
    env.jersey().register(new ImporterResource(importManager, diDao));
  
    if (cfg.importer.continousImportPolling > 0) {
      LOG.info("Enable continuous importing");
      env.lifecycle().manage(new ContinuousImporter(cfg.importer, importManager, getSqlSessionFactory()));
    } else {
      LOG.warn("Disable continuous importing");
    }
  
    // activate gbif sync?
    if (cfg.gbif.syncFrequency > 0) {
      LOG.info("Enable GBIF dataset sync");
      env.lifecycle().manage(new GbifSync(cfg.gbif, getSqlSessionFactory(), jerseyRxClient));
    } else {
      LOG.warn("Disable GBIF dataset sync");
    }
  
    // exporter
    AcExporter exporter = new AcExporter(cfg);
    
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
    TaxonDao tdao = new TaxonDao(getSqlSessionFactory());
    NameDao ndao = new NameDao(getSqlSessionFactory(), aNormalizer);
    ReferenceDao rdao = new ReferenceDao(getSqlSessionFactory());
    SynonymDao sdao = new SynonymDao(getSqlSessionFactory());
    
    // resources
    env.jersey().register(new AdminResource(getSqlSessionFactory(), new DownloadUtil(httpClient), cfg.normalizer, imgService, tdao));
    env.jersey().register(new AssemblyResource(assembly, exporter));
    env.jersey().register(new DataPackageResource());
    env.jersey().register(new DatasetResource(getSqlSessionFactory(), imgService, cfg, new DownloadUtil(httpClient), diff));
    env.jersey().register(new DecisionResource(getSqlSessionFactory(), indexService));
    env.jersey().register(new DocsResource(cfg));
    env.jersey().register(new DuplicateResource());
    env.jersey().register(new MatchingResource(ni));
    env.jersey().register(new NameResource(nuss, ndao));
    env.jersey().register(new NameSearchResource(nuss));
    env.jersey().register(new ParserResource());
    env.jersey().register(new ReferenceResource(rdao));
    env.jersey().register(new SectorResource(getSqlSessionFactory(), diDao, diff));
    env.jersey().register(new SynonymResource(sdao));
    env.jersey().register(new TaxonResource(tdao));
    env.jersey().register(new TreeResource(tdao));
    env.jersey().register(new UserResource(auth.getJwtCodec()));
    env.jersey().register(new VerbatimResource());
    env.jersey().register(new VocabResource());
  }
  
  /**
   * Mostly copied from HttpClientBuilder
   */
  private static RequestConfig requestConfig(JerseyClientConfiguration cfg) {
    final String cookiePolicy =
        cfg.isCookiesEnabled() ? CookieSpecs.DEFAULT : CookieSpecs.IGNORE_COOKIES;
    return RequestConfig.custom().setCookieSpec(cookiePolicy)
        .setSocketTimeout((int) cfg.getTimeout().toMilliseconds())
        .setConnectTimeout((int) cfg.getConnectionTimeout().toMilliseconds())
        .setConnectionRequestTimeout((int) cfg.getConnectionRequestTimeout().toMilliseconds())
        .build();
  }
}
