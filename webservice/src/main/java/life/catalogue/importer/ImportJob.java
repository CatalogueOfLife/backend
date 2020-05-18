package life.catalogue.importer;

import com.google.common.base.Preconditions;
import life.catalogue.WsServerConfig;
import life.catalogue.api.model.DatasetImport;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.api.vocab.Setting;
import life.catalogue.common.concurrent.StartNotifier;
import life.catalogue.common.io.ChecksumUtils;
import life.catalogue.common.io.CompressionUtil;
import life.catalogue.common.io.DownloadUtil;
import life.catalogue.common.lang.Exceptions;
import life.catalogue.common.lang.InterruptedRuntimeException;
import life.catalogue.common.util.LoggingUtils;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.SubjectRematcher;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.img.ImageService;
import life.catalogue.img.LogoUpdateJob;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.NeoDbFactory;
import life.catalogue.importer.proxy.ArchiveDescriptor;
import life.catalogue.importer.proxy.DistributedArchiveService;
import life.catalogue.matching.NameIndex;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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
  private final DatasetWithSettings dataset;
  private DatasetImport di;
  private DatasetImport last;
  private final WsServerConfig cfg;
  private final DownloadUtil downloader;
  private final SqlSessionFactory factory;
  private final DatasetImportDao dao;
  private final NameIndex index;
  private final NameUsageIndexService indexService;
  private final ImageService imgService;
  private final DistributedArchiveService distributedArchiveService;
  
  private final StartNotifier notifier;
  private final Consumer<ImportRequest> successCallback;
  private final BiConsumer<ImportRequest, Exception> errorCallback;
  
  ImportJob(ImportRequest req, DatasetWithSettings d,
            WsServerConfig cfg,
            DownloadUtil downloader, SqlSessionFactory factory, NameIndex index,
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
    this.indexService = indexService;
    dao = new DatasetImportDao(factory, cfg.metricsRepo);
    this.imgService = imgService;
    
    this.notifier = notifier;
    this.successCallback = successCallback;
    this.errorCallback = errorCallback;

    validate();
  }
  
  private void validate() {
    if (dataset.getOrigin() == DatasetOrigin.RELEASED) {
      throw new IllegalArgumentException("Dataset " + datasetKey + " is released and cannot be imported");
      
    } else if (!req.upload && dataset.getOrigin() == DatasetOrigin.EXTERNAL && !dataset.has(Setting.DATA_ACCESS)) {
      throw new IllegalArgumentException("Dataset " + datasetKey + " is external but lacks a data access URL");
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

  private void setFormat(DataFormat format) {
    di.setFormat(format);
    dataset.setDataFormat(format);
  }

  private void checkIfCancelled() {
    Exceptions.interruptIfCancelled("Import " + di.attempt() + " was cancelled");
  }

  /**
   * Prepares the source files so we can run the normalizer.
   * This includes downloading, proxy downloads, modified checks and checks for uploads detecting the actual format
   * @return true if sourceDir should be imported
   */
  private boolean prepareSourceData(Path sourceDir) throws IOException, IllegalArgumentException {
    last = dao.getLast(dataset.getKey());

    File source = cfg.normalizer.source(datasetKey);
    source.getParentFile().mkdirs();
    if (req.upload) {
      // if data was uploaded we need to find out the format.
      // We do this after decompression, but we need to check if we have a proxy descriptor so we can download the real files first
      if (DataFormatDetector.isProxyDescriptor(source)) {
        updateState(ImportState.DOWNLOADING);
        ArchiveDescriptor proxy = distributedArchiveService.uploaded(source);
        setFormat(proxy.format);
      }

    } else if (DatasetOrigin.EXTERNAL == dataset.getOrigin()){
      di.setDownloadUri(dataset.getDataAccess());
      updateState(ImportState.DOWNLOADING);
      if (dataset.getDataFormat() == DataFormat.PROXY) {
        ArchiveDescriptor proxy = distributedArchiveService.download(dataset.getDataAccess(), source);
        setFormat(proxy.format);
      } else {
        // download archive directly
        LOG.info("Downloading source for dataset {} from {} to {}", datasetKey, dataset.getDataAccess(), source);
        downloader.downloadIfModified(di.getDownloadUri(), source);
      }

    } else {
      // no point to import a managed or released dataset without an upload
      throw new IllegalStateException("Dataset " + datasetKey + " is not external and there are no uploads to be imported");
    }

    boolean isModified = lastMD5IsDifferent(source);
    di.setDownload(downloader.lastModified(source));
    dao.update(di);

    checkIfCancelled();
    // decompress and import?
    if (isModified || req.force) {
      if (!isModified) {
        LOG.info("Force reimport of unchanged archive {}", datasetKey);
      }
      LOG.info("Extracting files from archive {}", datasetKey);
      CompressionUtil.decompressFile(sourceDir.toFile(), source);

      // detect data format if not set from proxy yet
      if (dataset.getDataFormat() == null) {
        setFormat(DataFormatDetector.detectFormat(sourceDir));
        LOG.info("Detected data format {} for dataset {}", dataset.getDataFormat(), dataset.getKey());
      }
      return true;
    }
    return false;
  }

  private void importDataset() throws Exception {
    di = dao.createWaiting(dataset.getDataset(), this, req.createdBy);
    LoggingUtils.setDatasetMDC(datasetKey, getAttempt(), getClass());
    LOG.info("Start new import attempt {} for {} dataset {}: {}", di.getAttempt(), dataset.getOrigin(), datasetKey, dataset.getTitle());

    final Path sourceDir = cfg.normalizer.sourceDir(datasetKey).toPath();
    NeoDb store = null;

    try {
      final boolean doImport = prepareSourceData(sourceDir);
      checkIfCancelled();
      if (doImport) {
        LOG.info("Normalizing {}", datasetKey);
        updateState(ImportState.PROCESSING);
        store = NeoDbFactory.create(datasetKey, getAttempt(), cfg.normalizer);
        new Normalizer(dataset, store, sourceDir, index, imgService).call();
  
        LOG.info("Fetching logo for {}", datasetKey);
        LogoUpdateJob.updateDatasetAsync(dataset.getDataset(), factory, downloader, cfg.normalizer::scratchFile, imgService);
        
        LOG.info("Writing {} to Postgres!", datasetKey);
        updateState(ImportState.INSERTING);
        store = NeoDbFactory.open(datasetKey, getAttempt(), cfg.normalizer);
        new PgImport(dataset, store, factory, cfg.importer).call();
        // update dataset with latest success attempt now that all data is in postgres - even if we fail further down
        dao.updateDatasetLastAttempt(di);

        LOG.info("Build import metrics for dataset {}", datasetKey);
        updateState(ImportState.BUILDING_METRICS);
        dao.updateMetrics(di);
  
        LOG.info("Build search index for dataset {}", datasetKey);
        updateState(ImportState.INDEXING);
        indexService.indexDataset(datasetKey);

        if (rematchDecisions()) {
          updateState(ImportState.MATCHING);
          LOG.info("Updating sectors and decisions for dataset {}", datasetKey);
          new SubjectRematcher(factory, req.createdBy).matchDatasetSubjects(datasetKey);
        }

        LOG.info("Dataset import {} completed in {}", datasetKey,
            DurationFormatUtils.formatDurationHMS(Duration.between(di.getStarted(), LocalDateTime.now()).toMillis()));
        di.setFinished(LocalDateTime.now());
        di.setError(null);
        updateState(ImportState.FINISHED);
  
      } else {
        LOG.info("Dataset {} sources unchanged. Stop import", datasetKey);
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
      throw e;
      
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

  private boolean rematchDecisions() {
    return dataset.has(Setting.REMATCH_DECISIONS)
        && dataset.getBool(Setting.REMATCH_DECISIONS);
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
