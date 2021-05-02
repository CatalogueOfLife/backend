package life.catalogue.common.concurrent;

import life.catalogue.api.exception.UnavailableException;
import life.catalogue.common.util.LoggingUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Marks a Runnable that required a dataset lock to run.
 */
public abstract class DatasetBlockingJob extends BackgroundJob {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetBlockingJob.class);

  protected final int datasetKey;
  private Consumer<DatasetBlockingJob> blockedHandler;
  private int attempt = 0;

  public DatasetBlockingJob(int datasetKey, int userKey, @Nullable JobPriority priority) {
    super(priority, userKey);
    this.datasetKey = datasetKey;
  }

  /**
   * Sets a consumer to handle a job that is blocked by another DatasetBlockingJob.
   * By default an UnavailableException is thrown.
   */
  public void setBlockedHandler(Consumer<DatasetBlockingJob> blockedHandler) {
    this.blockedHandler = blockedHandler;
  }

  public int getDatasetKey() {
    return datasetKey;
  }

  public int getAttempt() {
    return attempt;
  }

  protected abstract void runWithLock() throws Exception;

  @Override
  public final void execute() throws Exception {
    // we track attempts to run this job - it can be blocked
    attempt++;
    // try to acquire a lock, otherwise fail
    if (DatasetLock.lock(datasetKey, getKey())) {
      try {
        LoggingUtils.setDatasetMDC(datasetKey, getClass());
        runWithLock();
      } finally {
        DatasetLock.unlock(datasetKey);
        LoggingUtils.removeDatasetMDC();
      }
    } else {
      Optional<UUID> process = DatasetLock.isLocked(datasetKey);
      process.ifPresent(proc -> {
        LOG.warn("Failed to acquire lock for dataset {} from {} job {}. Job {} currently running", datasetKey, this.getClass().getSimpleName(), getKey(), process);
      });
      if (blockedHandler != null) {
        blockedHandler.accept(this);
      } else {
        throw new UnavailableException(String.format("Failed to acquire lock for dataset %s from %s: %s", datasetKey, this.getClass().getSimpleName(), getKey()));
      }
    }
  }

  @Override
  public boolean isDuplicate(BackgroundJob other) {
    // rescheduling of blocked jobs will not work if we just test for the same job key
    if (attempt >= 1) {
      return false;
    }
    return super.isDuplicate(other);
  }
}
