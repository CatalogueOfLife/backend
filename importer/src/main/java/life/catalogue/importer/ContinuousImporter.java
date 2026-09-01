package life.catalogue.importer;

import life.catalogue.api.vocab.Users;
import life.catalogue.concurrent.AbstractPollingScheduler;
import life.catalogue.config.ContinuousImportConfig;
import life.catalogue.config.ImporterConfig;
import life.catalogue.db.mapper.DatasetMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;


/**
 * A scheduler for new import jobs that runs continuously in the background
 * and submits new import jobs to the ImportManager if it is idle.
 * <p>
 * New jobs are selected by priority according to the following criteria:
 * <p>
 * - never imported datasets first
 * - the datasets configured indexing frequency
 *
 * If imports have failed previously, there will be an embargo for 1 week.
 */
public class ContinuousImporter extends AbstractPollingScheduler {
  private static final Logger LOG = LoggerFactory.getLogger(ContinuousImporter.class);
  private static final String THREAD_NAME = "continuous-importer";
  private static final int WAIT_TIME_IN_HOURS = 1;

  private final ImportManager manager;
  private final ContinuousImportConfig cfg;
  private final SqlSessionFactory factory;

  public ContinuousImporter(ImporterConfig cfg, ImportManager manager, SqlSessionFactory factory) {
    super(THREAD_NAME, cfg.continuous.polling);
    this.cfg = cfg.continuous;
    this.manager = manager;
    this.factory = factory;
    // the batch size to compare against is the number of datasets queued per poll, not ImporterConfig.batchSize
    // which is the unrelated PgImport db flush size
    final int maxQueue = manager.maxQueue();
    if (maxQueue < this.cfg.batchSize) {
      LOG.warn("Importer queue is shorter ({}) than the batch size ({}) to submit. Reduce batches to half the queue size!", maxQueue, this.cfg.batchSize);
      this.cfg.batchSize = (maxQueue / 2);
    }
    if (this.cfg.forceBefore != null) {
      LOG.info("Enforce all imports which last happened before {}", this.cfg.forceBefore);
    }
  }

  @Override
  protected void pollOnce() throws InterruptedException {
    // whether imports can run at all is the job executor's answer; if it is stopped or paused the submits
    // below simply fail and the base class backs off, which is why they must never be fatal here
    while (isRunning() && manager.queueSize() > cfg.threshold) {
      LOG.debug("Importer busy, sleep for {} minutes", cfg.polling);
      TimeUnit.MINUTES.sleep(cfg.polling);
    }
    if (!isRunning()) {
      return;
    }
    List<DatasetMapper.DatasetAttempt> datasets = fetch();
    if (datasets.isEmpty()) {
      LOG.debug("No datasets eligable to be imported. Sleep for {} hour", WAIT_TIME_IN_HOURS);
      TimeUnit.HOURS.sleep(WAIT_TIME_IN_HOURS);

    } else {
      LOG.info("Trying to schedule {} dataset imports", datasets.size());
      datasets.forEach(this::scheduleImport);
    }
  }

  private void scheduleImport(DatasetMapper.DatasetAttempt d) {
    try {
      boolean forceBefore = wasImportedBefore(d, cfg.forceBefore);
      if (forceBefore) {
        LOG.info("Schedule a forced import of dataset {} which was last imported before our forceBefore cutoff on {}: {}", d.getKey(), d.getLastImportAttempt(), d.getTitle());
      } else if (d.isFailed()) {
        LOG.info("Schedule a forced import of dataset {} which failed the last time on {}: {}", d.getKey(), d.getLastImportAttempt(), d.getTitle());
      }
      manager.submit(ImportRequest.external(d.getKey(), Users.IMPORTER, d.isFailed() || forceBefore));
    } catch (RuntimeException e) {
      // one dataset that cannot be queued - a full queue, an unavailable executor, a bad url - must not
      // cost us the rest of the batch, let alone the scheduler thread
      LOG.warn("Failed to schedule a {}dataset import {}: {}", d.isFailed()? "forced ":"", d.getKey(), d.getTitle(), e);
    }
  }

  /**
   * Find the next batch of datasets eligable for importing
   */
  private List<DatasetMapper.DatasetAttempt> fetch() {
    // check never crawled datasets first
    try (SqlSession session = factory.openSession(true)) {
      List<DatasetMapper.DatasetAttempt> datasets = session.getMapper(DatasetMapper.class).listNeverImported(cfg.batchSize);
      removeRunningImports(datasets);
      if (datasets.isEmpty()) {
        // now check for eligable datasets based on import frequency
        datasets = session.getMapper(DatasetMapper.class).listToBeImported(cfg.defaultFrequency, cfg.batchSize);
        removeRunningImports(datasets);
      }
      return datasets;
    }
  }

  private void removeRunningImports(List<DatasetMapper.DatasetAttempt> datasets) {
    datasets.removeIf(d -> manager.isRunning(d.getKey()));
  }

  @VisibleForTesting
  static boolean wasImportedBefore(DatasetMapper.DatasetAttempt d, LocalDate before) {
    return before != null && d.getLastImportAttempt() != null && before.isAfter(d.getLastImportAttempt().toLocalDate());
  }

}
