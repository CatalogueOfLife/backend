package org.col.db;

import java.io.IOException;
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
import ru.yandex.qatools.embed.postgresql.EmbeddedPostgres;

/**
 * A junit test rule that starts up an {@link EmbeddedPostgres} server together with a
 * {@link HikariDataSource} and stops both at the end. The rule was designed to share the server
 * across all tests of a test class if it runs as a static {@link org.junit.ClassRule}.
 * <p>
 * It can even be used to share the same postgres server across several test classes if it is used
 * in as a {@link org.junit.ClassRule} in a TestSuite.
 * <p>
 * By default it uses an embedded postgres server. Setting the System variable "" to true enables
 * the use of
 */
public class PgSetupRule extends ExternalResource {
  private static final Logger LOG = LoggerFactory.getLogger(PgSetupRule.class);
  
  private static HikariDataSource dataSource;
  private static SqlSessionFactory sqlSessionFactory;
  private static PgConfig cfg;
  private EmbeddedColPg postgres;
  private final boolean serverOnly;
  private final boolean doInitDb;
  
  public PgSetupRule() {
    this(false, true);
  }
  
  /**
   * @param serverOnly if true just provides a postgres server but does not connectPool to it or init a
   *                   colplus database
   */
  public PgSetupRule(boolean serverOnly) {
    this(serverOnly, true);
  }
  
  /**
   * @param serverOnly if true just provides a postgres server and connects to it but does not init
   *                   the colplus db; can be handy for ad hoc (@Ignore) tests where you want to work with a
   *                   good chunk of data.
   */
  public PgSetupRule(boolean serverOnly, boolean doInitDb) {
    this.serverOnly = serverOnly;
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
      startDb();
      if (!serverOnly) {
        connectPool();
        if (doInitDb) {
          initDb(cfg);
        }
      }
    } catch (Exception e) {
      LOG.error("Pg setup error: {}", e.getMessage(), e);
      shutdown();
      throw new RuntimeException(e);
    }
  }
  
  private void startDb() throws IOException {
    cfg = YamlUtils.read(PgConfig.class, "/pg-test.yaml");
    if (cfg.embedded()) {
      postgres = new EmbeddedColPg(cfg);
      postgres.start();
      
    } else {
      LOG.info("Use external Postgres server {}/{}", cfg.host, cfg.database);
    }
  }
  
  public PgConnection connect() throws SQLException {
    if (dataSource != null) {
      LOG.debug("Connection via pool");
      return (PgConnection) dataSource.getConnection();
    }
    LOG.debug("Connection directly via JDBC");
    return (PgConnection) cfg.connect();
  }

  public void connectPool() {
    LOG.debug("Setup connection pool");
    HikariConfig hikari = cfg.hikariConfig();
    hikari.setAutoCommit(false);
    dataSource = new HikariDataSource(hikari);
    
    // configure single mybatis session factory
    sqlSessionFactory = MybatisFactory.configure(dataSource, "test");
  }
  
  public static void wipeDB(Connection con) throws SQLException {
    con.setAutoCommit(false);
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
    if (postgres != null) {
      postgres.stop();
    }
  }
  
}
