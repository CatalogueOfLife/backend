package org.col.db;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.col.common.io.PathUtils;
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
			// cached server directory to not unzip postgres binaries on every run is flawed:
			// https://github.com/yandex-qatools/postgresql-embedded/issues/142

			// use workaround with system property instead!
			tmpServerDir = cfg.host == null;
			serverDir = tmpServerDir ? Files.createTempDirectory("colplus-pg-") : Paths.get(cfg.host);
			System.setProperty("de.flapdoodle.embed.io.tmpdir", serverDir.toString());
			LOG.debug("Use embedded Postgres, server dir={}", serverDir);

			postgres = new EmbeddedPostgres(Version.V10_3);
			// assigned some free port using local socket 0
			cfg.port = new ServerSocket(0).getLocalPort();
			cfg.host = "localhost";
			cfg.maximumPoolSize = 3;
			//postgres.start(EmbeddedPostgres.defaultRuntimeConfig(),
			//		cfg.host, cfg.port, cfg.database, cfg.user, cfg.password,
			//		DEFAULT_ADD_PARAMS
			//);
			postgres.start(cfg.host, cfg.port, cfg.database, cfg.user, cfg.password);
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
			final File dataDir = postgres.getConfig().get().storage().dbDir();
			LOG.info("Stopping embedded Postgres server={}, data={}", serverDir, dataDir);
			postgres.stop();

			if (tmpServerDir) {
				try {
					FileUtils.deleteDirectory(serverDir.toFile());
					LOG.info("Removed Postgres server directory {}", serverDir);
				} catch (IllegalArgumentException | IOException e) {
					LOG.warn("Failed to remove Postgres server directory {}", serverDir, e);
				}
			} else if (dataDir.exists()) {
				try {
					FileUtils.deleteDirectory(dataDir);
					PathUtils.removeFileAndParentsIfEmpty(dataDir.toPath());
					LOG.info("Removed Postgres data directory {}", dataDir);
				} catch (IllegalArgumentException | IOException e) {
					LOG.warn("Failed to remove Postgres data directory {}", dataDir, e);
				}
			} else {
				LOG.info("Postgres data directory {} already removed, server dir {} preserved for subsequent runs", dataDir, serverDir);
			}
		}

	}

}
