package life.catalogue.dao.job;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.DatasetBlockingJob;
import life.catalogue.concurrent.JobPriority;
import life.catalogue.dao.DatasetDao;

public class DeleteDatasetJob extends DatasetBlockingJob {
  private final DatasetDao dao;
  public DeleteDatasetJob(int datasetKey, int userKey, DatasetDao dao) {
    super(datasetKey, userKey, JobPriority.HIGH);
    this.dao = dao;
    this.dataset = dao.get(datasetKey);
    if (dataset == null || dataset.getDeleted() != null) {
      throw new NotFoundException("Dataset " + datasetKey + " does not exist");
    }
  }
  @Override
  protected void runWithLock() throws Exception {
    dao.delete(datasetKey, getUserKey());
  }

  @Override
  public boolean isDuplicate(BackgroundJob other) {
    if (other instanceof DeleteDatasetJob) {
      return datasetKey == ((DeleteDatasetJob) other).datasetKey;
    }
    return false;
  }
}
