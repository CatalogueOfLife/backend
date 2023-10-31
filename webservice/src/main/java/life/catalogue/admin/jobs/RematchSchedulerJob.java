package life.catalogue.admin.jobs;

import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.db.mapper.NameMatchMapper;

import life.catalogue.matching.NameIndex;
import life.catalogue.matching.RematchJob;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RematchSchedulerJob extends DatasetSchedulerJob {
  private final NameIndex ni;
  private NameMatchMapper nmm;

  public RematchSchedulerJob(int userKey, double threshold, SqlSessionFactory factory, NameIndex ni, JobExecutor exec) {
    super(userKey, threshold, factory, exec);
    this.ni = ni;
  }

  @Override
  public int countDone(int datasetKey) {
    return nmm.count(datasetKey);
  }

  @Override
  protected void init(SqlSession session) {
    this.nmm = session.getMapper(NameMatchMapper.class);
  }

  @Override
  public BackgroundJob buildJob(int datasetKey) {
    return RematchJob.one(getUserKey(), factory, ni, datasetKey);
  }
}