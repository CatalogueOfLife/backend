package org.col.config;

import com.google.common.base.MoreObjects;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.validation.constraints.Min;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

/**
 * A configuration for the postgres database connection pool as used by the mybatis layer.
 */
@SuppressWarnings("PublicField")
public class PgConfig {

    public String host = "localhost";
    public String database;
    public String user;
    public String password;

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
     * The postgres work_mem session setting in MB that should be used for each connection.
     * A value of zero or below does not set anything and thus uses the global postgres settings
     */
    public int workMem = 0;

    @Min(1000)
    public int connectionTimeout = sec(5);

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
    public Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl(), user, password);
    }

    private String jdbcUrl() {
        return "jdbc:postgresql://" + host + "/" + database;
    }

    /**
     * @return a new hikari connection pool for the configured db
     */
    public HikariDataSource pool() {
        return new HikariDataSource(hikariConfig());
    }

    public HikariConfig hikariConfig() {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(jdbcUrl());
        //hikari.setDataSourceClassName("org.postgresql.ds.PGSimpleDataSource");
        hikari.setUsername(user);
        hikari.setPassword(password);
        hikari.setConnectionTimeout(connectionTimeout);
        hikari.setMaximumPoolSize(maximumPoolSize);
        hikari.setMinimumIdle(minimumIdle);
        hikari.setIdleTimeout(idleTimeout);
        hikari.setMaxLifetime(maxLifetime);
        if (workMem > 0) {
            hikari.setConnectionInitSql("SET work_mem='" + workMem + "MB'");
        }
        return hikari;
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
                .add("workMem", workMem)
                .toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, database, user, password, maximumPoolSize, minimumIdle, idleTimeout, maxLifetime, workMem, connectionTimeout);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final PgConfig other = (PgConfig) obj;
        return Objects.equals(this.host, other.host)
                && Objects.equals(this.database, other.database)
                && Objects.equals(this.user, other.user)
                && Objects.equals(this.password, other.password)
                && Objects.equals(this.maximumPoolSize, other.maximumPoolSize)
                && Objects.equals(this.minimumIdle, other.minimumIdle)
                && Objects.equals(this.idleTimeout, other.idleTimeout)
                && Objects.equals(this.maxLifetime, other.maxLifetime)
                && Objects.equals(this.workMem, other.workMem)
                && Objects.equals(this.connectionTimeout, other.connectionTimeout);
    }
}
