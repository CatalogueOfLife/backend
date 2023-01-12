package life.catalogue.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import life.catalogue.common.util.YamlUtils;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.dao.Partitioner;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.rules.ExternalResource;
import org.postgresql.jdbc.PgConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * A junit test rule that creates a {@link HikariDataSource} and SqlSessionFactory for the clb postgres db configured in pg-test.yaml.
 * This rule does not alter the database and simply connects to it and provides the mybatis session factory.
 *
 * The rule was designed to share the pool across all tests of a test class
 * if it runs as a static {@link org.junit.ClassRule}.
 */
public class PgConnectRule extends ExternalResource {
  private static final Logger LOG = LoggerFactory.getLogger(PgConnectRule.class);

  private static HikariDataSource dataSource;
  private static SqlSessionFactory sqlSessionFactory;
  protected static PgConfig cfg;

  public static PgConfig getCfg() {
    return cfg;
  }

  public static SqlSessionFactory getSqlSessionFactory() {
    return sqlSessionFactory;
  }
  
  @Override
  protected void before() throws Throwable {
    try {
      cfg = YamlUtils.read(PgConfig.class, "/pg-test.yaml");
      System.out.println("psql -U postgres " + cfg.database);
      setupMybatis(cfg);
    } catch (Exception e) {
      LOG.error("Pg setup error: {}", e.getMessage(), e);
      after();
      throw new RuntimeException(e);
    }
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

  @Override
  public void after() {
    if (dataSource != null) {
      LOG.info("Shutdown dbpool");
      dataSource.close();
      DatasetInfoCache.CACHE.setFactory(null);
    }
  }

}
