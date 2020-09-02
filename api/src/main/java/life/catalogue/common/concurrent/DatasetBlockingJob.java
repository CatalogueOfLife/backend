package life.catalogue.common.concurrent;

import life.catalogue.api.exception.UnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Marks a Runnable that required a dataset lock to run.
 */
public abstract class DatasetBlockingJob extends BackgroundJob {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetBlockingJob.class);

  private final DatasetLock.ProcessType type;

  public DatasetBlockingJob(DatasetLock.ProcessType type, int userKey) {
    super(userKey);
    this.type = type == null ? DatasetLock.ProcessType.OTHER : type;
  }

  abstract int blockedDataset();

  abstract void runWithLock() throws Exception;

  @Override
  public final void execute() throws Exception {
    // try to acquire a lock, otherwise fail
    final int datasetKey = blockedDataset();
    if (DatasetLock.lock(datasetKey, type)) {
      try {
        runWithLock();
      } finally {
        DatasetLock.unlock(datasetKey);
      }
    } else {
      Optional<DatasetLock.ProcessType> process = DatasetLock.isLocked(datasetKey);
      process.ifPresent(proc -> {
        LOG.warn("Failed to acquire lock for dataset {} from job {}: {}. {} currently running", datasetKey, this.getClass().getSimpleName(), getKey(), process);
      });
      throw new UnavailableException(String.format("Failed to acquire lock for dataset %s from %s: %s", datasetKey, this.getClass().getSimpleName(), getKey()));
    }
  }
}
