package life.catalogue.db;

import com.github.dockerjava.api.DockerClient;

import life.catalogue.common.func.ThrowingSupplier;
import life.catalogue.dao.Partitioner;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import com.zaxxer.hikari.HikariDataSource;

/**
 * A junit test rule that creates a {@link HikariDataSource} and SqlSessionFactory for the clb postgres db and stops it the end.
 * By default the rule inits a new db and creates a draft col partition needed to host the names index,
 * but it can also be configured to run without wiping.
 *
 * The rule was designed to share the pool across all tests of a test class
 * if it runs as a static {@link org.junit.ClassRule}.
 */
public class PgSetupRule extends SqlSessionFactoryRule {
  private static final Logger LOG = LoggerFactory.getLogger(PgSetupRule.class);

  public static String COL_DB_NAME = "col";
  public static String ADMIN_DB_NAME = "admin";
  private static PostgreSQLContainer<?> PG_CONTAINER;
  private PgConfig prev;

  @Override
  protected void before() throws Throwable {
    PG_CONTAINER = setupPostgres();
    PG_CONTAINER.start();
    prev = cfg; // we modify static configs here, so we keep them to replace them again to avoid parallel test problems
    cfg = buildContainerConfig(PG_CONTAINER);
    try {
      initDb(PG_CONTAINER, cfg);
    } catch (Exception e) {
      LOG.error("Pg setup error: {}", e.getMessage(), e);
      after();
      throw new RuntimeException(e);
    }
  }

  public static PgConfig buildContainerConfig(PostgreSQLContainer<?> container) {
    PgConfig cfg = new PgConfig();
    cfg.host = container.getHost();
    cfg.database = COL_DB_NAME;
    cfg.user = container.getUsername();
    cfg.password = container.getPassword();
    cfg.port = container.getFirstMappedPort();
    System.out.println("Postgres container using port " + cfg.port);
    return cfg;
  }

  public static PostgreSQLContainer<?> setupPostgres() {
    PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:15.4").withDatabaseName(ADMIN_DB_NAME);
    container.withAccessToHost(true);
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
    initDb(() -> container.createConnection(""), cfg);
  }

  /**
   * Creates a new database with a unique database name based on the supplied cfg.database.
   * The cfg instance will be changed to contain the new, unique dbname.
   * This also sets up mybatis.
   *
   * @param connectionSupplier db admin connection to drop and create databases
   * @param cfg configs for the to be created database
   */
  public static void initDb(ThrowingSupplier<Connection, Exception> connectionSupplier, PgConfig cfg) throws Exception {
    LOG.info("Starting initialisation of db {}", cfg);
    try (Connection con = connectionSupplier.get()) {
      PgUtils.createDatabase(con, cfg.database, cfg.user);
    }

    try (Connection con = cfg.connect()) {
      LOG.info("Init empty database schema on {}", cfg.location());
      ScriptRunner runner = PgConfig.scriptRunner(con);
      runner.runScript(Resources.getResourceAsReader(InitDbUtils.SCHEMA_FILE));
      con.commit();
    }
    setupMybatis(cfg);
    Partitioner.createPartitions(getSqlSessionFactory(), 2);
  }

  @Override
  public void after() {
    shutdownDbPool();
    PG_CONTAINER.stop();
    cfg = prev;
  }

  public static DockerClient getDockerClient() {
    return PG_CONTAINER == null ? null : PG_CONTAINER.getDockerClient();
  }

}
