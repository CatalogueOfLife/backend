package life.catalogue.admin.jobs;

import life.catalogue.api.model.User;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.common.concurrent.BackgroundJob;
import life.catalogue.common.concurrent.JobPriority;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetPartitionMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Updates the usage counter for all managed datasets.
 */
public class UsageCountJob extends BackgroundJob {
  private static final Logger LOG = LoggerFactory.getLogger(UsageCountJob.class);
  private final SqlSessionFactory factory;

  public UsageCountJob(User user, JobPriority priority, SqlSessionFactory factory) {
    super(priority, user.getKey());
    this.factory = factory;
  }

  @Override
  public void execute() {
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      DatasetPartitionMapper dpm = session.getMapper(DatasetPartitionMapper.class);
      for (int key : dm.keys(DatasetOrigin.MANAGED)) {
        int cnt = dpm.updateUsageCounter(key);
        LOG.info("Updated usage counter for managed dataset {} to {}", key, cnt);
      }
    }
  }
}
