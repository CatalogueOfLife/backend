package life.catalogue.jobs.cron;

import life.catalogue.concurrent.JobConfig;
import life.catalogue.db.mapper.JobMapper;

import java.util.concurrent.TimeUnit;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Periodically trims the job history, which otherwise only ever grows - the weekly fleet sync alone adds
 * one record per sector.
 * <p>
 * Only records that no import, sync or export metrics refer to are ever removed: a job row carries the
 * status, step and error of whatever metrics point at it, and dataset_export even inner joins it, so
 * deleting one out from under a metrics record would corrupt that record rather than tidy anything up.
 * The heavy job classes are exactly the ones with metrics, so in practice this reaps the ephemeral jobs -
 * matching, indexing, logo updates, metrics rebuilds and the like. Trimming the import and sync history
 * itself is the job of SectorImportRetentionJob, which removes metrics, names files and job row together.
 */
public class JobCleanup extends CronJob {
  private static final Logger LOG = LoggerFactory.getLogger(JobCleanup.class);

  private final SqlSessionFactory factory;
  private final JobConfig cfg;

  public JobCleanup(SqlSessionFactory factory, JobConfig cfg) {
    // monthly - the table grows slowly enough that a daily pass would find nothing to do most days
    super(30, TimeUnit.DAYS);
    this.factory = factory;
    this.cfg = cfg;
  }

  @Override
  public void run() {
    int total = 0;
    try {
      int deleted;
      do {
        // one short autocommit transaction per batch, so a large first run never holds a long one open
        try (SqlSession session = factory.openSession(true)) {
          deleted = session.getMapper(JobMapper.class).deleteOld(
            cfg.retentionDays, cfg.retentionDaysByClass, cfg.retentionKeepPerClass, cfg.retentionBatchSize);
        }
        total += deleted;
        // deleteOld applies its limit after every filter, so a full batch means there is more to come
      } while (deleted >= cfg.retentionBatchSize);

      if (total > 0) {
        LOG.info("Removed {} job records older than {} days, keeping the newest {} per job class",
          total, cfg.retentionDays, cfg.retentionKeepPerClass);
      } else {
        LOG.debug("No job records to remove");
      }

    } catch (RuntimeException e) {
      // a cron thread that dies takes the schedule with it
      LOG.error("Failed to clean up the job history after removing {} records", total, e);
    }
  }
}
