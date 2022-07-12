package life.catalogue.db;

import life.catalogue.common.util.YamlUtils;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.dao.Partitioner;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
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
public class PgSetupRule extends ExternalResource {
  private static final Logger LOG = LoggerFactory.getLogger(PgSetupRule.class);
  
  private static HikariDataSource dataSource;
  private static SqlSessionFactory sqlSessionFactory;
  private static PgConfig cfg;
  
  private final boolean wipe;
  
  public static PgConfig getCfg() {
    return cfg;
  }
  
  public PgSetupRule() {
    this.wipe = true;
  }

  public PgSetupRule(boolean wipe) {
    this.wipe = wipe;
  }
  
  public static SqlSessionFactory getSqlSessionFactory() {
    return sqlSessionFactory;
  }
  
  @Override
  protected void before() throws Throwable {
    System.out.println("run PgSetupRule wipe=" + wipe);
    super.before();
    try {
      cfg = YamlUtils.read(PgConfig.class, "/pg-test.yaml");
      if (wipe) {
        initDb(cfg);
      } else {
        setupMybatis(cfg);
      }
    } catch (Exception e) {
      LOG.error("Pg setup error: {}", e.getMessage(), e);
      after();
      throw new RuntimeException(e);
    }
  }
  
  public PgConnection connect() throws SQLException {
    LOG.debug("Connection directly via JDBC");
    return (PgConnection) cfg.connect();
  }

  public static void wipeDB(Connection con) throws SQLException {
    con.setAutoCommit(false);
    LOG.debug("Recreate empty public schema");
    try (Statement st = con.createStatement()) {
      st.execute("DROP SCHEMA public CASCADE");
      st.execute("CREATE SCHEMA public");
      con.commit();
    }
  }
  
  private static void setupMybatis(PgConfig cfg) throws Exception {
    LOG.info("Creating hikari pool for Postgres server {}/{}", cfg.host, cfg.database);
    HikariConfig hikari = cfg.hikariConfig();
    hikari.setAutoCommit(false);
    dataSource = new HikariDataSource(hikari);
  
    // configure single mybatis session factory
    LOG.info("Configure MyBatis session factory");
    sqlSessionFactory = MybatisFactory.configure(dataSource, "test");
    DatasetInfoCache.CACHE.setFactory(sqlSessionFactory);
  }
  
  public static void initDb(PgConfig cfg) throws Exception {
    try (Connection con = cfg.connect()) {
      LOG.info("Init empty database schema on {}", cfg.location());
      wipeDB(con);
      ScriptRunner runner = PgConfig.scriptRunner(con);
      runner.runScript(Resources.getResourceAsReader(InitDbUtils.SCHEMA_FILE));
      con.commit();
      // this enables autocommit on the connection
      LookupTables.recreateTables(con);
    }
    setupMybatis(cfg);
    Partitioner.createDefaultPartitions(sqlSessionFactory, 2);
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
