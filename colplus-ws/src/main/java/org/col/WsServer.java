package org.col;

import com.fasterxml.jackson.databind.ObjectMapper;

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
import org.col.common.csl.CslUtil;
import org.col.common.io.DownloadUtil;
import org.col.common.tax.AuthorshipNormalizer;
import org.col.dao.DatasetImportDao;
import org.col.dao.NameDao;
import org.col.dao.ReferenceDao;
import org.col.dao.SynonymDao;
import org.col.dao.TaxonDao;
import org.col.db.tree.DiffService;
import org.col.dw.ManagedCloseable;
import org.col.dw.auth.AuthBundle;
import org.col.dw.cors.CorsBundle;
import org.col.dw.db.MybatisBundle;
import org.col.dw.es.ManagedEsClient;
import org.col.dw.health.CslUtilsHealthCheck;
import org.col.dw.health.DiffHealthCheck;
import org.col.dw.health.EsHealthCheck;
import org.col.dw.health.NameParserHealthCheck;
import org.col.dw.health.NamesIndexHealthCheck;
import org.col.dw.jersey.ColJerseyBundle;
import org.col.es.EsClientFactory;
import org.col.es.name.index.NameUsageIndexService;
import org.col.es.name.index.NameUsageIndexServiceEs;
import org.col.es.name.search.NameUsageSearchService;
import org.col.es.name.search.NameUsageSearchServiceEs;
import org.col.es.name.suggest.NameSuggestionService;
import org.col.es.name.suggest.NameSuggestionServiceEs;
import org.col.gbifsync.GbifSync;
import org.col.img.ImageService;
import org.col.img.ImageServiceFS;
import org.col.importer.ContinuousImporter;
import org.col.importer.ImportManager;
import org.col.matching.NameIndex;
import org.col.matching.NameIndexFactory;
import org.col.parser.NameParser;
import org.col.resources.AdminResource;
import org.col.resources.AssemblyResource;
import org.col.resources.DataPackageResource;
import org.col.resources.DatasetResource;
import org.col.resources.DecisionResource;
import org.col.resources.DocsResource;
import org.col.resources.DuplicateResource;
import org.col.resources.EstimateResource;
import org.col.resources.ImporterResource;
import org.col.resources.MatchingResource;
import org.col.resources.NameResource;
import org.col.resources.NameSearchResource;
import org.col.resources.ParserResource;
import org.col.resources.ReferenceResource;
import org.col.resources.SectorResource;
import org.col.resources.SynonymResource;
import org.col.resources.TaxonResource;
import org.col.resources.TreeResource;
import org.col.resources.UserResource;
import org.col.resources.VerbatimResource;
import org.col.resources.VocabResource;
import org.elasticsearch.client.RestClient;
import org.gbif.dwc.terms.TermFactory;
import org.glassfish.jersey.client.rx.RxClient;
import org.glassfish.jersey.client.rx.java8.RxCompletionStageInvoker;
import org.glassfish.jersey.client.spi.ConnectorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import io.dropwizard.Application;
import io.dropwizard.client.DropwizardApacheConnector;
import io.dropwizard.client.HttpClientBuilder;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.forms.MultiPartBundle;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

import static org.col.es.EsConfig.ES_INDEX_NAME_USAGE;

