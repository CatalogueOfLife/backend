package life.catalogue.exporter;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.DatasetExport;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.common.io.CompressionUtil;
import life.catalogue.common.lang.Exceptions;
import life.catalogue.concurrent.*;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.db.mapper.DatasetExportMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.TaxonMapper;
import life.catalogue.img.ImageService;
import life.catalogue.img.ImgConfig;
import life.catalogue.metadata.coldp.DatasetYamlWriter;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

/**
 * Base class for all dataset exporter that blocks parallel exports for the same dataset
 * and tracks exports by users in the database.
 */
public abstract class DatasetExportJob extends DatasetBlockingJob {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetExportJob.class);
  private static final String METADATA_FILENAME = "metadata.yaml";
  protected final SqlSessionFactory factory;
  protected final ExportRequest req;
  protected File archive;
  protected File tmpDir;
  protected final JobConfig jCfg;
  protected final NormalizerConfig nCfg;
  protected final ImgConfig iCfg;
  protected final ImageService imageService;
  protected final UsageCounter counter = new UsageCounter();
  private final DatasetExport export;

  @VisibleForTesting
  DatasetExportJob(ExportRequest req, int userKey, DataFormat requiredFormat, List<SimpleName> classification, SqlSessionFactory factory,
                   ExporterConfig cfg, ImageService imageService) {
    super(req.getDatasetKey(), userKey, JobPriority.LOW);
    Preconditions.checkNotNull(requiredFormat, "format required");
    if (req.getFormat() == null) {
      req.setFormat(requiredFormat);
    } else if (req.getFormat() != requiredFormat) {
      throw new IllegalArgumentException("Format "+req.getFormat()+" cannot be exported with "+getClass().getSimpleName());
    }
    this.jCfg = cfg.getJob();
    this.nCfg = cfg.getNormalizerConfig();
    this.iCfg = cfg.getImgConfig();
    this.imageService = imageService;
    this.req = Preconditions.checkNotNull(req);
    this.factory = factory;
    this.archive = this.jCfg.downloadFile(getKey());
    this.tmpDir = new File(nCfg.scratchDir, "export/" + getKey().toString());
    this.dataset = loadDataset(factory, req.getDatasetKey());
    export = DatasetExport.createWaiting(getKey(), userKey, req, dataset);
    export.setClassification(classification);
    // create waiting export in db
    createExport(export);
    LOG.info("Created {} job {} by user {} for dataset {} to {}", getClass().getSimpleName(), getUserKey(), getKey(), datasetKey, archive);
  }

  DatasetExportJob(ExportRequest req, int userKey, DataFormat requiredFormat, boolean allowExcel, SqlSessionFactory factory,
                   ExporterConfig cfg, ImageService imageService) {
    this(req, userKey, requiredFormat, loadClassification(factory, req), factory, cfg, imageService);
    if (req.isExcel() && !allowExcel) {
      throw new IllegalArgumentException(requiredFormat.getName() + " cannot be exported in Excel");
    }
  }

  private static List<SimpleName> loadClassification(SqlSessionFactory factory, ExportRequest req){
    if (req.getTaxonID() != null) {
      try (SqlSession session = factory.openSession(true)) {
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

  protected void createExport(DatasetExport export){
    try (SqlSession session = factory.openSession(true)) {
      session.getMapper(DatasetExportMapper.class).create(export);
    }
  }
  protected void updateExport(JobStatus status){
    try (SqlSession session = factory.openSession(true)) {
      export.setStatus(status);
      session.getMapper(DatasetExportMapper.class).update(export);
    }
  }

  @Override
  public String getEmailTemplatePrefix() {
    return "export";
  }

  public ExportRequest getReq() {
    return req;
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
      exportMetadata();
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
  protected void onFinishLocked() throws Exception {
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
      export.calculateSizeAndMd5();
    } catch (IOException e) {
      LOG.error("Failed to read generated archive file stats for {}", archive, e);
    }
    updateExport(getStatus());
  }

  protected void bundle() throws IOException {
    LOG.info("Bundling archive at {}", archive.getAbsolutePath());
    FileUtils.forceMkdir(archive.getParentFile());
    CompressionUtil.zipDir(tmpDir, archive, true);
  }

  protected void exportMetadata() throws IOException {
    LOG.info("Adding metadata.yaml");
    DatasetYamlWriter.write(dataset, new File(tmpDir, METADATA_FILENAME));
  }

  protected abstract void export() throws Exception;

  @Override
  public Class<? extends BackgroundJob> maxPerUserClass() {
    return DatasetExportJob.class;
  }

  @Override
  public boolean isDuplicate(BackgroundJob other) {
    if (other instanceof DatasetExportJob) {
      DatasetExportJob job = (DatasetExportJob) other;
      return job.getReq().equals(this.req) && job.getUserKey() == this.getUserKey();
    }
    return false;
  }
}
