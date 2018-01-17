package org.col;

import io.dropwizard.client.DropwizardApacheConnector;
import io.dropwizard.client.HttpClientBuilder;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.col.command.initdb.InitDbCmd;
import org.col.command.neoshell.ShellCmd;
import org.col.config.TaskServerConfig;
import org.col.resources.ImporterResource;
import org.col.task.gbifsync.GbifSync;
import org.col.task.importer.ContinousImporter;
import org.col.task.importer.ImportManager;
import org.glassfish.jersey.client.rx.RxClient;
import org.glassfish.jersey.client.rx.java8.RxCompletionStageInvoker;
import org.glassfish.jersey.client.spi.ConnectorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

public class TaskServer extends PgApp<TaskServerConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(TaskServer.class);
  public static final String MDC_KEY_TASK = "task";

  public static void main(final String[] args) throws Exception {
    SLF4JBridgeHandler.install();
    new TaskServer().run(args);
  }

  @Override
  public String getName() {
    return "task-server";
  }

  @Override
  public void initialize(Bootstrap<TaskServerConfig> bootstrap) {
    super.initialize(bootstrap);

    // add some cli commands not accessible via the admin interface
    bootstrap.addCommand(new InitDbCmd());
    bootstrap.addCommand(new ShellCmd());
  }

  @Override
  public void run(TaskServerConfig cfg, Environment env) {
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
