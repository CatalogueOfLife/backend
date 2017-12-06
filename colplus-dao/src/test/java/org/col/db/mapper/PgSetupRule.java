package org.col.db.mapper;

import com.google.common.base.Throwables;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.db.MybatisFactory;
import org.col.db.PgConfig;
import org.col.util.YamlUtils;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.yandex.qatools.embed.postgresql.EmbeddedPostgres;
import ru.yandex.qatools.embed.postgresql.distribution.Version;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;

import static org.col.db.PgConfig.SCHEMA_FILE;

/**
 * A junit test rule that starts up an {@link EmbeddedPostgres} server together
 * with a {@link HikariDataSource} and stops both at the end. The rule was
 * designed to share the server across all tests of a test class if it runs as a
 * static {@link org.junit.ClassRule}.
 *
 * It can even be used to share the same postgres server across several test
 * classes if it is used in as a {@link org.junit.ClassRule} in a TestSuite.
 *
 * By default it uses an embedded postgres server.
 * Setting the System variable "" to true enables the use of
 */
public class PgSetupRule extends ExternalResource {
  private static final Logger LOG = LoggerFactory.getLogger(PgSetupRule.class);

	private static EmbeddedPostgres postgres;
	private static HikariDataSource dataSource;
	private static SqlSessionFactory sqlSessionFactory;
  private static PgConfig cfg;
	private boolean startedHere = false;

  public static PgConfig getCfg() throws SQLException {
    return cfg;
  }

  public static Connection getConnection() throws SQLException {
		return dataSource.getConnection();
	}

	public static SqlSessionFactory getSqlSessionFactory() throws SQLException {
		return sqlSessionFactory;
	}

	@Override
	protected void before() throws Throwable {
		super.before();
		if (postgres == null) {
			startDb();
			startedHere = true;
			initDb();
		}
	}

	private void startDb() {
		try {
      cfg = YamlUtils.read(PgConfig.class, "/pg-test.yaml");
			if (cfg.host == null) {
        LOG.info("Starting embedded Postgres");
				Instant start = Instant.now();
				postgres = new EmbeddedPostgres(Version.V10_0);
				// assigned some free port using local socket 0
        cfg.port = new ServerSocket(0).getLocalPort();
        cfg.host = "localhost";
        cfg.maximumPoolSize = 2;
				postgres.start(cfg.host, cfg.port, cfg.database, cfg.user, cfg.password);
        LOG.info("Pg startup time: {} ms", Duration.between(start, Instant.now()).toMillis());

			} else {
				LOG.info("Use external Postgres server {}/{}", cfg.host, cfg.database);
			}

			HikariConfig hikari = cfg.hikariConfig();
			hikari.setAutoCommit(false);
			dataSource = new HikariDataSource(hikari);

			// configure single mybatis session factory
			sqlSessionFactory = MybatisFactory.configure(dataSource, "test");

		} catch (Exception e) {
      LOG.error("Pg startup error: {}", e.getMessage(), e);

			if (dataSource != null) {
				dataSource.close();
			}
			if (postgres != null) {
				postgres.stop();
			}
			Throwables.propagate(e);
		}
	}

  private void initDb() {
		try (Connection con = dataSource.getConnection()) {
			System.out.println("Init empty database schema\n");
			ScriptRunner runner = new ScriptRunner(con);
			runner.runScript(Resources.getResourceAsReader(SCHEMA_FILE));
			con.commit();

		} catch (SQLException | IOException e) {
			Throwables.propagate(e);
		}
	}

	@Override
	public void after() {
		if (startedHere) {
			System.out.println("Shutdown dbpool");
			dataSource.close();
			if (postgres != null) {
				System.out.println("Stopping Postgres");
				postgres.stop();
			}
		}
	}

}
