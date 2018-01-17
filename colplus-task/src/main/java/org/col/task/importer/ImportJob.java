package org.col.task.importer;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.Dataset;
import org.col.api.DatasetImport;
import org.col.config.TaskServerConfig;
import org.col.dao.DatasetImportDao;
import org.col.task.importer.dwca.Normalizer;
import org.col.task.importer.neo.NeoDbFactory;
import org.col.task.importer.neo.NormalizerStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Callable;

import static org.col.TaskServer.MDC_KEY_TASK;

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
  private final TaskServerConfig cfg;
  private final DownloadUtil downloader;
  private final SqlSessionFactory factory;
  private final DatasetImportDao dao;

  ImportJob(Dataset d, boolean force, TaskServerConfig cfg, DownloadUtil downloader, SqlSessionFactory factory) {
    this.datasetKey = d.getKey();
    this.dataset = d;
    this.force = force;
    this.cfg = cfg;
    this.downloader = downloader;
    this.factory = factory;
    dao = new DatasetImportDao(factory);
  }

  @Override
  public DatasetImport call() {
    MDC.put(MDC_KEY_TASK, getClass().getSimpleName());
    MDC.put(MDC_KEY_DATASET, String.valueOf(datasetKey));

    try {
      importDataset();
    } finally {
      MDC.remove(MDC_KEY_TASK);
      MDC.remove(MDC_KEY_DATASET);
    }
    return di;
  }

  private void importDataset() {
    final File dwcaDir = cfg.normalizer.sourceDir(datasetKey);
    NormalizerStore store = null;

    try {
      di = dao.createRunning(dataset);
      LOG.info("Start new import attempt {} for dataset {}: {}", di.getAttempt(), datasetKey, dataset.getTitle());

      LOG.info("Downloading sources for dataset {} from {}", datasetKey, di.getDownloadUri());
      File dwca = cfg.normalizer.source(datasetKey);
      dwca.getParentFile().mkdirs();

      final boolean isModified = downloader.downloadIfModified(di.getDownloadUri(), dwca);
      if(isModified || force) {
        if (!isModified) {
          LOG.info("Force reimport of unchanged archive {}", datasetKey);
        }
        di.setDownload(downloader.lastModified(dwca));

        LOG.info("Extracting files from archive {}", datasetKey);
        CompressionUtil.decompressFile(dwcaDir, dwca);

        LOG.info("Normalizing {}!", datasetKey);
        store = NeoDbFactory.create(cfg.normalizer, datasetKey);
        new Normalizer(store, dwcaDir).run();

        LOG.info("Writing {} to Postgres!", datasetKey);
        store = NeoDbFactory.open(cfg.normalizer, datasetKey);
        new PgImport(datasetKey, store, factory, cfg.importer).run();

        LOG.info("Build import metrics for dataset {}", datasetKey);
        dao.updateImportSuccess(di);

        LOG.info("Dataset import {} completed in {}", datasetKey,
            DurationFormatUtils.formatDurationHMS(Duration.between(di.getStarted(), LocalDateTime.now()).toMillis())
        );

      } else {
        LOG.info("Dataset {} sources unchanged. Stop import", datasetKey);
        di.setDownload(downloader.lastModified(dwca));
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
        // delete it
        File storeDir = cfg.normalizer.neoDir(datasetKey);
        LOG.debug("Remove NormalizerStore at {}", storeDir);
        try {
          FileUtils.deleteDirectory(storeDir);
        } catch (IOException e) {
          LOG.error("Failed to remove NormalizerStore at {}", storeDir, e);
        }
      }
      // remove decompressed dwca folder
      LOG.debug("Remove uncompressed dwca dir {}", dwcaDir.getAbsolutePath());
      try {
        FileUtils.deleteDirectory(dwcaDir);
      } catch (IOException e) {
        LOG.error("Failed to remove uncompressed dwca dir {}", dwcaDir, e);
      }
    }
  }

}
