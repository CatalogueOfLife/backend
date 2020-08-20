package life.catalogue.common.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Marks a Runnable that required a dataset lock to run.
 */
public abstract class DatasetBlockingJob extends BackgroundJob {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetBlockingJob.class);

  private final DatasetLock.ProcessType type;

  public DatasetBlockingJob(DatasetLock.ProcessType type) {
    this.type = type == null ? DatasetLock.ProcessType.OTHER : type;
  }

  abstract int blockedDataset();

  abstract void runWithLock() throws Exception;

  @Override
  public void run() {
    // try to acquire a lock, otherwise fail
    final int datasetKey = blockedDataset();
    if (DatasetLock.lock(datasetKey, type)) {
      try {
        runWithLock();
      } catch (Exception e) {
        LOG.error("Failed to run {} for dataset {}", this.getClass().getSimpleName(), datasetKey, e);
      } finally {
        DatasetLock.unlock(datasetKey);
      }
    } else {
      LOG.warn("Failed to acquire lock for dataset {} from {}", datasetKey, this.getClass().getSimpleName());
    }
  }
}
