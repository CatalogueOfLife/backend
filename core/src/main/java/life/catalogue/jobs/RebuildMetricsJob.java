package life.catalogue.jobs;

import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.DatasetBlockingJob;
import life.catalogue.concurrent.JobPriority;
import life.catalogue.dao.DaoUtils;
import life.catalogue.dao.TaxonMetricsBuilder;

import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Recreates all taxon metrics for a given dataset
 */
public class RebuildMetricsJob extends DatasetBlockingJob {
  private final SqlSessionFactory factory;

  public RebuildMetricsJob(int userKey, SqlSessionFactory factory, int datasetKey) {
    super(datasetKey, userKey, JobPriority.HIGH);
    this.factory = factory;
    DaoUtils.notProject(datasetKey);
  }

  @Override
  protected void runWithLock() throws Exception {
    TaxonMetricsBuilder.rebuildMetrics(factory, datasetKey);
  }

  @Override
  public boolean isDuplicate(BackgroundJob other) {
    if (other instanceof RebuildMetricsJob) {
      RebuildMetricsJob job = (RebuildMetricsJob) other;
      return datasetKey == job.datasetKey;
    }
    return false;
  }
}
