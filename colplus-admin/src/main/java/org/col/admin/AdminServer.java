package org.col.admin;

import io.dropwizard.client.DropwizardApacheConnector;
import io.dropwizard.client.HttpClientBuilder;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.col.admin.command.initdb.InitDbCmd;
import org.col.admin.command.neoshell.ShellCmd;
import org.col.admin.config.AdminServerConfig;
import org.col.admin.gbifsync.GbifSync;
import org.col.admin.importer.ContinuousImporter;
import org.col.admin.importer.ImportManager;
import org.col.admin.matching.NameIndex;
import org.col.admin.matching.NameIndexFactory;
import org.col.admin.resources.ImporterResource;
import org.col.admin.resources.MatchingResource;
import org.col.api.vocab.ColTerm;
import org.col.api.vocab.Datasets;
import org.col.dw.PgApp;
import org.col.dw.es.ManagedEsClient;
import org.col.es.EsClientFactory;
import org.elasticsearch.client.RestClient;
import org.gbif.dwc.terms.TermFactory;
import org.glassfish.jersey.client.rx.RxClient;
import org.glassfish.jersey.client.rx.java8.RxCompletionStageInvoker;
import org.glassfish.jersey.client.spi.ConnectorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

public class AdminServer extends PgApp<AdminServerConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(AdminServer.class);
  // milliseconds to wait during shutdown before forcing a shutdown
  public static final int MILLIS_TO_DIE = 12000;

  public static void main(final String[] args) throws Exception {
    SLF4JBridgeHandler.install();
    new AdminServer().run(args);
  }

  @Override
  public String getName() {
    return "admin-server";
  }

  @Override
  public void initialize(Bootstrap<AdminServerConfig> bootstrap) {
    super.initialize(bootstrap);

    // register CoLTerms
    TermFactory.instance().registerTermEnum(ColTerm.class);

    // add some cli commands not accessible via the admin interface
    bootstrap.addCommand(new InitDbCmd());
    bootstrap.addCommand(new ShellCmd());
  }

  @Override
  public void run(AdminServerConfig cfg, Environment env) {
    super.run(cfg, env);

    // add custom index
    env.admin().addServlet("index-menu", new IndexServlet(cfg)).addMapping("");

    // http client pool is managed via DW lifecycle already
    final CloseableHttpClient hc = new HttpClientBuilder(env).using(cfg.client).build(getName());

    // reuse the same http client pool also for jersey clients!
    final RxClient<RxCompletionStageInvoker> client = new JerseyClientBuilder(env).using(cfg.client)
        .using((ConnectorProvider) (cl, runtimeConfig) -> new DropwizardApacheConnector(hc,
            requestConfig(cfg.client), cfg.client.isChunkedEncodingEnabled()))
        .buildRx(getName(), RxCompletionStageInvoker.class);

    // name index
    NameIndex ni;
    if (cfg.namesIndexFile == null) {
      LOG.info("Using volatile in memory names index");
      ni = NameIndexFactory.memory(Datasets.PROV_CAT, getSqlSessionFactory());
    } else {
      LOG.info("Using names index at {}", cfg.namesIndexFile.getAbsolutePath());
      ni = NameIndexFactory.persistent(Datasets.PROV_CAT, cfg.namesIndexFile,
          getSqlSessionFactory());
    }
    env.jersey().register(new MatchingResource(ni));

    RestClient esClient = new EsClientFactory(cfg.es).createClient();
    env.lifecycle().manage(new ManagedEsClient(esClient));

    // setup async importer
    final ImportManager importManager =
        new ImportManager(cfg, env.metrics(), hc, getSqlSessionFactory(), ni, esClient);
    env.lifecycle().manage(importManager);
    env.jersey().register(new ImporterResource(importManager, getSqlSessionFactory()));

    if (cfg.importer.continousImportPolling > 0) {
      LOG.info("Enable continuous importing");
      env.lifecycle()
          .manage(new ContinuousImporter(cfg.importer, importManager, getSqlSessionFactory()));
    } else {
      LOG.warn("Disable continuous importing");
    }

    // activate gbif sync?
    if (cfg.gbif.syncFrequency > 0) {
      LOG.info("Enable GBIF dataset sync");
      env.lifecycle().manage(new GbifSync(cfg.gbif, getSqlSessionFactory(), client));
    } else {
      LOG.warn("Disable GBIF dataset sync");
    }
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
