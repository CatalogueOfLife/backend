package org.col.db;

import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.yandex.qatools.embed.postgresql.EmbeddedPostgres;
import ru.yandex.qatools.embed.postgresql.distribution.Version;

/**
 * An {@link EmbeddedPostgres} server that can be start up and inits a minimal CoL+ db.
 */
public class EmbeddedColPg {
  private static final Logger LOG = LoggerFactory.getLogger(EmbeddedColPg.class);

	private EmbeddedPostgres postgres;
  private final PgConfig cfg;

	public EmbeddedColPg() {
		this.cfg = new PgConfig();
		cfg.user = "postgres";
		cfg.password = "postgres";
		cfg.database = "colplus";
	}

	public EmbeddedColPg(PgConfig cfg) {
		this.cfg = cfg;
	}

	public PgConfig getCfg() {
    return cfg;
  }

	public void start() {
		if (postgres == null) {
			startDb();
		} else {
			LOG.info("Embedded Postgres already running");
		}
	}

	private void startDb() {
		try {
			LOG.info("Starting embedded Postgres");
			Instant start = Instant.now();
			postgres = new EmbeddedPostgres(Version.V10_3);
			// assigned some free port using local socket 0
			cfg.port = new ServerSocket(0).getLocalPort();
			cfg.host = "localhost";
			cfg.maximumPoolSize = 2;
			postgres.start(cfg.host, cfg.port, cfg.database, cfg.user, cfg.password);
			LOG.info("Pg startup time: {} ms", Duration.between(start, Instant.now()).toMillis());

		} catch (Exception e) {
      LOG.error("Pg startup error: {}", e.getMessage(), e);

			if (postgres != null) {
				postgres.stop();
			}
			throw new RuntimeException(e);
		}
	}

	public void stop() {
		if (postgres != null) {
			postgres.stop();
		}
	}

}
