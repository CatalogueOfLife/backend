package life.catalogue.exporter;

import com.google.common.annotations.VisibleForTesting;
import life.catalogue.WsServerConfig;
import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.DatasetExport;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.concurrent.DatasetBlockingJob;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.db.mapper.DatasetExportMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.img.ImageService;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.simplejavamail.api.mailer.Mailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ExportManager {
  private static final Logger LOG = LoggerFactory.getLogger(ExportManager.class);

  private final WsServerConfig cfg;
  private final SqlSessionFactory factory;
  private final ImageService imageService;
  private final JobExecutor executor;
  private final Optional<EmailNotification> emailer;

  public ExportManager(WsServerConfig cfg, SqlSessionFactory factory, JobExecutor executor, ImageService imageService, Mailer mailer) {
    this.cfg = cfg;
    this.factory = factory;
    this.executor = executor;
    this.imageService = imageService;
    // mailer
    this.emailer = mailer == null ? Optional.empty() : Optional.of(new EmailNotification(mailer, factory, cfg));
  }

  public UUID submit(ExportRequest req, int userKey) throws IllegalArgumentException {
    DatasetExport prev = findPreviousExport(req);
    if (prev != null) {
      LOG.info("Existing export {} found for request {}", prev.getKey(), req);
      return prev.getKey();
    }
    validate(req);
    DatasetBlockingJob job;
    switch (req.getFormat()) {
      case COLDP:
        job = new ColdpExporter(req, userKey, factory, cfg, imageService);
        break;
      case DWCA:
        job = new DwcaExporter(req, userKey, factory, cfg, imageService);
        break;
      case ACEF:
        job = new AcefExporter(req, userKey, factory, cfg, imageService);
        break;
      case TEXT_TREE:
        job = new TextTreeExporter(req, userKey, factory, cfg, imageService);
        break;

      default:
        throw new IllegalArgumentException("Export format "+req.getFormat() + " is not supported yet");
    }
    return submit(job);
  }

  @VisibleForTesting
  UUID submit(DatasetBlockingJob job) throws IllegalArgumentException {
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
        if (job.getAttempt() < 10) {
          TimeUnit.SECONDS.sleep(1);
        } else {
          TimeUnit.MINUTES.sleep(1);
        }
      } catch (InterruptedException e) {
      }
    }
    LOG.info("Reschedule job {}, attempt {}", job.getKey(), job.getAttempt());
    executor.submit(job);
  }

  private DatasetExport findPreviousExport(ExportRequest req) {
    try (SqlSession session = factory.openSession()) {
      return session.getMapper(DatasetExportMapper.class).search(req);
    }
  }

  /**
   * Makes sure taxonID exists if given
   */
  private void validate(ExportRequest req) throws IllegalArgumentException {
    if (req.getTaxonID() != null) {
      try (SqlSession session = factory.openSession()) {
        var root = session.getMapper(NameUsageMapper.class).getSimple(DSID.of(req.getDatasetKey(), req.getTaxonID()));
        if (root == null) {
          throw new IllegalArgumentException("Root taxon " + req.getTaxonID() + " does not exist in dataset " + req.getDatasetKey());
        } else if (!root.getStatus().isTaxon()) {
          throw new IllegalArgumentException("Root usage " + req.getTaxonID() + " is not an accepted taxon but " + root.getStatus());
        }
      }
    }
  }
}
