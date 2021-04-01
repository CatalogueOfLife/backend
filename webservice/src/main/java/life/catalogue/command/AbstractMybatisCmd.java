package life.catalogue.command;

import com.zaxxer.hikari.HikariDataSource;
import io.dropwizard.setup.Bootstrap;
import life.catalogue.WsServerConfig;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.User;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Users;
import life.catalogue.dao.DaoUtils;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.MybatisFactory;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.UserMapper;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

public abstract class AbstractMybatisCmd extends AbstractPromptCmd {
  private static final String ARG_USER = "user";
  WsServerConfig cfg;
  Namespace ns;
  Integer userKey;
  User user;
  SqlSessionFactory factory;
  HikariDataSource dataSource;

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
      execute();

    } finally {
      dataSource.close();
    }
  }

  abstract void execute() throws Exception;
}
