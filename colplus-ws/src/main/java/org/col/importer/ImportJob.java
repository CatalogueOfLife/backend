package org.col.importer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.google.common.base.Preconditions;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.WsServerConfig;
import org.col.dao.DecisionRematcher;
import org.col.importer.neo.NeoDb;
import org.col.importer.neo.NeoDbFactory;
import org.col.matching.NameIndex;
import org.col.api.model.Dataset;
import org.col.api.model.DatasetImport;
import org.col.api.vocab.DatasetOrigin;
import org.col.api.vocab.ImportState;
import org.col.common.concurrent.StartNotifier;
import org.col.common.io.ChecksumUtils;
import org.col.common.io.CompressionUtil;
import org.col.common.io.DownloadUtil;
import org.col.common.lang.Exceptions;
import org.col.common.lang.InterruptedRuntimeException;
import org.col.common.util.LoggingUtils;
import org.col.dao.DatasetImportDao;
import org.col.es.NameUsageIndexService;
import org.col.img.ImageService;
import org.col.img.LogoUpdateJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Asynchronous import job that orchestrates the entire import process including download,
 * normalization and insertion into Postgres.
 * <p>
 * It can be cancelled by an according method at any time. Equality of instances is just based on
 * the datasetKey which allows multiple imports for the same dataset to be easily detected.
 */
