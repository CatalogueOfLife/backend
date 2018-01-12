package org.col.task;

import io.dropwizard.client.DropwizardApacheConnector;
import io.dropwizard.client.HttpClientBuilder;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.col.PgApp;
import org.col.command.initdb.InitDbCmd;
import org.col.command.neoshell.ShellCmd;
import org.col.task.common.TaskServerConfig;
import org.col.task.gbifsync.GbifSyncTask;
import org.col.task.hello.HelloTask;
import org.col.task.importer.ImporterTask;
import org.glassfish.jersey.client.rx.RxClient;
import org.glassfish.jersey.client.rx.java8.RxCompletionStageInvoker;
import org.glassfish.jersey.client.spi.ConnectorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

public class TaskServer extends PgApp<TaskServerConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(TaskServer.class);

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

    // tasks
    LOG.debug("Adding tasks");
    env.admin().addTask(new HelloTask());
    env.admin().addTask(new GbifSyncTask(cfg.gbif, getSqlSessionFactory(), client));
    env.admin().addTask(new ImporterTask(cfg, getSqlSessionFactory(), hc));
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
