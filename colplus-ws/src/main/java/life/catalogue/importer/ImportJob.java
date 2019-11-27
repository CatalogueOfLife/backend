package life.catalogue.importer;

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
import org.apache.ibatis.session.SqlSessionFactory;
import life.catalogue.WsServerConfig;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetImport;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.common.concurrent.StartNotifier;
import life.catalogue.common.io.ChecksumUtils;
import life.catalogue.common.io.CompressionUtil;
import life.catalogue.common.io.DownloadUtil;
import life.catalogue.common.lang.Exceptions;
import life.catalogue.common.lang.InterruptedRuntimeException;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.util.LoggingUtils;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.SubjectRematcher;
import life.catalogue.es.name.index.NameUsageIndexService;
import life.catalogue.img.ImageService;
import life.catalogue.img.LogoUpdateJob;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.NeoDbFactory;
import life.catalogue.importer.proxy.DistributedArchiveService;
import life.catalogue.matching.NameIndex;
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
  private final AuthorshipNormalizer aNormalizer;
  private final DistributedArchiveService distributedArchiveService;
  
  private final StartNotifier notifier;
  private final Consumer<ImportRequest> successCallback;
  private final BiConsumer<ImportRequest, Exception> errorCallback;
  
  ImportJob(ImportRequest req, Dataset d,
            WsServerConfig cfg,
            DownloadUtil downloader, SqlSessionFactory factory, AuthorshipNormalizer aNormalizer, NameIndex index,
            NameUsageIndexService indexService, ImageService imgService,
            StartNotifier notifier,
            Consumer<ImportRequest> successCallback,
            BiConsumer<ImportRequest, Exception> errorCallback
    ) {
    this.dataset = Preconditions.checkNotNull(d);
    this.datasetKey = d.getKey();
    this.req = req;
    this.cfg = cfg;
    this.downloader = downloader;
    this.distributedArchiveService = new DistributedArchiveService(downloader.getClient());
    this.factory = factory;
    this.index = index;
    this.aNormalizer = aNormalizer;
    this.indexService = indexService;
    dao = new DatasetImportDao(factory, cfg.metricsRepo);
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
  
  public ImportRequest getRequest() {
    return req;
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
  
  public DatasetImport getDatasetImport() {
    return di;
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
      last = dao.getLast(dataset.getKey());
      di = dao.create(dataset);
      LoggingUtils.setDatasetMDC(datasetKey, getAttempt(), getClass());
      LOG.info("Start new import attempt {} for {} dataset {}: {}", di.getAttempt(), dataset.getOrigin(), datasetKey, dataset.getTitle());
  
      boolean isModified;
      File source = cfg.normalizer.source(datasetKey);
      if (dataset.getDataFormat() == DataFormat.PROXY) {
        // we have a yaml descriptor for distributed archives, download files individually
        if (dataset.getOrigin() == DatasetOrigin.UPLOADED) {
          dataset.setDataFormat(distributedArchiveService.uploaded(source));
        } else if (dataset.getOrigin() == DatasetOrigin.EXTERNAL) {
          dataset.setDataFormat(distributedArchiveService.download(di.getDownloadUri(), source));
        }
        isModified = lastMD5IsDifferent(source);
        
      } else if (dataset.getOrigin() == DatasetOrigin.UPLOADED) {
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
        new Normalizer(store, sourceDir, index, imgService).call();
  
        LOG.info("Fetching logo for {}", datasetKey);
        LogoUpdateJob.updateDatasetAsync(dataset, factory, downloader, cfg.normalizer::scratchFile, imgService);
        
        LOG.info("Writing {} to Postgres!", datasetKey);
        updateState(ImportState.INSERTING);
        store = NeoDbFactory.open(datasetKey, getAttempt(), cfg.normalizer);
        new PgImport(datasetKey, store, factory, aNormalizer, cfg.importer).call();
        // update dataset with latest success attempt now that all data is in postgres - even if we fail further down
        dao.updateDatasetLastAttempt(di);

        LOG.info("Build import metrics for dataset {}", datasetKey);
        updateState(ImportState.BUILDING_METRICS);
        dao.updateMetrics(di);
  
        LOG.info("Build search index for dataset {}", datasetKey);
        updateState(ImportState.INDEXING);
        indexService.indexDataset(datasetKey);
  
        LOG.info("Updating draft sectors and decisions for dataset {}", datasetKey);
        new SubjectRematcher(factory, Datasets.DRAFT_COL, req.createdBy).matchDatasetSubjects(datasetKey);
  
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
      
    } catch (Throwable e) {
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
