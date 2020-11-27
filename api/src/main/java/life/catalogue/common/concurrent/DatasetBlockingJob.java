package life.catalogue.common.concurrent;

import life.catalogue.api.exception.UnavailableException;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

/**
 * Marks a Runnable that required a dataset lock to run.
 */
public abstract class DatasetBlockingJob extends BackgroundJob {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetBlockingJob.class);

  protected final int datasetKey;

  public DatasetBlockingJob(int datasetKey, int userKey) {
    this(datasetKey, userKey, JobPriority.MEDIUM);
  }

  public DatasetBlockingJob(int datasetKey, int userKey, @Nullable JobPriority priority) {
    super(priority, userKey);
    this.datasetKey = datasetKey;
  }

  protected abstract void runWithLock() throws Exception;

  @Override
  public final void execute() throws Exception {
    // try to acquire a lock, otherwise fail
    if (DatasetLock.lock(datasetKey, getKey())) {
      try {
        runWithLock();
      } finally {
        DatasetLock.unlock(datasetKey);
      }
    } else {
      Optional<UUID> process = DatasetLock.isLocked(datasetKey);
      process.ifPresent(proc -> {
        LOG.warn("Failed to acquire lock for dataset {} from {} job {}. Job {} currently running", datasetKey, this.getClass().getSimpleName(), getKey(), process);
      });
      throw new UnavailableException(String.format("Failed to acquire lock for dataset %s from %s: %s", datasetKey, this.getClass().getSimpleName(), getKey()));
    }
  }
}
