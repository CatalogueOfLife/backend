package org.col.dw;

import io.dropwizard.Application;
import io.dropwizard.client.DropwizardApacheConnector;
import io.dropwizard.client.HttpClientBuilder;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.forms.MultiPartBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.jackson.ApiModule;
import org.col.dw.auth.AuthBundle;
import org.col.dw.auth.JwtCodec;
import org.col.dw.cors.CorsBundle;
import org.col.dw.db.MybatisBundle;
import org.col.dw.health.NameParserHealthCheck;
import org.col.dw.jersey.ColJerseyBundle;
import org.col.parser.NameParser;
import org.glassfish.jersey.client.rx.RxClient;
import org.glassfish.jersey.client.rx.java8.RxCompletionStageInvoker;
import org.glassfish.jersey.client.spi.ConnectorProvider;

public abstract class PgApp<T extends PgAppConfig> extends Application<T> {

  private final MybatisBundle mybatis = new MybatisBundle();
  private final AuthBundle auth = new AuthBundle();
  protected CloseableHttpClient httpClient;
  protected RxClient<RxCompletionStageInvoker> jerseyRxClient;

  @Override
	public void initialize(final Bootstrap<T> bootstrap) {
		// our mybatis classes
		bootstrap.addBundle(mybatis);
		// various custom jersey providers
		bootstrap.addBundle(new ColJerseyBundle());
    bootstrap.addBundle(new MultiPartBundle());
    bootstrap.addBundle(new CorsBundle());
    // authentication which requires the UserMapper from mybatis AFTER the mybatis bundle has run
    bootstrap.addBundle(auth);
    // customize jackson
    ApiModule.configureMapper(bootstrap.getObjectMapper());
  }

  /**
   * Make sure to call this after the app has been bootstrapped, otherwise its null.
   * Methods to add tasks or healthchecks can be sure to use the session factory.
   */
  public SqlSessionFactory getSqlSessionFactory() {
    return mybatis.getSqlSessionFactory();
  }
  
  public JwtCodec getJwtCoder() {
    return auth.getJwtCodec();
  }
  
  @Override
  public void run(T cfg, Environment env) {
    // http client pool is managed via DW lifecycle already
    httpClient = new HttpClientBuilder(env).using(cfg.client).build(getName());
  
    // reuse the same http client pool also for jersey clients!
    JerseyClientBuilder builder = new JerseyClientBuilder(env)
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
