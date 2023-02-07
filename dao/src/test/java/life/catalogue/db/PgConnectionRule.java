package life.catalogue.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.dao.Partitioner;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.rules.ExternalResource;
import org.postgresql.jdbc.PgConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

/**
 * A junit test rule that creates a {@link HikariDataSource} and SqlSessionFactory for the clb postgres db and stops it the end.
 * It does not alter the database in any way, just sets up the connection and mybatis and makes sure to close it at the end.
 */
public class PgConnectionRule extends ExternalResource {
  private static final Logger LOG = LoggerFactory.getLogger(PgConnectionRule.class);

  private HikariDataSource dataSource;
  private SqlSessionFactory sqlSessionFactory;
  private final PgConfig cfg;

  public PgConnectionRule(PgConfig cfg) {
    this.cfg = cfg;
  }

  public PgConnectionRule(String localhost, String database, String user, String password) {
    cfg = new PgConfig(localhost, database, user, password);
  }

  @Override
  public void before() throws Throwable {
    LOG.info("Creating hikari pool for Postgres server {}/{}", cfg.host, cfg.database);
    HikariConfig hikari = cfg.hikariConfig();
    hikari.setAutoCommit(false);
    dataSource = new HikariDataSource(hikari);

    // configure single mybatis session factory
    LOG.info("Configure MyBatis session factory");
    sqlSessionFactory = MybatisFactory.configure(dataSource, "test");
    DatasetInfoCache.CACHE.setFactory(sqlSessionFactory);
  }

  public PgConfig getCfg() {
    return cfg;
  }

  public SqlSessionFactory getSqlSessionFactory() {
    return sqlSessionFactory;
  }

  public PgConnection connect() throws SQLException {
    LOG.debug("Connection directly via JDBC");
    return cfg.connect();
  }

  @Override
  public void after() {
    if (dataSource != null) {
      LOG.info("Shutdown dbpool");
      dataSource.close();
    }
    DatasetInfoCache.CACHE.setFactory(null);
  }
}
