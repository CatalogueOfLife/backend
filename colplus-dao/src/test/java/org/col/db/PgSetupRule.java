package org.col.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.common.util.YamlUtils;
import org.junit.rules.ExternalResource;
import org.postgresql.jdbc.PgConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A junit test rule that creates a {@link HikariDataSource} and SqlSessionFactory and stops it the end.
 * The rule was designed to share the pool across all tests of a test class
 * if it runs as a static {@link org.junit.ClassRule}.
 * <p>
 * It can even be used to share the same pool across several test classes if it is used
 * in as a {@link org.junit.ClassRule} in a TestSuite.
 */
public class PgSetupRule extends ExternalResource {
  private static final Logger LOG = LoggerFactory.getLogger(PgSetupRule.class);
  
  private static HikariDataSource dataSource;
  private static SqlSessionFactory sqlSessionFactory;
  private static PgConfig cfg;
  private final boolean doInitDb;
  
  public PgSetupRule() {
    this(true);
  }
  
  /**
   * @param doInitDb if true does init the colplus db;
   */
  public PgSetupRule(boolean doInitDb) {
    this.doInitDb = doInitDb;
  }
  
  public static PgConfig getCfg() {
    return cfg;
  }
  
  public static SqlSessionFactory getSqlSessionFactory() {
    return sqlSessionFactory;
  }
  
  @Override
  protected void before() throws Throwable {
    super.before();
    try {
      cfg = YamlUtils.read(PgConfig.class, "/pg-test.yaml");
      connectPool();
      if (doInitDb) {
        initDb(cfg);
      }
    } catch (Exception e) {
      LOG.error("Pg setup error: {}", e.getMessage(), e);
      shutdown();
      throw new RuntimeException(e);
    }
  }
  
  public PgConnection connect() throws SQLException {
    LOG.debug("Connection directly via JDBC");
    return (PgConnection) cfg.connect();
  }

  public void connectPool() {
    LOG.info("Create dbpool for Postgres server {}/{}", cfg.host, cfg.database);
    HikariConfig hikari = cfg.hikariConfig();
    hikari.setAutoCommit(false);
    dataSource = new HikariDataSource(hikari);
    
    // configure single mybatis session factory
    sqlSessionFactory = MybatisFactory.configure(dataSource, "test");
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
  
  public static void initDb(PgConfig cfg) throws Exception {
    try (Connection con = cfg.connect()) {
      LOG.info("Init empty database schema");
      wipeDB(con);
      ScriptRunner runner = PgConfig.scriptRunner(con);
      runner.runScript(Resources.getResourceAsReader(PgConfig.SCHEMA_FILE));
      con.commit();
    }
  }
  
  @Override
  public void after() {
    shutdown();
  }
  
  private void shutdown() {
    if (dataSource != null) {
      LOG.info("Shutdown dbpool");
      dataSource.close();
    }
  }
  
}
