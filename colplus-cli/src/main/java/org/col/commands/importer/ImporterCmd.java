package org.col.commands.importer;

import com.zaxxer.hikari.HikariDataSource;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.Dataset;
import org.col.commands.config.CliConfig;
import org.col.commands.importer.dwca.Normalizer;
import org.col.commands.importer.neo.NeoDbFactory;
import org.col.commands.importer.neo.NormalizerStore;
import org.col.dao.DatasetDao;
import org.col.db.MybatisFactory;
import org.col.db.mapper.DatasetMapper;
import org.gbif.util.DownloadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class ImporterCmd extends ConfiguredCommand<CliConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(ImporterCmd.class);

  private CliConfig cfg;

  public ImporterCmd() {
    super("import", "Imports the latest version of an external dataset into staging");
  }

  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    // Adds import options
    subparser.addArgument("-k", "--key")
        .dest("key")
        .type(Integer.class)
        .required(false)
        .help("The key of the dataset to import");
    subparser.addArgument("--all")
        .dest("all")
        .type(Boolean.class)
        .setDefault(false)
        .setConst(true)
        .required(false)
        .help("Import all datasets");
  }

  @Override
  protected void run(Bootstrap<CliConfig> bootstrap, Namespace namespace, CliConfig cfg) throws Exception {
    this.cfg = cfg;

    // create datasource and make sure it gets closed at the end!
    HikariDataSource ds = cfg.db.pool();
    SqlSessionFactory factory = MybatisFactory.configure(ds, "importer");

    try (SqlSession session = factory.openSession(true)){
      DatasetDao ddao = new DatasetDao(session);
      final boolean all = namespace.getBoolean("all");
      if (all) {
        for (Dataset d : ddao.all()){
          importDataset(d, factory);
        }

      } else {
        final int datasetKey = namespace.getInt("key");
        Dataset d = ddao.get(datasetKey);
        if (d == null) {
          throw new IllegalArgumentException("Dataset " + datasetKey + " not existing");

        } else if (d.getDataAccess() == null) {
          throw new IllegalStateException("Dataset " + datasetKey + " has no external datasource configured");
        }
        importDataset(datasetKey, factory);
      }

    } finally {
      ds.close();
    }
  }

  /**
   * @return last modified timestamp of the local download file
   * (which should be the same as the remote file)
   */
  private LocalDateTime lastModified(final int datasetKey) {
    File dwca = cfg.normalizer.source(datasetKey);
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(dwca.lastModified()), ZoneId.systemDefault());
  }

  private void importDataset(int datasetKey, SqlSessionFactory factory) throws Exception {
    try (SqlSession session = factory.openSession(true)){
      Dataset d = session.getMapper(DatasetMapper.class).get(datasetKey);
      if (d == null) {
        throw new IllegalArgumentException("Dataset " + datasetKey + " not existing");

      } else if (d.getDataAccess() == null) {
        throw new IllegalStateException("Dataset " + datasetKey + " has no external datasource configured");
      }
      importDataset(d, factory);
    }
  }

  private void importDataset(Dataset d, SqlSessionFactory factory) throws Exception {
    final int datasetKey = d.getKey();
    final LocalDateTime start = LocalDateTime.now();
    try {
      LOG.info("Downloading sources for dataset {} from {}", datasetKey, d.getDataAccess());
      File dwca = cfg.normalizer.source(datasetKey);
      dwca.getParentFile().mkdirs();
      DownloadUtil.download(d.getDataAccess().toURL(), dwca);

      LOG.info("Extracting files from archive {}", datasetKey);
      File dwcaDir = cfg.normalizer.sourceDir(datasetKey);
      dwcaDir.mkdirs();
      CompressionUtil.decompressFile(dwcaDir, dwca);

      LOG.info("Normalizing {}!", datasetKey);
      NormalizerStore store = NeoDbFactory.create(cfg.normalizer, datasetKey);
      Normalizer normalizer = new Normalizer(store, dwcaDir);
      normalizer.run();

      LOG.info("Writing {} to Postgres!", datasetKey);
      PgImport pgImport = new PgImport(datasetKey,
          NeoDbFactory.open(cfg.normalizer, datasetKey), factory, cfg.importer
      );
      pgImport.run();

      LOG.info("Analyzing {} metrics", datasetKey);
      try (SqlSession session = factory.openSession(true)){
        DatasetDao dao = new DatasetDao(session);
        dao.createImportSuccess(d, start, lastModified(datasetKey));
      }
      LOG.info("Dataset import {} completed in {}",
          datasetKey,
          DurationFormatUtils.formatDurationHMS(Duration.between(start, LocalDateTime.now()).toMillis())
      );

    } catch (Exception e) {
      // failed import
      LOG.error("Dataset {} import failed. Log to pg.", datasetKey, e);
      try (SqlSession session = factory.openSession(true)){
        DatasetDao dao = new DatasetDao(session);
        dao.createImportFailure(d, start, lastModified(datasetKey), e);
      }
      throw e;
    }
  }
}
