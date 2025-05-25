package life.catalogue.importer;

import life.catalogue.api.event.DatasetDataChanged;
import life.catalogue.api.model.DatasetImport;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.api.vocab.Setting;
import life.catalogue.common.io.ChecksumUtils;
import life.catalogue.common.io.CompressionUtil;
import life.catalogue.common.io.DownloadException;
import life.catalogue.common.io.DownloadUtil;
import life.catalogue.common.lang.Exceptions;
import life.catalogue.common.lang.InterruptedRuntimeException;
import life.catalogue.common.util.LoggingUtils;
import life.catalogue.concurrent.StartNotifier;
import life.catalogue.config.ImporterConfig;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.DecisionDao;
import life.catalogue.dao.SectorDao;
import life.catalogue.db.mapper.DatasetImportMapper;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.event.EventBroker;
import life.catalogue.img.ImageService;
import life.catalogue.img.LogoUpdateJob;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.NeoDbFactory;
import life.catalogue.importer.proxy.ArchiveDescriptor;
import life.catalogue.importer.proxy.DistributedArchiveService;
import life.catalogue.matching.decision.DecisionRematchRequest;
import life.catalogue.matching.decision.DecisionRematcher;
import life.catalogue.matching.decision.SectorRematchRequest;
import life.catalogue.matching.decision.SectorRematcher;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.metadata.DoiResolver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import jakarta.validation.Validator;

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
  private final ImporterConfig iCfg;
  private final NormalizerConfig nCfg;
  private final DownloadUtil downloader;
  private final SqlSessionFactory factory;
  private final DatasetImportDao dao;
  private final DatasetDao dDao;
  private final DecisionDao decisionDao;
  private final SectorDao sDao;
  private final NameIndex index;
  private final NameUsageIndexService indexService;
  private final ImageService imgService;
  private final Validator validator;
  private final DoiResolver resolver;
  private final DistributedArchiveService distributedArchiveService;
  private final EventBroker bus;
  private final StartNotifier notifier;
  private final Consumer<ImportRequest> successCallback;
  private final BiConsumer<ImportRequest, Exception> errorCallback;
  
  ImportJob(ImportRequest req, DatasetWithSettings d,
            ImporterConfig iCfg, NormalizerConfig nCfg,
            DownloadUtil downloader, SqlSessionFactory factory, NameIndex index, Validator validator, DoiResolver resolver,
            NameUsageIndexService indexService, ImageService imgService,
            DatasetImportDao diao, DatasetDao dDao, SectorDao sDao, DecisionDao decisionDao, EventBroker bus,
            StartNotifier notifier,
            Consumer<ImportRequest> successCallback,
            BiConsumer<ImportRequest, Exception> errorCallback
    ) {
    this.validator = validator;
    this.dataset = Preconditions.checkNotNull(d);
    this.datasetKey = d.getKey();
    this.req = req;
    this.iCfg = iCfg;
    this.nCfg = nCfg;
    this.downloader = downloader;
    this.resolver = resolver;
    this.distributedArchiveService = new DistributedArchiveService(downloader.getClient());
    this.factory = factory;
    this.index = index;
    this.indexService = indexService;
    this.dDao = dDao;
    this.sDao = sDao;
    this.decisionDao = decisionDao;
    this.dao = diao;
    this.imgService = imgService;
    this.bus = bus;

    this.notifier = notifier;
    this.successCallback = successCallback;
    this.errorCallback = errorCallback;

    validate();
  }
  
  private void validate() {
    if (dataset.getOrigin().isRelease()) {
      throw new IllegalArgumentException("Dataset " + datasetKey + " is released and cannot be imported");

    } else if (req.reimportAttempt != null && req.hasUpload()) {
      throw new IllegalArgumentException("Dataset " + datasetKey + " cannot be reimported and uploaded");

    } else if (req.reimportAttempt != null) {
      if (!nCfg.archive(datasetKey, req.reimportAttempt).exists()) {
       throw new IllegalArgumentException("Dataset " + datasetKey + " lacks a source archive for import attempt " + req.reimportAttempt);
      }

    } else if (req.hasUpload()) {
      if(!Files.exists(req.upload)) {
        throw new IllegalArgumentException("Dataset " + datasetKey + " lacks upload file at " + req.upload);
      }

    } else if (!dataset.has(Setting.DATA_ACCESS)) {
      throw new IllegalArgumentException("Dataset " + datasetKey + " lacks a data access URL");
    }
  }
  
  public ImportRequest getRequest() {
    return req;
  }
  
  @Override
  public void run() {
    LoggingUtils.setDatasetMDC(datasetKey, getClass());
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
  
  private void updateState(ImportState state) throws InterruptedException {
    di.setState(state);
    dao.update(di);
    checkIfCancelled();
  }

  private void setFormat(DataFormat format) {
    di.setFormat(format);
    dataset.setDataFormat(format);
  }

  private void checkIfCancelled() throws InterruptedException {
    Exceptions.interruptIfCancelled("Import " + di.attempt() + " was cancelled");
  }

  /**
   * Prepares the source files so we can run the normalizer.
   * This includes downloading, proxy downloads, modified checks and checks for uploads detecting the actual format
   * @return true if sourceDir should be imported
   */
  private boolean prepareSourceData(Path sourceDir) throws IOException, IllegalArgumentException, InterruptedException {
    File archive = nCfg.archive(datasetKey, getAttempt());
    archive.getParentFile().mkdirs();

    if (req.reimportAttempt != null) {
      // copy previous up/downloaded archive to repository
      Path prev = nCfg.archive(datasetKey, req.reimportAttempt).toPath();
      LOG.info("Symlink previous archive from import attempt {} for dataset {}: {}", req.reimportAttempt, datasetKey, prev);
      Files.createSymbolicLink(archive.toPath(), prev);

    } else if (req.hasUpload()) {
      // if data was uploaded we need to find out the format.
      // We do this after decompression, but we need to check if we have a proxy descriptor so we can download the real files first
      if (DataFormatDetector.isProxyDescriptor(req.getUpload())) {
        updateState(ImportState.DOWNLOADING);
        ArchiveDescriptor proxy = distributedArchiveService.upload(archive);
        setFormat(proxy.format);
      } else {
        // copy uploaded data to repository
        LOG.info("Move upload for dataset {} from {} to {}", datasetKey, req.upload, archive);
        Files.move(req.upload, archive.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }

    } else if (DatasetOrigin.EXTERNAL == dataset.getOrigin()){
      di.setDownloadUri(dataset.getDataAccess());
      updateState(ImportState.DOWNLOADING);
      if (dataset.getDataFormat() == DataFormat.PROXY) {
        ArchiveDescriptor proxy = distributedArchiveService.download(dataset.getDataAccess(), archive);
        setFormat(proxy.format);
      } else {
        // download archive directly
        if (req.force) {
          LOG.info("Force download of source for dataset {} from {} to {}", datasetKey, dataset.getDataAccess(), archive);
          downloader.download(di.getDownloadUri(), archive);
        } else {
          LOG.info("Download source for dataset {} from {} to {}", datasetKey, dataset.getDataAccess(), archive);
          downloader.downloadIfModified(di.getDownloadUri(), archive);
        }
        // make sure we received a real archive and not just a plain text error response
        // https://github.com/CatalogueOfLife/backend/issues/1327
        var size = Files.size(archive.toPath());
        // 22 bytes is the minimum zip file length, BDJ responds with 14 text bytes only
        if (size < 22) {
          throw new DownloadException(di.getDownloadUri(), "Tiny file with "+size+" bytes. Cannot be an archive");
        }
      }

    } else {
      // no point to import a managed or released dataset without an upload
      throw new IllegalStateException("Dataset " + datasetKey + " is not external and there are no uploads to be imported");
    }


    di.setMd5(ChecksumUtils.getMD5Checksum(archive));
    di.setDownload(downloader.lastModified(archive));
    dao.update(di);

    boolean isModified = true;
    if (dataset.getImportAttempt() != null) {
      try (SqlSession session = factory.openSession()) {
        String lastMD5 = session.getMapper(DatasetImportMapper.class).getMD5(datasetKey, dataset.getImportAttempt());
        if (Objects.equals(lastMD5, di.getMd5())) {
          // replace archive with symlink to last archive to save space
          File lastArchive = nCfg.archive(datasetKey, dataset.getImportAttempt());
          if (lastArchive.exists()) {
            // we have seen wrong MD5 hashes being stored (BDJ), so lets recalculate the last one from the file to make sure!
            var lastMD5Redone = ChecksumUtils.getMD5Checksum(lastArchive);
            if (lastMD5Redone.equals(lastMD5)) {
              isModified = false;
              LOG.info("MD5 unchanged: {}", di.getMd5());
              Path lastReal = lastArchive.toPath().toRealPath();
              if (archive.exists()) {
                archive.delete();
              }
              Files.createSymbolicLink(archive.toPath(), lastReal);

            } else {
              LOG.info("MD5 stored for attempt {} differs from stored file. Consider archive {}#{} modified", dataset.getImportAttempt(), datasetKey, getAttempt());
            }
          }
        } else {
          LOG.info("MD5 changed from attempt {}: {} to {}", dataset.getImportAttempt(), lastMD5, di.getMd5());
        }
      }
    }

    // update latest symlink
    File latest = nCfg.lastestArchiveSymlink(datasetKey);
    if (latest.exists()) {
      latest.delete();
    }
    Files.createSymbolicLink(latest.toPath(), archive.toPath());

    checkIfCancelled();
    // decompress and import?
    if (isModified || req.force) {
      if (req.force) {
        LOG.info("Force reimport of unchanged archive {}", datasetKey);
      }
      LOG.info("Extracting files from archive {}", datasetKey);
      CompressionUtil.decompressFile(sourceDir.toFile(), archive);

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
    di = dao.createWaiting(datasetKey, this, req.createdBy);
    LoggingUtils.setDatasetMDC(datasetKey, getAttempt(), getClass());
    LOG.info("Start new {}import attempt {} for {} dataset {}: {}", req.force ? "forced " : "" ,di.getAttempt(), dataset.getOrigin(), datasetKey, dataset.getTitle());

    final Path sourceDir = nCfg.sourceDir(datasetKey).toPath();
    NeoDb store = null;

    try {
      final boolean doImport = prepareSourceData(sourceDir);
      if (doImport) {
        LOG.info("Normalizing {}", datasetKey);
        updateState(ImportState.PROCESSING);
        store = NeoDbFactory.create(datasetKey, getAttempt(), nCfg);
        new Normalizer(dataset, store, sourceDir, index, imgService, validator, resolver).call();
  
        LOG.info("Fetching logo for {}", datasetKey);
        LogoUpdateJob.updateDatasetAsync(dataset.getDataset(), factory, downloader, nCfg::scratchFile, imgService, req.createdBy);
        
        LOG.info("Writing {} to Postgres & Elastic!", datasetKey);
        updateState(ImportState.INSERTING);
        store = NeoDbFactory.open(datasetKey, getAttempt(), nCfg);
        // this does write to both pg and elastic!
        var pgImport = new PgImport(di.getAttempt(), dataset, req.createdBy, store, factory, iCfg, dDao, indexService);
        pgImport.call();

        LOG.info("Build import metrics for dataset {}", datasetKey);
        updateState(ImportState.ANALYZING);
        di.setMaxClassificationDepth(pgImport.getMaxDepth());
        dao.updateMetrics(di, datasetKey);

        bus.publish().datasetDataChanged(new DatasetDataChanged(datasetKey));

        if (rematchDecisions()) {
          updateState(ImportState.MATCHING);
          LOG.info("Rematching all decisions from any project for subject dataset {}", datasetKey);
          for (Integer projectKey : decisionDao.listProjects(datasetKey)) {
            DecisionRematcher.match(decisionDao, new DecisionRematchRequest(projectKey, datasetKey, false), req.createdBy);
          }
          LOG.info("Rematching all sectors from any project for subject dataset {}", datasetKey);
          for (Integer projectKey : sDao.listProjects(datasetKey)) {
            SectorRematcher.match(sDao, new SectorRematchRequest(projectKey, datasetKey), req.createdBy);
          }
        }

        LOG.info("Dataset import {} completed in {}", datasetKey,
            DurationFormatUtils.formatDurationHMS(Duration.between(di.getStarted(), LocalDateTime.now()).toMillis()));
        di.setFinished(LocalDateTime.now());
        di.setError(null);
        updateState(ImportState.FINISHED);
  
      } else {
        LOG.info("Dataset {} sources unchanged. Stop import", datasetKey);
        dao.delete(datasetKey, di.getAttempt());
      }
  
      if (iCfg.wait > 0) {
        LOG.info("Wait for {}s before finishing import job", iCfg.wait);
        try {
          TimeUnit.SECONDS.sleep(iCfg.wait);
        } catch (InterruptedException e) {
          LOG.debug("Woke up dataset {} from sleep", datasetKey);
          // swallow, we only interrupted the sleep and the import is done anyways.
          // Just continue
        }
      }
  
    } catch (InterruptedException | InterruptedRuntimeException e) {
      // cancelled import
      LOG.warn("Dataset {} import cancelled", datasetKey);
      dao.updateImportCancelled(di);
      
    } catch (Throwable e) {
      // failed import
      LOG.error("Dataset {} import failed. {}", datasetKey, e.getMessage(), e);
      dao.updateImportFailure(di, e);
      throw e;
      
    } finally {
      // close neo store if open
      if (store != null) {
        store.close();
      }
      // remove source scratch folder with neo4j and decompressed dwca folders
      final File scratchDir = nCfg.scratchDir(datasetKey);
      LOG.debug("Remove scratch dir {}", scratchDir.getAbsolutePath());
      try {
        FileUtils.deleteDirectory(scratchDir);
      } catch (IOException e) {
        LOG.error("Failed to remove scratch dir {}", scratchDir, e);
      }
      // update last contact
      try (SqlSession session = factory.openSession(true)) {
        session.getMapper(DatasetMapper.class).updateLastImportAttempt(datasetKey);
      }
      // flush dataset in varnish
      bus.publish().datasetDataChanged(new DatasetDataChanged(datasetKey));
    }
  }

  private boolean rematchDecisions() {
    return dataset.isEnabled(Setting.REMATCH_DECISIONS);
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
