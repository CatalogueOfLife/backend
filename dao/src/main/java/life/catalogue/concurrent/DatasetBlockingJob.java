package life.catalogue.concurrent;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.Dataset;
import life.catalogue.common.util.LoggingUtils;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetPartitionMapper;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Marks a Runnable that requires a dataset lock to run.
 */
public abstract class DatasetBlockingJob extends BackgroundJob {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetBlockingJob.class);

  protected final int datasetKey;
  protected Dataset dataset;
  private int attempt = 0;

  public DatasetBlockingJob(int datasetKey, int userKey, @Nullable JobPriority priority) {
    super(priority, userKey);
    this.datasetKey = datasetKey;
  }

  public int getDatasetKey() {
    return datasetKey;
  }

  public Dataset getDataset() {
    return dataset;
  }

  public int getAttempt() {
    return attempt;
  }

  protected abstract void runWithLock() throws Exception;

  /**
   * Loads the dataset and throws an IAE if it does not exist, is deleted or does not contain and data yet.
   */
  protected Dataset loadDataset(SqlSessionFactory factory, int datasetKey){
    try (SqlSession session = factory.openSession(false)) {
      Dataset dataset = session.getMapper(DatasetMapper.class).get(datasetKey);
      if (dataset == null || dataset.getDeleted() != null) {
        throw new NotFoundException("Dataset " + datasetKey + " does not exist");
      }
      return dataset;
    }
  }

  @Override
  public final void execute() throws Exception {
    // we track attempts to run this job - it can be blocked
    attempt++;
    // did we try several times already so it seems there is a longer running job blocking and the executor is rather idle
    if (attempt>25) {
      TimeUnit.MINUTES.sleep(5);
    } else if (attempt>10) {
      TimeUnit.MINUTES.sleep(1);
    } else if (attempt>5) {
      TimeUnit.SECONDS.sleep(10);
    } else if (attempt>2) {
      TimeUnit.SECONDS.sleep(1);
    } else {
      TimeUnit.MILLISECONDS.sleep(100);
    }

    // try to acquire a lock, otherwise fail
    UUID proc = DatasetLock.lock(datasetKey, getKey());
    if (getKey().equals(proc)) {
      LoggingUtils.setDatasetMDC(datasetKey, getClass());
      runWithLock();
    } else {
      LOG.info("Failed to acquire lock for dataset {} from {} job {} #{}. Blocked by currently running job {}", datasetKey, this.getClass().getSimpleName(), getKey(), attempt, proc);
      throw new DatasetBlockedException(proc, datasetKey);
    }
  }

  @Override
  protected final void onFinish() throws Exception {
    try {
      onFinishLocked();
    } finally {
      DatasetLock.unlock(datasetKey);
      LoggingUtils.removeDatasetMDC();
    }
  }

  /**
   * Override the onFinish method which is made final to make sure to unlock the dataset.
   * @throws Exception
   */
  protected void onFinishLocked() throws Exception {
  }

}
