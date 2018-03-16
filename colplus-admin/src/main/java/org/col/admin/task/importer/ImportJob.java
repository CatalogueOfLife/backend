package org.col.admin.task.importer;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.admin.AdminServer;
import org.col.admin.config.AdminServerConfig;
import org.col.admin.task.importer.neo.NeoDb;
import org.col.admin.task.importer.neo.NeoDbFactory;
import org.col.api.model.Dataset;
import org.col.api.model.DatasetImport;
import org.col.db.dao.DatasetImportDao;
import org.col.util.io.CompressionUtil;
import org.col.util.io.DownloadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Callable;

/**
 * Base task setting up MDC logging properties.
 */
public class ImportJob implements Callable<DatasetImport> {
  private static final Logger LOG = LoggerFactory.getLogger(ImportJob.class);
  private static final String MDC_KEY_DATASET = "dataset";

  private final int datasetKey;
  private final boolean force;
  private final Dataset dataset;
  private DatasetImport di;
  private final AdminServerConfig cfg;
  private final DownloadUtil downloader;
  private final SqlSessionFactory factory;
  private final DatasetImportDao dao;

  ImportJob(Dataset d, boolean force, AdminServerConfig cfg, DownloadUtil downloader, SqlSessionFactory factory) {
    this.datasetKey = d.getKey();
    this.dataset = d;
    this.force = force;
    this.cfg = cfg;
    this.downloader = downloader;
    this.factory = factory;
    dao = new DatasetImportDao(factory);
  }

  public static void setMDC(int datasetKey) {
    MDC.put(AdminServer.MDC_KEY_TASK, ImportJob.class.getSimpleName());
    MDC.put(MDC_KEY_DATASET, String.valueOf(datasetKey));
  }

  public static void removeMDC() {
    MDC.remove(AdminServer.MDC_KEY_TASK);
    MDC.remove(MDC_KEY_DATASET);
  }

  @Override
  public DatasetImport call() {
    setMDC(datasetKey);
    try {
      importDataset();
    } finally {
      removeMDC();
    }
    return di;
  }

  private void importDataset() {
    final Path dwcaDir = cfg.normalizer.sourceDir(datasetKey).toPath();
    NeoDb store = null;

    try {
      di = dao.createRunning(dataset);
      LOG.info("Start new import attempt {} for dataset {}: {}", di.getAttempt(), datasetKey, dataset.getTitle());

      File source = cfg.normalizer.source(datasetKey);
      source.getParentFile().mkdirs();

      LOG.info("Downloading sources for dataset {} from {} to {}", datasetKey, di.getDownloadUri(), source);
      final boolean isModified = downloader.downloadIfModified(di.getDownloadUri(), source);
      if(isModified || force) {
        if (!isModified) {
          LOG.info("Force reimport of unchanged archive {}", datasetKey);
        }
        di.setDownload(downloader.lastModified(source));

        LOG.info("Extracting files from archive {}", datasetKey);
        CompressionUtil.decompressFile(dwcaDir.toFile(), source);

        LOG.info("Normalizing {}", datasetKey);
        store = NeoDbFactory.create(datasetKey, cfg.normalizer);
        store.put(dataset);
        new Normalizer(store, dwcaDir).run();

        LOG.info("Writing {} to Postgres!", datasetKey);
        store = NeoDbFactory.open(datasetKey, cfg.normalizer);
        new PgImport(datasetKey, store, factory, cfg.importer).run();

        LOG.info("Build import metrics for dataset {}", datasetKey);
        dao.updateImportSuccess(di);

        LOG.info("Dataset import {} completed in {}", datasetKey,
            DurationFormatUtils.formatDurationHMS(Duration.between(di.getStarted(), LocalDateTime.now()).toMillis())
        );

      } else {
        LOG.info("Dataset {} sources unchanged. Stop import", datasetKey);
        di.setDownload(downloader.lastModified(source));
        dao.updateImportUnchanged(di);
      }

    } catch (Exception e) {
      // failed import
      LOG.error("Dataset {} import failed. Log to pg.", datasetKey, e);
      dao.updateImportFailure(di, e);

    } finally {
      // close neo store if open
      if (store != null) {
        store.close();
      }
      // remove source scratch folder with neo4j and decompressed dwca folders
      final File scratchDir  = cfg.normalizer.scratchDir(datasetKey);
      LOG.debug("Remove scratch dir {}", scratchDir.getAbsolutePath());
      try {
        FileUtils.deleteDirectory(scratchDir);
      } catch (IOException e) {
        LOG.error("Failed to remove scratch dir {}", scratchDir, e);
      }
    }
  }
}
