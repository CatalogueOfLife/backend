package life.catalogue.admin.jobs;

import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.concurrent.GlobalBlockingJob;
import life.catalogue.concurrent.JobPriority;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetPartitionMapper;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Updates the usage counter for all managed datasets.
 */
public class UsageCountJob extends GlobalBlockingJob {
  private static final Logger LOG = LoggerFactory.getLogger(UsageCountJob.class);
  private final SqlSessionFactory factory;

  public UsageCountJob(int userKey, JobPriority priority, SqlSessionFactory factory) {
    super(userKey, priority);
    this.factory = factory;
  }

  @Override
  public void execute() {
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      DatasetPartitionMapper dpm = session.getMapper(DatasetPartitionMapper.class);
      int counter = 0;
      for (int key : dm.keys(DatasetOrigin.PROJECT)) {
        int cnt = dpm.updateUsageCounter(key);
        LOG.info("Updated usage counter for project {} to {}", key, cnt);
        counter++;
      }
      LOG.info("Done. Updated usage counter for all {} projects", counter);
    }
  }
}
