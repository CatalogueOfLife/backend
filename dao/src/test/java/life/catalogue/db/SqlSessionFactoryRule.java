package life.catalogue.db;

import life.catalogue.dao.DatasetInfoCache;

import java.sql.SQLException;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.rules.ExternalResource;
import org.postgresql.jdbc.PgConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * A junit test rule that creates a {@link HikariDataSource} and SqlSessionFactory for the clb postgres db and stops it the end.
 * By default the rule inits a new db and creates a draft col partition needed to host the names index,
 * but it can also be configured to run without wiping.
 *
 * The rule was designed to share the pool across all tests of a test class
 * if it runs as a static {@link org.junit.ClassRule}.
 */
public abstract class SqlSessionFactoryRule extends ExternalResource {
  private static final Logger LOG = LoggerFactory.getLogger(SqlSessionFactoryRule.class);
  protected static HikariDataSource dataSource;
  protected static SqlSessionFactory sqlSessionFactory;
  protected static PgConfig cfg;


  public static PgConfig getCfg() {
    return cfg;
  }

  public static SqlSessionFactory getSqlSessionFactory() {
    return sqlSessionFactory;
  }

  public PgConnection connect() throws SQLException {
    LOG.debug("Connection directly via JDBC");
    return cfg.connect();
  }

  protected static void setupMybatis(PgConfig cfg) throws Exception {
    LOG.info("Creating hikari pool for Postgres server {}/{}", cfg.host, cfg.database);
    HikariConfig hikari = cfg.hikariConfig();
    hikari.setAutoCommit(false);
    dataSource = new HikariDataSource(hikari);

    // configure single mybatis session factory
    LOG.info("Configure MyBatis session factory");
    sqlSessionFactory = MybatisFactory.configure(dataSource, "test");
    DatasetInfoCache.CACHE.setFactory(sqlSessionFactory);
  }

  public void shutdownDbPool() {
    if (dataSource != null) {
      LOG.info("Shutdown dbpool");
      dataSource.close();
    }
    DatasetInfoCache.CACHE.setFactory(null);
  }

}
