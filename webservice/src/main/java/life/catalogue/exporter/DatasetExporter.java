package life.catalogue.exporter;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import life.catalogue.WsServerConfig;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.common.io.ChecksumUtils;
import life.catalogue.common.io.CompressionUtil;
import life.catalogue.common.lang.Exceptions;
import life.catalogue.concurrent.DatasetBlockingJob;
import life.catalogue.concurrent.JobPriority;
import life.catalogue.concurrent.UsageCounter;
import life.catalogue.db.mapper.*;
import life.catalogue.img.ImageService;
import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Base class for all dataset exporter that blocks parallel exports for the same dataset
 * and tracks exports by users in the database.
 */
abstract class DatasetExporter extends DatasetBlockingJob {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetExporter.class);

  protected final SqlSessionFactory factory;
  protected final ExportRequest req;
  protected final Dataset dataset;
  protected File archive;
  protected File tmpDir;
  protected final WsServerConfig cfg;
  protected final ImageService imageService;
  protected final UsageCounter counter = new UsageCounter();
  private final DatasetExport export;
  private EmailNotification emailer;

  @VisibleForTesting
  DatasetExporter(ExportRequest req, int userKey, DataFormat requiredFormat, Dataset d, List<SimpleName> classification, SqlSessionFactory factory, WsServerConfig cfg, ImageService imageService) {
    super(req.getDatasetKey(), userKey, JobPriority.LOW);
    if (req.getFormat() == null) {
      req.setFormat(requiredFormat);
    } else if (req.getFormat() != requiredFormat) {
      throw new IllegalArgumentException("Format "+req.getFormat()+" cannot be exported with "+getClass().getSimpleName());
    }
    this.cfg = cfg;
    this.imageService = imageService;
    this.req = Preconditions.checkNotNull(req);
    this.factory = factory;
    this.archive = cfg.downloadFile(getKey());
    this.tmpDir = new File(cfg.normalizer.scratchDir, "export/" + getKey().toString());
    this.dataset = d;
    if (dataset == null || dataset.getDeleted() != null) {
      throw new NotFoundException("Dataset "+datasetKey+" does not exist");
    }
    export = DatasetExport.createWaiting(getKey(), userKey, req, dataset);
    export.setClassification(classification);
    // create waiting export in db
    try (SqlSession session = factory.openSession(true)) {
      session.getMapper(DatasetExportMapper.class).create(export);
    }
    LOG.info("Created {} job {} by user {} for dataset {} to {}", getClass().getSimpleName(), getUserKey(), getKey(), datasetKey, archive);
  }

  DatasetExporter(ExportRequest req, int userKey, DataFormat requiredFormat, SqlSessionFactory factory, WsServerConfig cfg, ImageService imageService) {
    this(req, userKey, requiredFormat, loadDataset(factory, req.getDatasetKey()), loadClassification(factory, req), factory, cfg, imageService);
  }

  private static Dataset loadDataset(SqlSessionFactory factory, int datasetKey){
    try (SqlSession session = factory.openSession(false)) {
      Dataset dataset = session.getMapper(DatasetMapper.class).get(datasetKey);
      if (dataset == null || dataset.getDeleted() != null) {
        throw new NotFoundException("Dataset "+datasetKey+" does not exist");
      }
      if (!session.getMapper(DatasetPartitionMapper.class).exists(datasetKey, dataset.getOrigin())) {
        throw new IllegalArgumentException("Dataset "+datasetKey+" does not have any data");
      }
      return dataset;
    }
  }

  private static List<SimpleName> loadClassification(SqlSessionFactory factory, ExportRequest req){
    try (SqlSession session = factory.openSession(true)) {
      if (req.getTaxonID() != null) {
        final var key = DSID.of(req.getDatasetKey(), req.getTaxonID());
        SimpleName root = session.getMapper(NameUsageMapper.class).getSimple(key);
        if (root == null) {
          throw new NotFoundException("Root taxon " + req.getTaxonID() + " does not exist in dataset " + req.getDatasetKey());
        }
        req.setRoot(root);
        return session.getMapper(TaxonMapper.class).classificationSimple(key);
      }
    }
    return null;
  }

  protected void updateExport(JobStatus status){
    try (SqlSession session = factory.openSession(true)) {
      export.setStatus(status);
      session.getMapper(DatasetExportMapper.class).update(export);
    }
  }

  void setEmailer(EmailNotification emailer) {
    this.emailer = emailer;
  }

  public DatasetExport getExport() {
    return export;
  }

  public File getArchive() {
    return archive;
  }

  @Override
  public final void runWithLock() throws Exception {
    FileUtils.forceMkdir(tmpDir);
    try {
      export.setStarted(LocalDateTime.now());
      updateExport(JobStatus.RUNNING);
      // actual export work
      export();
      bundle();
      LOG.info("Export {} of dataset {} completed", getKey(), datasetKey);
    } finally {
      LOG.info("Remove temporary export directory {}", tmpDir.getAbsolutePath());
      try {
        FileUtils.deleteDirectory(tmpDir);
      } catch (IOException e) {
        LOG.warn("Failed to delete temporary export directory {}", tmpDir.getAbsolutePath(), e);
      }
    }
  }

  /**
   * Tracks the successfully executed request in the database.
   */
  @Override
  protected void onFinish() throws Exception {
    // first update the export instance
    if (getError() != null) {
      String msg = Exceptions.getFirstMessage(getError());
      export.setError(msg);
    }
    export.setFinished(getFinished());
    export.setSynonymCount(counter.getSynCounter().get());
    export.setTaxonCount(counter.getTaxCounter().get());
    export.setTaxaByRankCount(counter.getRankCounterMap());
    try {
      export.setSize(Files.size(archive.toPath()));
      export.setMd5(ChecksumUtils.getMD5Checksum(archive));
    } catch (IOException e) {
      LOG.error("Failed to read generated archive file stats for {}", archive, e);
    }
    updateExport(getStatus());

    // email notification
    if (emailer != null) {
      emailer.email(this);
    }
  }

  protected void bundle() throws IOException {
    LOG.info("Bundling archive at {}", archive.getAbsolutePath());
    FileUtils.forceMkdir(archive.getParentFile());
    CompressionUtil.zipDir(tmpDir, archive, true);
  }

  protected abstract void export() throws Exception;

}
