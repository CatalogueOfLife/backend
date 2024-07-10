package life.catalogue.junit;

import life.catalogue.db.PgConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariDataSource;

/**
 * A junit test rule that creates a {@link HikariDataSource} and SqlSessionFactory for the clb postgres db and stops it the end.
 * It does not alter the database in any way, just sets up the connection and mybatis and makes sure to close it at the end.
 */
public class PgConnectionRule extends SqlSessionFactoryRule {
  private static final Logger LOG = LoggerFactory.getLogger(PgConnectionRule.class);

  private final boolean initDB = true;
  private final PgConfig adminCfg;

  public PgConnectionRule(String database, String user, String password) {
    this("localhost", database, user, password);
  }

  public PgConnectionRule(String host, String database, String user, String password) {
    cfg = new PgConfig(host, database, user, password);
    adminCfg = new PgConfig(host, "postgres", "postgres", "postgres");
  }

  @Override
  public void before() throws Throwable {
    if (initDB) {
      try {
        PgSetupRule.initDb(adminCfg::connect, cfg);
      } catch (Exception e) {
        LOG.error("Pg setup error: {}", e.getMessage(), e);
        after();
        throw new RuntimeException(e);
      }
    } else {
      setupMybatis(cfg);
    }
  }

  @Override
  public void after() {
    shutdownDbPool();
  }
}
