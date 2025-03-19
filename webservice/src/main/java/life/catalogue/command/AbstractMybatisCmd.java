package life.catalogue.command;

import life.catalogue.WsServer;
import life.catalogue.WsServerConfig;
import life.catalogue.api.model.User;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.concurrent.ExecutorUtils;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.MybatisFactory;
import life.catalogue.db.mapper.UserMapper;
import life.catalogue.dw.jersey.ClbJerseyBundle;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.glassfish.jersey.CommonProperties;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.RequestEntityProcessing;
import org.glassfish.jersey.client.spi.ConnectorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariDataSource;

import io.dropwizard.client.DropwizardApacheConnector;
import io.dropwizard.client.HttpClientBuilder;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.core.setup.Bootstrap;
import jakarta.ws.rs.client.Client;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

public abstract class AbstractMybatisCmd extends AbstractPromptCmd {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractMybatisCmd.class);
  private static final String ARG_USER = "user";
  protected WsServerConfig cfg;
  protected Namespace ns;
  protected Integer userKey;
  protected User user;
  protected SqlSessionFactory factory;
  HikariDataSource dataSource;
  Client jerseyClient;
  ExecutorService exec;
  private final boolean jersey;
  CloseableHttpClient httpClient;

  public AbstractMybatisCmd(String name, String description) {
    this(name, false, description);
  }

  public AbstractMybatisCmd(String name, boolean initJersey, String description) {
    super(name, description);
    this.jersey = initJersey;
  }

  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    subparser.addArgument("--"+ARG_USER)
      .dest(ARG_USER)
      .type(String.class)
      .required(false)
      .help("Valid user name to use as the main actor");
  }

  @Override
  public final void execute(Bootstrap<WsServerConfig> bootstrap, Namespace namespace, WsServerConfig cfg) throws Exception {
    this.cfg = cfg;
    ns = namespace;

    try {
      LOG.info("Connecting to database {} on {}", cfg.db.database, cfg.db.host);
      dataSource = cfg.db.pool();
      factory = MybatisFactory.configure(dataSource, getClass().getSimpleName());
      DatasetInfoCache.CACHE.setFactory(factory);

      String username = namespace.getString(ARG_USER);
      if (username != null) {
        try (SqlSession session = factory.openSession()) {
          UserMapper um = session.getMapper(UserMapper.class);
          user = um.getByUsername(username);
          if (user == null) {
            throw new IllegalArgumentException("User " + username + " does not exist");
          }
          System.out.printf("Act with user %s \n", user);
          userKey = user.getKey();
        }
      }

      if (jersey) {
        final String userAgent = "ColCli/" + ObjectUtils.coalesce(cfg.versionString(), "1.0");
        bootstrap.addBundle(new ClbJerseyBundle());
        httpClient = new HttpClientBuilder(bootstrap.getMetricRegistry())
          .using(cfg.client)
          .build(userAgent);
        exec = Executors.newFixedThreadPool(4);
        JerseyClientBuilder builder = new JerseyClientBuilder(bootstrap.getMetricRegistry())
          .withProperty(CommonProperties.FEATURE_AUTO_DISCOVERY_DISABLE, Boolean.TRUE)
          .withProperty(ClientProperties.REQUEST_ENTITY_PROCESSING, RequestEntityProcessing.BUFFERED)
          .using(cfg.client)
          .using(exec)
          .using(bootstrap.getObjectMapper())
          .using((ConnectorProvider) (cl, runtimeConfig)
            -> new DropwizardApacheConnector(httpClient, WsServer.requestConfig(cfg.client), cfg.client.isChunkedEncodingEnabled())
          );
        jerseyClient = builder.build(userAgent);
      }

      execute();

    } finally {
      LOG.info("Shutdown cli");
      if (dataSource != null) {
        dataSource.close();
      }
      if (jerseyClient != null) {
        jerseyClient.close();
      }
      if (exec != null && !exec.isShutdown()) {
        ExecutorUtils.shutdown(exec);
      }
    }
  }

  public abstract void execute() throws Exception;
}
