package org.col.db;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;

import org.apache.commons.io.FileUtils;
import org.col.common.lang.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.yandex.qatools.embed.postgresql.EmbeddedPostgres;
import ru.yandex.qatools.embed.postgresql.distribution.Version;

import static java.util.Arrays.asList;

/**
 * An {@link EmbeddedPostgres} server that can be start up and inits a minimal CoL+ db.
 * If PgConfig.host is pointing to an absolute path it will be used to reuse a already unzipped, cached server instance,
 * but does not share a data directory.
 */
public class EmbeddedColPg {
  private static final Logger LOG = LoggerFactory.getLogger(EmbeddedColPg.class);

	private static final List<String> DEFAULT_ADD_PARAMS = asList(
			"-E", "SQL_ASCII",
			"--locale=C",
			"--lc-collate=C",
			"--lc-ctype=C");

	private EmbeddedPostgres postgres;
  private final PgConfig cfg;
	private Path dataDir;
	private Path serverDir;
	private boolean tmpServerDir;

	@Deprecated
	public EmbeddedColPg() {
		this.cfg = new PgConfig();
		cfg.host = null;
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
			// use a cached server directory to not unzip postgres binaries on every run
			tmpServerDir = cfg.host == null;
			serverDir = tmpServerDir ? Files.createTempDirectory("colplus-pg-") : Paths.get(cfg.host);
			dataDir = serverDir.resolve("data/"+String.valueOf(new Random().nextInt()));
			Files.createDirectories(dataDir);
			LOG.debug("Use embedded Postgres, server dir={}, data dir={}", serverDir, dataDir);
			postgres = new EmbeddedPostgres(Version.V10_3, dataDir.toString());
			// assigned some free port using local socket 0
			cfg.port = new ServerSocket(0).getLocalPort();
			cfg.host = "localhost";
			cfg.maximumPoolSize = 4;
			postgres.start(EmbeddedPostgres.cachedRuntimeConfig(serverDir),
					cfg.host, cfg.port, cfg.database, cfg.user, cfg.password,
					DEFAULT_ADD_PARAMS
			);
			if (postgres.getProcess().isPresent()) {
				LOG.info("Pg started on port {}. Startup time: {} ms", cfg.port, Duration.between(start, Instant.now()).toMillis());
			} else {
				throw new IllegalStateException("Embedded postgres failed to startup");
			}

		} catch (Exception e) {
      LOG.error("Pg startup error {}: {}", e.getMessage(), cfg, e);
			stop();
			Exceptions.throwRuntime(e);
		}
	}

	public void stop() {
		if (postgres != null && postgres.getProcess().isPresent()) {
			LOG.info("Stopping embedded Postgres in {}", serverDir);
			postgres.stop();

			try {
				FileUtils.deleteDirectory(dataDir.toFile());
				LOG.info("Removed Postgres data directory {}", dataDir);
			} catch (IllegalArgumentException | IOException e) {
				LOG.warn("Failed to remove Postgres data directory {}", dataDir, e);
			}

			if (tmpServerDir) {
				try {
					FileUtils.deleteDirectory(serverDir.toFile());
					LOG.info("Removed Postgres server directory {}", serverDir);
				} catch (IllegalArgumentException | IOException e) {
					LOG.warn("Failed to remove Postgres server directory {}", serverDir, e);
				}
			}
		}

	}

}