public class ImportJob implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(ImportJob.class);
  private final int datasetKey;
  private final ImportRequest req;
  private final Dataset dataset;
  private DatasetImport di;
  private DatasetImport last;
  private final WsServerConfig cfg;
  private final DownloadUtil downloader;
  private final SqlSessionFactory factory;
  private final DatasetImportDao dao;
  private final NameIndex index;
  private final NameUsageIndexService indexService;
  private final ImageService imgService;
  
  private final StartNotifier notifier;
  private final Consumer<ImportRequest> successCallback;
  private final BiConsumer<ImportRequest, Exception> errorCallback;
  
  ImportJob(ImportRequest req, Dataset d,
            WsServerConfig cfg,
            DownloadUtil downloader, SqlSessionFactory factory, NameIndex index, NameUsageIndexService indexService, ImageService imgService,
            StartNotifier notifier,
            Consumer<ImportRequest> successCallback,
            BiConsumer<ImportRequest, Exception> errorCallback
    ) {
    this.dataset = Preconditions.checkNotNull(d);
    this.datasetKey = d.getKey();
    this.req = req;
    this.cfg = cfg;
    this.downloader = downloader;
    this.factory = factory;
    this.index = index;
    this.indexService = indexService;
    dao = new DatasetImportDao(factory);
    this.imgService = imgService;
    
    this.notifier = notifier;
    this.successCallback = successCallback;
    this.errorCallback = errorCallback;

    validate();
  }
  
  private void validate() {
    if (dataset.getOrigin() == DatasetOrigin.MANAGED) {
      throw new IllegalArgumentException("Dataset " + datasetKey + " is managed and cannot be imported");
      
    } else if (dataset.getOrigin() == DatasetOrigin.EXTERNAL && dataset.getDataAccess() == null) {
      throw new IllegalArgumentException("Dataset " + datasetKey + " is external but lacks a data access URL");
      
    } else if (dataset.getOrigin() == DatasetOrigin.UPLOADED && !cfg.normalizer.source(datasetKey).exists()) {
      throw new IllegalArgumentException("Dataset " + datasetKey + " is lacking an uploaded archive");
    }
  }
  
  @Override
  public void run() {
    LoggingUtils.setDatasetMDC(datasetKey, -1, getClass());
    try {
      notifier.started();
      importDataset();
      successCallback.accept(req);
      
    } catch (Exception e) {
      errorCallback.accept(req, e);
    
    } finally {
      LoggingUtils.removeDatasetMDC();
    }
  }
  
  public int getDatasetKey() {
    return datasetKey;
  }
  
  public Integer getAttempt() {
    return di == null ? null : di.getAttempt();
  }
  
  private void updateState(ImportState state) {
    di.setState(state);
    dao.update(di);
    checkIfCancelled();
  }
  
  private void checkIfCancelled() {
    Exceptions.interruptIfCancelled("Import " + di.attempt() + " was cancelled");
  }
  
  private void importDataset() {
    final Path sourceDir = cfg.normalizer.sourceDir(datasetKey).toPath();
    NeoDb store = null;
    
    try {
      last = dao.getLast(dataset);
      di = dao.create(dataset);
      LoggingUtils.setDatasetMDC(datasetKey, getAttempt(), getClass());
      LOG.info("Start new import attempt {} for {} dataset {}: {}", di.getAttempt(), dataset.getOrigin(), datasetKey, dataset.getTitle());
  
      File source = cfg.normalizer.source(datasetKey);
      boolean isModified;
      if (dataset.getOrigin() == DatasetOrigin.UPLOADED) {
        // did we import that very archive before already?
        isModified = lastMD5IsDifferent(source);
        
      } else if (dataset.getOrigin() == DatasetOrigin.EXTERNAL) {
        // first download archive
        source.getParentFile().mkdirs();
        LOG.info("Downloading sources for dataset {} from {} to {}", datasetKey, di.getDownloadUri(), source);
        isModified = downloader.downloadIfModified(di.getDownloadUri(), source) && lastMD5IsDifferent(source);
  
      } else {
        // we catch this in constructor already and should never reach here
        throw new IllegalStateException("Dataset " + datasetKey + " is managed and cannot be imported");
      }
      
      checkIfCancelled();
      if (isModified || req.force) {
        if (!isModified) {
          LOG.info("Force reimport of unchanged archive {}", datasetKey);
        }
        di.setDownload(downloader.lastModified(source));
        checkIfCancelled();
        
        LOG.info("Extracting files from archive {}", datasetKey);
        CompressionUtil.decompressFile(sourceDir.toFile(), source);
        
        LOG.info("Normalizing {}", datasetKey);
        updateState(ImportState.PROCESSING);
        store = NeoDbFactory.create(datasetKey, getAttempt(), cfg.normalizer);
        store.put(dataset);
        new Normalizer(store, sourceDir, index).call();
        LogoUpdateJob.updateDatasetAsync(dataset, factory, downloader, cfg.normalizer::scratchFile, imgService);
        
        LOG.info("Writing {} to Postgres!", datasetKey);
        updateState(ImportState.INSERTING);
        store = NeoDbFactory.open(datasetKey, getAttempt(), cfg.normalizer);
        new PgImport(datasetKey, store, factory, cfg.importer).call();
        // update dataset with latest success attempt now that all data is in postgres - even if we fail further down
        dao.updateDatasetLastAttempt(di);

        LOG.info("Build import metrics for dataset {}", datasetKey);
        updateState(ImportState.BUILDING_METRICS);
        dao.updateMetrics(di);
  
        LOG.info("Build search index for dataset {}", datasetKey);
        updateState(ImportState.INDEXING);
        indexService.indexDataset(datasetKey);
  
        LOG.info("Updating sectors and decisions for dataset {}", datasetKey);
        try(SqlSession session = factory.openSession(true)) {
          new DecisionRematcher(session).matchDataset(datasetKey);
        }
  
        LOG.info("Dataset import {} completed in {}", datasetKey,
            DurationFormatUtils.formatDurationHMS(Duration.between(di.getStarted(), LocalDateTime.now()).toMillis()));
        di.setFinished(LocalDateTime.now());
        di.setError(null);
        updateState(ImportState.FINISHED);
  
      } else {
        LOG.info("Dataset {} sources unchanged. Stop import", datasetKey);
        di.setDownload(downloader.lastModified(source));
        di.setFinished(LocalDateTime.now());
        di.setError(null);
        updateState(ImportState.UNCHANGED);
      }
  
      if (cfg.importer.wait > 0) {
        LOG.info("Wait for {}s before finishing import job", cfg.importer.wait);
        try {
          TimeUnit.SECONDS.sleep(cfg.importer.wait);
        } catch (InterruptedException e) {
          LOG.debug("Woke up dataset {} from sleep", datasetKey);
          // swallow, we only interrupted the sleep and the import is done anyways.
          // Just continue
        }
      }
  
    } catch (InterruptedException | InterruptedRuntimeException e) {
      // cancelled import
      LOG.warn("Dataset {} import cancelled. Log to db", datasetKey);
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
      final File scratchDir = cfg.normalizer.scratchDir(datasetKey);
      LOG.debug("Remove scratch dir {}", scratchDir.getAbsolutePath());
      try {
        FileUtils.deleteDirectory(scratchDir);
      } catch (IOException e) {
        LOG.error("Failed to remove scratch dir {}", scratchDir, e);
      }
    }
  }
  
  /**
   * See https://github.com/Sp2000/colplus-backend/issues/78
   *
   * @return true if the source file has a different MD5 hash as the last imported file
   */
  private boolean lastMD5IsDifferent(File source) throws IOException {
    di.setMd5(ChecksumUtils.getMD5Checksum(source));
    if (last != null) {
      LOG.debug("Compare with last MD5 {}", last.getMd5());
      return !di.getMd5().equals(last.getMd5());
    } else {
      return true;
    }
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    ImportJob importJob = (ImportJob) o;
    return datasetKey == importJob.datasetKey;
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(datasetKey);
  }
  
  @Override
  public String toString() {
    return "ImportJob{" + "datasetKey=" + datasetKey + ", force=" + req.force + '}';
  }
}
