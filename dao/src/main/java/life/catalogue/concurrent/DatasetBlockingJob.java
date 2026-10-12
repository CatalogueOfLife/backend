package life.catalogue.concurrent;

import life.catalogue.api.vocab.JobPriority;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.Dataset;
import life.catalogue.common.util.LoggingUtils;
import life.catalogue.db.mapper.DatasetMapper;

import java.util.UUID;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A job that requires a lock on its dataset to run, EXCLUSIVE unless lockMode() says otherwise.
 * A job that cannot get its lock fails fast with a DatasetBlockedException. The JobExecutor then parks it
 * without occupying a worker and runs it again once the dataset got unlocked.
 */
public abstract class DatasetBlockingJob extends DatasetJob {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetBlockingJob.class);

  private boolean skipLock;

  public DatasetBlockingJob(int datasetKey, int userKey, @Nullable JobPriority priority) {
    super(datasetKey, userKey, priority);
  }

  /**
   * Runs the job without a lock, e.g. when it runs embedded in another job that holds the lock already.
   */
  public void skipLock() {
    this.skipLock = true;
  }

  /**
   * @return the lock the job needs on its dataset. EXCLUSIVE by default, jobs that only read their dataset can share it.
   */
  public DatasetLock.Mode lockMode() {
    return DatasetLock.Mode.EXCLUSIVE;
  }

  protected abstract void runWithLock() throws Exception;

  @Override
  public final void execute() throws Exception {
    checkIfCancelled();
    if (!skipLock) {
      UUID holder = DatasetLock.tryLock(datasetKey, getKey(), lockMode());
      if (holder != null) {
        LOG.info("Failed to acquire {} lock for dataset {} from {} job {}. Blocked by job {}", lockMode(), datasetKey, getClass().getSimpleName(), getKey(), holder);
        throw new DatasetBlockedException(holder, datasetKey);
      }
    }
    LoggingUtils.setDatasetMDC(datasetKey, getClass());
    runWithLock();
  }

  @Override
  protected final void onFinish() throws Exception {
    try {
      onFinishLocked();
    } finally {
      if (!skipLock) {
        // only ever releases a lock this job holds, not the one of a job it was blocked by
        DatasetLock.unlock(datasetKey, getKey());
      }
    }
  }

  /**
   * Override the onFinish method which is made final to make sure to unlock the dataset.
   * @throws Exception
   */
  protected void onFinishLocked() throws Exception {
  }
}
