package life.catalogue.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

import javax.validation.constraints.Min;

import org.apache.ibatis.jdbc.ScriptRunner;
import org.postgresql.jdbc.PgConnection;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * A configuration for the postgres database connection pool as used by the mybatis layer.
 */
@SuppressWarnings("PublicField")
public class PgConfig extends PgDbConfig {

  /**
   * Postgres server host. Defaults to localhost
   */
  public String host = "localhost";

  public int port = 5432;

  @Min(1)
  public int maximumPoolSize = 8;
  
  /**
   * The minimum number of idle connections that the pool tries to maintain.
   * If the idle connections dip below this value, the pool will make a best effort to add additional connections quickly and efficiently.
   * However, for maximum performance and responsiveness to spike demands, it is recommended to set this value not too low.
   * Beware that postgres statically allocates the work_mem for each session which can eat up memory a lot.
   */
  @Min(0)
  public int minimumIdle = 1;
  
  /**
   * This property controls the maximum amount of time in milliseconds that a connection is allowed to sit idle in the pool.
   * A connection will never be retired as idle before this timeout.
   * A value of 0 means that idle connections are never removed from the pool.
   */
  @Min(0)
  public int idleTimeout = min(1);
  
  /**
   * This property controls the maximum lifetime of a connection in the pool.
   * When a connection reaches this timeout it will be retired from the pool.
   * An in-use connection will never be retired, only when it is closed will it then be removed.
   * A value of 0 indicates no maximum lifetime (infinite lifetime), subject of course to the idleTimeout setting.
   */
  @Min(0)
  public int maxLifetime = min(15);
  
  /**
   * Postgres property lock_timeout:
   * Abort any statement that takes more than the specified number of milliseconds,
   * starting from the time the command arrives at the server from the client.
   * A value of zero (the default) turns this off.
   */
  @Min(0)
  public int lockTimeout = 0;
  
  /**
   * Postgres property idle_in_transaction_session_timeout:
   * Terminate any session with an open transaction that has been idle for longer than the specified duration in milliseconds.
   * This allows any locks held by that session to be released and the connection slot to be reused;
   * it also allows tuples visible only to this transaction to be vacuumed.
   * <p>
   * We do need long running transactions in the backend though, e.g. when indexing a dataset into the search index.
   * Recommended to keep this feature disabled for CoL in production.
   * <p>
   * The default value of 0 disables this feature.
   */
  @Min(0)
  public int idleInTransactionSessionTimeout = 0;
  
  /**
   * The postgres work_mem session setting in MB that should be used for each connection.
   * A value of zero or below does not set anything and thus uses the global postgres settings
   */
  public int workMem = 0;
  
  @Min(1000)
  public int connectionTimeout = sec(5);
  
  public PgConfig() {
  }

  public PgConfig(String host, String database, String user, String password) {
    this.host = host;
    this.database = database;
    this.user = user;
    this.password = password;
  }

  /**
   * @return converted minutes in milliseconds
   */
  private static int min(int minutes) {
    return minutes * 60000;
  }
  
  /**
   * @return converted seconds in milliseconds
   */
  private static int sec(int seconds) {
    return seconds * 1000;
  }
  
  /**
   * @return a new simple postgres jdbc connection
   */
  public PgConnection connect() throws SQLException {
    return connect(this);
  }
  
  /**
   * @return a new simple postgres jdbc connection to the given db on this pg server
   */
  public PgConnection connect(PgDbConfig db) throws SQLException {
    PgConnection c = DriverManager.getConnection(jdbcUrl(db), Strings.emptyToNull(db.user), Strings.emptyToNull(db.password)).unwrap(PgConnection.class);
    // enforce UTF8 connections, see https://github.com/Sp2000/colplus-backend/issues/577
    try (Statement st = c.createStatement()) {
      st.execute("SET client_encoding = 'UTF8'");
    }
    return c;
  }

  public String location() {
    return location(database);
  }

  private String location(String database) {
    return host + ":" + port + "/" + database;
  }

  private String jdbcUrl(PgDbConfig db) {
    return "jdbc:postgresql://" + location(db.database);
  }

  /**
   * @return a new hikari connection pool for the configured db
   */
  public HikariDataSource pool() {
    return new HikariDataSource(hikariConfig());
  }
  
  public HikariConfig hikariConfig() {
    HikariConfig hikari = new HikariConfig();
    hikari.setJdbcUrl(jdbcUrl(this));
    //hikari.setDataSourceClassName("org.postgresql.ds.PGSimpleDataSource");
    hikari.setUsername(user);
    hikari.setPassword(password);
    hikari.setConnectionTimeout(connectionTimeout);
    hikari.setMaximumPoolSize(maximumPoolSize);
    hikari.setMinimumIdle(minimumIdle);
    hikari.setIdleTimeout(idleTimeout);
    hikari.setMaxLifetime(maxLifetime);
    
    // connection settings
    StringBuilder sb = new StringBuilder();
    if (workMem > 0) {
      sb.append("SET work_mem='" + workMem + "MB';");
    }
    if (lockTimeout > 0) {
      sb.append("SET lock_timeout TO " + lockTimeout + ";");
    }
    if (idleInTransactionSessionTimeout > 0) {
      sb.append("SET idle_in_transaction_session_timeout TO " + idleInTransactionSessionTimeout + ";");
    }
    if (sb.length() > 0) {
      hikari.setConnectionInitSql(sb.toString());
    }
    return hikari;
  }

  public static ScriptRunner scriptRunner(Connection con) {
    return scriptRunner(con, true);
  }

  public static ScriptRunner scriptRunner(Connection con, boolean sendFullScript) {
    ScriptRunner runner = new ScriptRunner(con);
    // needed to honor the $$ escapes in pg functions
    runner.setSendFullScript(sendFullScript);
    runner.setStopOnError(true);
    runner.setLogWriter(null);
    return runner;
  }
  
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("host", host)
        .add("database", database)
        .add("user", user)
        .add("password", password)
        .add("connectionTimeout", connectionTimeout)
        .add("maximumPoolSize", maximumPoolSize)
        .add("minimumIdle", minimumIdle)
        .add("idleTimeout", idleTimeout)
        .add("maxLifetime", maxLifetime)
        .add("lockTimeout", lockTimeout)
        .add("idleInTransactionSessionTimeout", idleInTransactionSessionTimeout)
        .add("workMem", workMem)
        .toString();
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PgConfig pgConfig = (PgConfig) o;
    return port == pgConfig.port &&
        maximumPoolSize == pgConfig.maximumPoolSize &&
        minimumIdle == pgConfig.minimumIdle &&
        idleTimeout == pgConfig.idleTimeout &&
        maxLifetime == pgConfig.maxLifetime &&
        lockTimeout == pgConfig.lockTimeout &&
        idleInTransactionSessionTimeout == pgConfig.idleInTransactionSessionTimeout &&
        workMem == pgConfig.workMem &&
        connectionTimeout == pgConfig.connectionTimeout &&
        Objects.equals(host, pgConfig.host) &&
        Objects.equals(database, pgConfig.database) &&
        Objects.equals(user, pgConfig.user) &&
        Objects.equals(password, pgConfig.password);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(host, port, database, user, password, maximumPoolSize, minimumIdle, idleTimeout, maxLifetime, lockTimeout, idleInTransactionSessionTimeout, workMem, connectionTimeout);
  }
}