public class WsServer extends Application<WsServerConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(WsServer.class);

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
    bootstrap.addCommand(new IndexAllCmd());
    bootstrap.addCommand(new ExportCmd());
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

  @Override
  public void run(WsServerConfig cfg, Environment env) throws Exception {
    // http client pool is managed via DW lifecycle already
    httpClient = new HttpClientBuilder(env).using(cfg.client).build(getName());

    // reuse the same http client pool also for jersey clients!
    JerseyClientBuilder builder = new JerseyClientBuilder(env)
        // .withProperty(CommonProperties.FEATURE_AUTO_DISCOVERY_DISABLE, Boolean.TRUE)
        .using(cfg.client)
        .using((ConnectorProvider) (cl,
            runtimeConfig) -> new DropwizardApacheConnector(httpClient, requestConfig(cfg.client), cfg.client.isChunkedEncodingEnabled()));
    // build both syncroneous and reactive clients sharing the same thread pool

    jerseyRxClient = builder.buildRx(getName(), RxCompletionStageInvoker.class);

    // finally provide the SqlSessionFactory & http client
    auth.getIdentityService().setSqlSessionFactory(mybatis.getSqlSessionFactory());
    auth.getIdentityService().setClient(httpClient);

    // name parser
    NameParser.PARSER.register(env.metrics());
    env.healthChecks().register("name-parser", new NameParserHealthCheck());

    // time CSL Util
    CslUtil.register(env.metrics());
    env.healthChecks().register("csl-utils", new CslUtilsHealthCheck());

    // authorship lookup & norm
    AuthorshipNormalizer aNormalizer = AuthorshipNormalizer.createWithAuthormap();

    // ES
    NameUsageIndexService svcIndex;
    NameUsageSearchService svcNameSearch;
    NameSuggestionService svcSuggest;
    if (cfg.es.hosts == null) {
      LOG.warn("No Elastic Search configured, use pass through indexing & searching");
      svcIndex = NameUsageIndexService.passThru();
      svcNameSearch = NameUsageSearchService.passThru();
      svcSuggest = NameSuggestionService.passThru();
    } else {
      final RestClient esClient = new EsClientFactory(cfg.es).createClient();
      env.lifecycle().manage(new ManagedEsClient(esClient));
      env.healthChecks().register("elastic", new EsHealthCheck(esClient, cfg.es));
      svcIndex = new NameUsageIndexServiceEs(esClient, cfg.es, getSqlSessionFactory());
      svcNameSearch = new NameUsageSearchServiceEs(cfg.es.indexName(ES_INDEX_NAME_USAGE), esClient);
      svcSuggest = new NameSuggestionServiceEs(cfg.es.indexName(ES_INDEX_NAME_USAGE), esClient);
    }

    // images
    final ImageService imgService = new ImageServiceFS(cfg.img);

    // name index
    NameIndex ni = NameIndexFactory.persistentOrMemory(cfg.namesIndexFile, getSqlSessionFactory(), aNormalizer);
    env.lifecycle().manage(new ManagedCloseable(ni));
    env.healthChecks().register("names-index", new NamesIndexHealthCheck(ni));

    final DatasetImportDao diDao = new DatasetImportDao(getSqlSessionFactory(), cfg.textTreeRepo);

    // async importer
    final ImportManager importManager = new ImportManager(cfg,
        env.metrics(),
        httpClient,
        getSqlSessionFactory(),
        aNormalizer,
        ni,
        svcIndex,
        imgService);
    env.lifecycle().manage(importManager);
    env.jersey().register(new ImporterResource(importManager, diDao));
    ContinuousImporter cImporter = new ContinuousImporter(cfg.importer, importManager, getSqlSessionFactory());
    env.lifecycle().manage(cImporter);

    // gbif sync
    GbifSync gbifSync = new GbifSync(cfg.gbif, getSqlSessionFactory(), jerseyRxClient);
    env.lifecycle().manage(gbifSync);

    // exporter
    AcExporter exporter = new AcExporter(cfg);

    // assembly
    AssemblyCoordinator assembly = new AssemblyCoordinator(getSqlSessionFactory(), diDao, svcIndex, env.metrics());
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
    env.jersey()
        .register(new AdminResource(getSqlSessionFactory(),
            new DownloadUtil(httpClient),
            cfg,
            imgService,
            svcIndex,
            tdao,
            cImporter,
            gbifSync));
    env.jersey().register(new AssemblyResource(getSqlSessionFactory(), assembly, exporter));
    env.jersey().register(new DataPackageResource());
    env.jersey().register(new DatasetResource(getSqlSessionFactory(), imgService, cfg, new DownloadUtil(httpClient), diff));
    env.jersey().register(new DecisionResource(getSqlSessionFactory(), svcIndex));
    env.jersey().register(new DocsResource(cfg));
    env.jersey().register(new DuplicateResource());
    env.jersey().register(new EstimateResource(getSqlSessionFactory()));
    env.jersey().register(new MatchingResource(ni));
    env.jersey().register(new NameResource(svcNameSearch, ndao, svcSuggest));
    env.jersey().register(new NameSearchResource(svcNameSearch, svcSuggest));
    env.jersey().register(new ParserResource());
    env.jersey().register(new ReferenceResource(rdao));
    env.jersey().register(new SectorResource(getSqlSessionFactory(), diDao, diff, assembly));
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
    return RequestConfig.custom()
        .setCookieSpec(cookiePolicy)
        .setSocketTimeout((int) cfg.getTimeout().toMilliseconds())
        .setConnectTimeout((int) cfg.getConnectionTimeout().toMilliseconds())
        .setConnectionRequestTimeout((int) cfg.getConnectionRequestTimeout().toMilliseconds())
        .build();
  }
}
