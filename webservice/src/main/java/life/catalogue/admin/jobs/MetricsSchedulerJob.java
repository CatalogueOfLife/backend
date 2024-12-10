package life.catalogue.admin.jobs;

import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.db.mapper.TaxonMetricsMapper;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

public class MetricsSchedulerJob extends DatasetSchedulerJob {
  private TaxonMetricsMapper tmm;

  public MetricsSchedulerJob(int userKey, SqlSessionFactory factory, double threshold, JobExecutor exec) {
    super(userKey, threshold, factory, exec, DatasetOrigin.EXTERNAL, DatasetOrigin.RELEASE, DatasetOrigin.XRELEASE);
  }

  @Override
  public int countDone(int datasetKey) {
    return tmm.countByDataset(datasetKey);
  }

  @Override
  protected void init(SqlSession session) {
    this.tmm = session.getMapper(TaxonMetricsMapper.class);
  }

  @Override
  public BackgroundJob buildJob(int datasetKey) {
    return new RebuildMetricsJob(getUserKey(), factory, datasetKey);
  }
}