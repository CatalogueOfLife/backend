package life.catalogue.admin;

import life.catalogue.WsServerConfig;
import life.catalogue.common.concurrent.JobExecutor;

public class JobManager {
  private final JobExecutor exec;

  public JobManager(WsServerConfig cfg) {
    this.exec = new JobExecutor(cfg.backgroundJobs, 1000);
  }


}
