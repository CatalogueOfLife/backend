package org.col.admin.importer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.admin.AdminServer;
import org.col.admin.config.AdminServerConfig;
import org.col.admin.importer.neo.NeoDb;
import org.col.admin.importer.neo.NeoDbFactory;
import org.col.admin.matching.NameIndex;
import org.col.api.model.CslData;
import org.col.api.model.Dataset;
import org.col.api.model.DatasetImport;
import org.col.api.vocab.ImportState;
import org.col.common.concurrent.StartNotifier;
import org.col.common.io.CompressionUtil;
import org.col.common.io.DownloadUtil;
import org.col.db.dao.DatasetImportDao;
import org.col.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Asynchronous import job that orchestrates the entire import process
 * including download, normalization and insertion into Postgres.
 *
 * It can be cancelled by an according method at any time.
 * Equality of instances is just based on the datasetKey which allows multiple imports for the same dataset to be easily detected.
 */
public class ImportJob implements Runnable {
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
  private final NameIndex index;
  private final StartNotifier notifier;

  ImportJob(Dataset d, boolean force, AdminServerConfig cfg, DownloadUtil downloader, SqlSessionFactory factory,
             NameIndex index, StartNotifier notifier) {
    this.datasetKey = d.getKey();
    this.dataset = d;
    this.force = force;
    this.cfg = cfg;
    this.downloader = downloader;
    this.factory = factory;
    this.index = index;
    dao = new DatasetImportDao(factory);
    this.notifier = notifier;
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
  public void run() {
    setMDC(datasetKey);
    try {
      notifier.started();
      importDataset();
    } finally {
      removeMDC();
    }
  }

  public int getDatasetKey() {
    return datasetKey;
  }

  public Integer getAttempt() {
    return di == null ? null : di.getAttempt();
  }

  private void updateState(ImportState state) throws InterruptedException {
    di.setState(state);
    dao.update(di);
    checkIfCancelled();
  }

  private void checkIfCancelled() throws InterruptedException {
    if (Thread.currentThread().isInterrupted()) {
      throw new InterruptedException("Import "+ di.attempt() +" was cancelled");
    }
  }

  private void importDataset() {
    final Path dwcaDir = cfg.normalizer.sourceDir(datasetKey).toPath();
    NeoDb store = null;

    try {
      di = dao.createDownloading(dataset);
      LOG.info("Start new import attempt {} for dataset {}: {}", di.getAttempt(), datasetKey, dataset.getTitle());

      File source = cfg.normalizer.source(datasetKey);
      source.getParentFile().mkdirs();

      checkIfCancelled();
      LOG.info("Downloading sources for dataset {} from {} to {}", datasetKey, di.getDownloadUri(), source);
      final boolean isModified = downloader.downloadIfModified(di.getDownloadUri(), source);
      if(isModified || force) {
        if (!isModified) {
          LOG.info("Force reimport of unchanged archive {}", datasetKey);
        }
        di.setDownload(downloader.lastModified(source));

        updateState(ImportState.PROCESSING);
        LOG.info("Extracting files from archive {}", datasetKey);
        CompressionUtil.decompressFile(dwcaDir.toFile(), source);

        checkIfCancelled();
        LOG.info("Normalizing {}", datasetKey);
        store = NeoDbFactory.create(datasetKey, cfg.normalizer);
        store.put(dataset);

        new Normalizer(store, dwcaDir, index).call();

        updateState(ImportState.INSERTING);
        LOG.info("Writing {} to Postgres!", datasetKey);
        store = NeoDbFactory.open(datasetKey, cfg.normalizer);
        new PgImport(datasetKey, store, factory, cfg.importer).call();

        checkIfCancelled();
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


    } catch (InterruptedException e) {
      // cancelled import
      LOG.error("Dataset {} import cancelled. Log to db", datasetKey, e);
      dao.updateImportCancelled(di);

    } catch (Exception e) {
      // failed import
      LOG.error("Dataset {} import failed. {}. Log to db", datasetKey, e.getMessage(), e);
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ImportJob importJob = (ImportJob) o;
    return datasetKey == importJob.datasetKey;
  }

  @Override
  public int hashCode() {
    return Objects.hash(datasetKey);
  }
}
