package life.catalogue.admin.jobs.cron;

import life.catalogue.admin.jobs.UsageCountJob;
import life.catalogue.api.vocab.Users;
import life.catalogue.concurrent.JobPriority;

import java.util.concurrent.TimeUnit;

import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProjectCounterUpdate extends CronJob {
  private static final Logger LOG = LoggerFactory.getLogger(ProjectCounterUpdate.class);
  private final SqlSessionFactory factory;
  public ProjectCounterUpdate(SqlSessionFactory factory) {
    super(1, TimeUnit.DAYS);
    this.factory = factory;
  }

  @Override
  public void run() {
    var job = new UsageCountJob(Users.GBIF_SYNC, JobPriority.MEDIUM, factory);
    job.run();
  }
}
