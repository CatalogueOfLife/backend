package life.catalogue.jobs.cron;

import life.catalogue.dao.DatasetDao;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TempDatasetCleanup extends CronJob {
  private static final Logger LOG = LoggerFactory.getLogger(TempDatasetCleanup.class);
  DatasetDao dao;
  public TempDatasetCleanup() {
    super(1, TimeUnit.DAYS);
  }

  @Override
  public void run() {
    LOG.info("Looking for temporary datasets to be removed");
    int cnt = dao.deleteTempDatasets();
    if (cnt > 0) {
      LOG.info("Removed {} temporary datasets", cnt);
    }
  }
}
