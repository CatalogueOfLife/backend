package life.catalogue.concurrent;

import life.catalogue.common.util.LoggingUtils;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Marks a Runnable that required a dataset lock to run.
 */
public abstract class DatasetBlockingJob extends BackgroundJob {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetBlockingJob.class);

  protected final int datasetKey;
  private int attempt = 0;

  public DatasetBlockingJob(int datasetKey, int userKey, @Nullable JobPriority priority) {
    super(priority, userKey);
    this.datasetKey = datasetKey;
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
    // did we try several times already so it seems there is a longer running job blocking and the executor is rather idle
    if (attempt>25) {
      TimeUnit.SECONDS.sleep(60);
    } else if (attempt>10) {
      TimeUnit.SECONDS.sleep(10);
    } else if (attempt>5) {
      TimeUnit.SECONDS.sleep(1);
    } else {
      TimeUnit.MILLISECONDS.sleep(100);
    }

    // try to acquire a lock, otherwise fail
    UUID proc = DatasetLock.lock(datasetKey, getKey());
    if (getKey().equals(proc)) {
      try {
        LoggingUtils.setDatasetMDC(datasetKey, getClass());
        runWithLock();
      } finally {
        DatasetLock.unlock(datasetKey);
        LoggingUtils.removeDatasetMDC();
      }
    } else {
      LOG.info("Failed to acquire lock for dataset {} from {} job {} #{}. Blocked by currently running job {}", datasetKey, this.getClass().getSimpleName(), getKey(), attempt, proc);
      throw new DatasetBlockedException(proc, datasetKey);
    }
  }

}
