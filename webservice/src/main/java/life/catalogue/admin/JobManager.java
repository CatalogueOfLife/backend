package life.catalogue.admin;

import life.catalogue.WsServerConfig;
import life.catalogue.common.concurrent.PBQThreadPoolExecutor2;

public class JobManager {
  private final PBQThreadPoolExecutor2 exec;

  public JobManager(WsServerConfig cfg) {
    this.exec = new PBQThreadPoolExecutor2(cfg.backgroundJobs, 1000);
  }


}
