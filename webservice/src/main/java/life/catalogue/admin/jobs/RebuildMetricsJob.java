package life.catalogue.admin.jobs;

import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.DatasetBlockingJob;
import life.catalogue.concurrent.JobPriority;
import life.catalogue.dao.DaoUtils;
import life.catalogue.dao.MetricsDao;
import life.catalogue.dao.NameDao;
import life.catalogue.dao.TaxonDao;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.matching.nidx.NameIndexFactory;

import jakarta.validation.Validator;

import life.catalogue.release.MetricsBuilder;

import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Recreates all taxon metrics for a given dataset
 */
public class RebuildMetricsJob extends DatasetBlockingJob {
  private final SqlSessionFactory factory;

  public RebuildMetricsJob(int userKey, SqlSessionFactory factory, int datasetKey) {
    super(datasetKey, userKey, JobPriority.HIGH);
    this.factory = factory;
    DaoUtils.requireProjectOrRelease(datasetKey);
  }

  @Override
  protected void runWithLock() throws Exception {
    MetricsBuilder.rebuildMetrics(factory, datasetKey);
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
