package life.catalogue.concurrent;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.Dataset;
import life.catalogue.common.util.LoggingUtils;
import life.catalogue.db.mapper.DatasetMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Marks a Runnable that requires a single dataset to run against.
 */
public abstract class DatasetJob extends BackgroundJob {

  protected final int datasetKey;
  protected Dataset dataset;

  public DatasetJob(int datasetKey, int userKey, @Nullable JobPriority priority) {
    super(priority, userKey);
    this.datasetKey = datasetKey;
  }

  public int getDatasetKey() {
    return datasetKey;
  }

  public Dataset getDataset() {
    return dataset;
  }

  /**
   * Loads the dataset and throws an IAE if it does not exist, is deleted or does not contain and data yet.
   */
  protected static Dataset loadDataset(SqlSessionFactory factory, int datasetKey){
    try (SqlSession session = factory.openSession(false)) {
      Dataset dataset = session.getMapper(DatasetMapper.class).get(datasetKey);
      if (dataset == null || dataset.getDeleted() != null) {
        throw new NotFoundException("Dataset " + datasetKey + " does not exist");
      }
      return dataset;
    }
  }

  @Override
  public void execute() throws Exception {
    LoggingUtils.setDatasetMDC(datasetKey, getClass());
  }

  @Override
  protected void onLogAppenderClose() {
    super.onLogAppenderClose();
    LoggingUtils.removeDatasetMDC();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + " #" + datasetKey + " " + getKey() + ": " + getStatus();
  }
}
