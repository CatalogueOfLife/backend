package life.catalogue.jobs.cron;

import life.catalogue.dao.DatasetDao;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TempDatasetCleanup extends CronJob {

  final DatasetDao dao;

  public TempDatasetCleanup(DatasetDao dao) {
    super(1, TimeUnit.DAYS);
    this.dao = dao;
  }

  @Override
  public void run() {
    dao.deleteTempDatasets();
  }
}
