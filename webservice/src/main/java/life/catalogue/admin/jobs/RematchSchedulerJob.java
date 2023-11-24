package life.catalogue.admin.jobs;

import com.google.common.eventbus.EventBus;

import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.db.mapper.NameMatchMapper;

import life.catalogue.matching.NameIndex;
import life.catalogue.matching.RematchJob;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

public class RematchSchedulerJob extends DatasetSchedulerJob {
  private final EventBus bus;
  private final NameIndex ni;
  private NameMatchMapper nmm;

  public RematchSchedulerJob(int userKey, double threshold, SqlSessionFactory factory, NameIndex ni, JobExecutor exec, EventBus bus) {
    super(userKey, threshold, factory, exec);
    this.ni = ni;
    this.bus = bus;
  }

  @Override
  public int countDone(int datasetKey) {
    return nmm.countByDataset(datasetKey);
  }

  @Override
  protected void init(SqlSession session) {
    this.nmm = session.getMapper(NameMatchMapper.class);
  }

  @Override
  public BackgroundJob buildJob(int datasetKey) {
    return RematchJob.one(getUserKey(), factory, ni, bus, datasetKey);
  }
}