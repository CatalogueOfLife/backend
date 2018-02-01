package org.col.dw;

import io.dropwizard.client.DropwizardApacheConnector;
import io.dropwizard.client.HttpClientBuilder;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.col.dw.command.initdb.InitDbCmd;
import org.col.dw.command.neoshell.ShellCmd;
import org.col.dw.config.AdminServerConfig;
import org.col.dw.resources.ImporterResource;
import org.col.dw.task.gbifsync.GbifSync;
import org.col.dw.task.importer.ContinousImporter;
import org.col.dw.task.importer.ImportManager;
import org.glassfish.jersey.client.rx.RxClient;
import org.glassfish.jersey.client.rx.java8.RxCompletionStageInvoker;
import org.glassfish.jersey.client.spi.ConnectorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

public class AdminServer extends PgApp<AdminServerConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(AdminServer.class);
  public static final String MDC_KEY_TASK = "task";

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

    // add some cli commands not accessible via the admin interface
    bootstrap.addCommand(new InitDbCmd());
    bootstrap.addCommand(new ShellCmd());
  }

  @Override
  public void run(AdminServerConfig cfg, Environment env) {
    super.run(cfg, env);

    // http client pool is managed via DW lifecycle already
    final CloseableHttpClient hc = new HttpClientBuilder(env)
        .using(cfg.client)
        .build(getName());

    // reuse the same http client pool also for jersey clients!
    final RxClient<RxCompletionStageInvoker> client = new JerseyClientBuilder(env)
        .using(cfg.client)
        .using((ConnectorProvider) (cl, runtimeConfig) -> new DropwizardApacheConnector(hc, requestConfig(cfg.client), cfg.client.isChunkedEncodingEnabled()))
        .buildRx(getName(), RxCompletionStageInvoker.class);

    // setup async importer
    final ImportManager importManager = new ImportManager(cfg, hc, getSqlSessionFactory());
    env.lifecycle().manage(importManager);
    env.jersey().register(new ImporterResource(importManager, getSqlSessionFactory()));

    if (cfg.importer.continousImportPolling > 0) {
      LOG.info("Enable continuous importing");
      env.lifecycle().manage(new ContinousImporter(cfg.importer, importManager, getSqlSessionFactory()));
    }

    // activate gbif sync?
    if (cfg.gbif.syncFrequency > 0) {
      LOG.info("Enable GBIF dataset sync");
      env.lifecycle().manage(new GbifSync(cfg.gbif, getSqlSessionFactory(), client));
    } else {
      LOG.warn("GBIF registry sync is deactivated. Please configure server with a positive gbif.syncFrequency");
    }
  }

  /**
   * Mostly copied from HttpClientBuilder
   */
  private static RequestConfig requestConfig(JerseyClientConfiguration cfg) {
    final String cookiePolicy = cfg.isCookiesEnabled() ? CookieSpecs.DEFAULT : CookieSpecs.IGNORE_COOKIES;
    return RequestConfig.custom().setCookieSpec(cookiePolicy)
        .setSocketTimeout((int)cfg.getTimeout().toMilliseconds())
        .setConnectTimeout((int)cfg.getConnectionTimeout().toMilliseconds())
        .setConnectionRequestTimeout((int)cfg.getConnectionRequestTimeout().toMilliseconds())
        .build();
  }

}
