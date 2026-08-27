package life.catalogue.jobs.cron;

import life.catalogue.concurrent.JobConfig;
import life.catalogue.db.mapper.JobMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Periodically trims the job history, which otherwise only ever grows - the weekly fleet sync alone adds
 * one record per sector and the continuous importer one per dataset it looks at.
 * <p>
 * Only records that no import, sync or export metrics refer to are ever removed: a job row carries the
 * status, step and error of whatever metrics point at it, and dataset_export even inner joins it, so
 * deleting one out from under a metrics record would corrupt that record rather than tidy anything up.
 * The heavy job classes are exactly the ones with metrics, so in practice this reaps the ephemeral jobs -
 * matching, indexing, logo updates, metrics rebuilds and the like, plus the imports that found their
 * source unchanged and deleted their own metrics row again. Trimming the import and sync history
 * itself is the job of SectorImportRetentionJob, which removes metrics, names files and job row together.
 * <p>
 * The two job logs are removed along with the record. Both are addressed by the job key alone, so once
 * the row is gone nothing can reach them again.
 */
public class JobCleanup extends CronJob {
  private static final Logger LOG = LoggerFactory.getLogger(JobCleanup.class);

  private final SqlSessionFactory factory;
  private final JobConfig cfg;

  public JobCleanup(SqlSessionFactory factory, JobConfig cfg) {
    // daily - a monthly pass used to find nothing most days, but the continuous importer adds hundreds
    // of unchanged imports a day and a short per class retention only bites as often as we sweep
    super(1, TimeUnit.DAYS);
    this.factory = factory;
    this.cfg = cfg;
  }

  @Override
  public void run() {
    int total = 0;
    int files = 0;
    int failedFiles = 0;
    try {
      List<UUID> deleted;
      do {
        // one short autocommit transaction per batch, so a large first run never holds a long one open
        try (SqlSession session = factory.openSession(true)) {
          deleted = session.getMapper(JobMapper.class).deleteOld(
            cfg.retentionDays, cfg.retentionDaysByClass, cfg.retentionKeepPerClass, cfg.retentionBatchSize);
        }
        total += deleted.size();
        for (UUID key : deleted) {
          for (File f : List.of(cfg.jobLog(key), cfg.downloadLogFile(key))) {
            // one bad file must not abort a sweep that has already deleted rows - count it and move on
            try {
              if (Files.deleteIfExists(f.toPath())) {
                files++;
              }
            } catch (IOException | RuntimeException e) {
              failedFiles++;
              LOG.warn("Failed to delete job log {}", f, e);
            }
          }
        }
        // deleteOld applies its limit after every filter, so a full batch means there is more to come
      } while (deleted.size() >= cfg.retentionBatchSize);

      if (total > 0) {
        LOG.info("Removed {} job records older than {} days with {} logs, keeping the newest {} per job class",
          total, cfg.retentionDays, files, cfg.retentionKeepPerClass);
        if (failedFiles > 0) {
          LOG.warn("Failed to delete {} job logs", failedFiles);
        }
      } else {
        LOG.debug("No job records to remove");
      }

    } catch (RuntimeException e) {
      // a cron thread that dies takes the schedule with it
      LOG.error("Failed to clean up the job history after removing {} records", total, e);
    }
  }
}
