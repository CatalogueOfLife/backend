package life.catalogue.junit;

import life.catalogue.db.PgConfig;

import com.zaxxer.hikari.HikariDataSource;

/**
 * A junit test rule that creates a {@link HikariDataSource} and SqlSessionFactory for the clb postgres db and stops it the end.
 * It does not alter the database in any way, just sets up the connection and mybatis and makes sure to close it at the end.
 */
public class PgConnectionRule extends SqlSessionFactoryRule {

  public PgConnectionRule(String database, String user, String password) {
    this("localhost", database, user, password);
  }

  public PgConnectionRule(String host, String database, String user, String password) {
    cfg = new PgConfig(host, database, user, password);
  }

  @Override
  public void before() throws Throwable {
    setupMybatis(cfg);
  }

  @Override
  public void after() {
    shutdownDbPool();
  }
}
