package life.catalogue.db;

import com.zaxxer.hikari.HikariConfig;

import life.catalogue.common.util.YamlUtils;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.dao.Partitioner;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.UUID;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.rules.ExternalResource;
import org.postgresql.jdbc.PgConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariDataSource;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

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

  public static String COL_DB_NAME = "col";
  public static String ADMIN_DB_NAME = "admin";
  private static PostgreSQLContainer<?> PG_CONTAINER;
  private static HikariDataSource dataSource;
  private static SqlSessionFactory sqlSessionFactory;
  protected static PgConfig cfg;

  @Override
  protected void before() throws Throwable {
    PG_CONTAINER = setupPostgres();
    PG_CONTAINER.start();
    cfg = buildContainerConfig(PG_CONTAINER);
    try {
      initDb(PG_CONTAINER, cfg);
    } catch (Exception e) {
      LOG.error("Pg setup error: {}", e.getMessage(), e);
      after();
      throw new RuntimeException(e);
    }
  }

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

  public static PgConfig buildContainerConfig(PostgreSQLContainer<?> container) {
    PgConfig cfg = new PgConfig();
    cfg.host = container.getHost();
    cfg.database = COL_DB_NAME;
    cfg.user = container.getUsername();
    cfg.password = container.getPassword();
    cfg.port = container.getFirstMappedPort();
    return cfg;
  }

  public static PostgreSQLContainer<?> setupPostgres() {
    PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:14.6").withDatabaseName(ADMIN_DB_NAME);
    container.withReuse(true)
             .withLabel("reuse.tag", "col_PG_container");
    container.setWaitStrategy(Wait.defaultWaitStrategy().withStartupTimeout(Duration.ofSeconds(60)));
    return container;
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

  /**
   * Creates a new database with a unique database name based on the supplied cfg.database.
   * The cfg instance will be changed to contain the new, unique dbname.
   */
  public static void initDb(PostgreSQLContainer<?> container, PgConfig cfg) throws Exception {
    LOG.info("Starting initialisation of db {}", cfg);
    try (Connection con = container.createConnection("")) {
      PgUtils.createDatabase(con, cfg.database, cfg.user);
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
    Partitioner.createDefaultPartitions(getSqlSessionFactory(), 2);
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

  @Override
  public void after() {
    if (dataSource != null) {
      LOG.info("Shutdown dbpool");
      dataSource.close();
      DatasetInfoCache.CACHE.setFactory(null);
    }
    PG_CONTAINER.stop();
  }

}
