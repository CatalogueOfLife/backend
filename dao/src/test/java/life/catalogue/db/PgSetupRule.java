package life.catalogue.db;

import life.catalogue.common.util.YamlUtils;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.dao.Partitioner;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

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
  private static PgConfig adminCfg;

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
      // modify database name to be unique
      cfg.database = cfg.database + "-" + UUID.randomUUID();
      System.out.println("psql -U postgres " + cfg.database);
      adminCfg = YamlUtils.read(PgConfig.class, "/pg-admin.yaml");
      initDb(cfg, adminCfg);
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

  /**
   * Creates a new database with a unique database name based on the supplied cfg.database.
   * The cfg instance will be changed to contain the new, unique dbname.
   */
  public static void initDb(PgConfig cfg) throws Exception {
    // modify database name to be unique
    cfg.database = cfg.database + "-" + UUID.randomUUID();
    var adminCfg = YamlUtils.read(PgConfig.class, "/pg-admin.yaml");
    initDb(cfg, adminCfg);
  }

  public static void initDb(PgConfig cfg, PgConfig admin) throws Exception {
    LOG.info("Starting initialisation of db {} using admin connection {}", cfg, admin);
    try (Connection con = admin.connect(admin);
         Statement st = con.createStatement()
    ) {
      LOG.info("Drop existing database {}", cfg.database);
      st.execute("DROP DATABASE IF EXISTS \"" + cfg.database + "\"");

      LOG.info("Create new database {}", cfg.database);
      st.execute("CREATE DATABASE  \"" + cfg.database + "\"" +
                 " WITH ENCODING UTF8 LC_COLLATE 'C' LC_CTYPE 'C' OWNER " + cfg.user + " TEMPLATE template0");

      LOG.info("Use UTC timezone for {}", cfg.database);
      st.execute("ALTER DATABASE  \"" + cfg.database + "\" SET timezone TO 'UTC'");
    }

    try (Connection con = cfg.connect()) {
      LOG.info("Init empty database schema on {}", cfg.location());
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

    LOG.info("Delete test db {} using admin connection {}", cfg, adminCfg);
    try (Connection con = adminCfg.connect(adminCfg);
         Statement st = con.createStatement()
    ) {
      LOG.info("Drop existing database {}", cfg.database);
      st.execute("DROP DATABASE IF EXISTS \"" + cfg.database + "\"");

    } catch (Exception e) {
      LOG.error("Failed to remove temp test database {}", cfg.database, e);
    }
  }

}
