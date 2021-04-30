package life.catalogue.exporter;

import life.catalogue.WsServerConfig;
import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.model.DSID;
import life.catalogue.common.concurrent.BackgroundJob;
import life.catalogue.common.concurrent.DatasetBlockingJob;
import life.catalogue.common.concurrent.JobExecutor;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.img.ImageService;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ExportManager {
  private final WsServerConfig cfg;
  private final SqlSessionFactory factory;
  private final ImageService imageService;
  private final JobExecutor executor;
  private final Consumer<BackgroundJob> emailHandler;

  public ExportManager(WsServerConfig cfg, SqlSessionFactory factory, JobExecutor executor, ImageService imageService, Consumer<BackgroundJob> emailHandler) {
    this.cfg = cfg;
    this.factory = factory;
    this.executor = executor;
    this.imageService = imageService;
    this.emailHandler = emailHandler;
  }

  public File archiveFiLe(UUID key) {
    return ArchiveExporter.archive(cfg.exportDir, key);
  }

  public URI archiveURI(UUID key) {
    String path = archiveFiLe(key).getAbsolutePath().substring(cfg.exportDir.getAbsolutePath().length());
    return cfg.downloadURI.resolve("/exports" + path);
  }

  public UUID submit(ExportRequest req) throws IllegalArgumentException {
    validate(req);
    DatasetBlockingJob job;
    switch (req.getFormat()) {
      case COLDP:
        job = new ColdpExporter(req, factory, cfg, imageService);
        break;
      case DWCA:
        job = new DwcaExporter(req, factory, cfg, imageService);
        break;
      case ACEF:
        job = new AcefExporterJob(req, factory, cfg, imageService);
        break;
      case TEXT_TREE:
        job = new TextTreeExporter(req, factory, cfg, imageService);
        break;

      default:
        throw new IllegalArgumentException("Export format "+req.getFormat() + " is not supported yet");
    }
    job.setBlockedHandler(this::waitAndReschedule);
    job.addHandler(emailHandler);
    executor.submit(job);
    return job.getKey();
  }

  void waitAndReschedule(DatasetBlockingJob job){
    // first try to just reschedule the job, appending to the job queue
    // If attempted several times before already we will wait a little, blocking the background queue for a minute as this runs in the executor
    if (job.getAttempt() > 2) {
      // if we tried many times already fail
      if (job.getAttempt() > 100) {
        throw new UnavailableException(String.format("Failed to schedule the job %s for dataset %s", this.getClass().getSimpleName(), job.getDatasetKey()));
      }
      try {
        TimeUnit.MINUTES.sleep(1);
      } catch (InterruptedException e) {
      }
    }
    executor.submit(job);
  }

  /**
   * Makes sure taxonID exists if given
   */
  void validate(ExportRequest req) throws IllegalArgumentException {
    if (req.getTaxonID() != null) {
      try (SqlSession session = factory.openSession()) {
        if (session.getMapper(NameUsageMapper.class).getSimple(DSID.of(req.getDatasetKey(), req.getTaxonID())) == null) {
          throw new IllegalArgumentException("Root taxonID " + req.getTaxonID() + " does not exist in dataset " + req.getDatasetKey());
        }
      }
    }
  }
}
