package life.catalogue.command;

import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.zaxxer.hikari.HikariDataSource;

import io.dropwizard.client.DropwizardApacheConnector;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.setup.Bootstrap;
import life.catalogue.WsServerConfig;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.User;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.MybatisFactory;
import life.catalogue.db.mapper.UserMapper;
import life.catalogue.doi.service.UserAgentFilter;

import life.catalogue.dw.jersey.ColJerseyBundle;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.glassfish.jersey.CommonProperties;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.RequestEntityProcessing;
import org.glassfish.jersey.client.spi.ConnectorProvider;
import org.glassfish.jersey.logging.LoggingFeature;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import java.util.logging.Level;

public abstract class AbstractMybatisCmd extends AbstractPromptCmd {
  private static final String ARG_USER = "user";
  WsServerConfig cfg;
  Namespace ns;
  Integer userKey;
  User user;
  SqlSessionFactory factory;
  HikariDataSource dataSource;
  Client jerseyClient;

  public AbstractMybatisCmd(String name, String description) {
    super(name, description);
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
          userKey = user.getKey();
        }
      }

      bootstrap.addBundle(new ColJerseyBundle());
      JerseyClientBuilder builder = new JerseyClientBuilder(bootstrap.getMetricRegistry())
        .withProperty(CommonProperties.FEATURE_AUTO_DISCOVERY_DISABLE, Boolean.TRUE)
        .withProperty(ClientProperties.REQUEST_ENTITY_PROCESSING, RequestEntityProcessing.BUFFERED)
        .using(cfg.client)
        .using(bootstrap.getObjectMapper());
      jerseyClient = builder.build("COLcmd/" + ObjectUtils.coalesce(cfg.versionString(), "1.0"));

      execute();

    } finally {
      dataSource.close();
      jerseyClient.close();
    }
  }

  abstract void execute() throws Exception;
}
