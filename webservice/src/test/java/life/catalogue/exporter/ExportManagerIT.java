package life.catalogue.exporter;

import life.catalogue.WsServerConfig;
import life.catalogue.common.concurrent.DatasetBlockingJob;
import life.catalogue.common.concurrent.JobExecutor;
import life.catalogue.common.concurrent.JobPriority;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.img.ImageService;
import org.jetbrains.annotations.Nullable;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class ExportManagerIT {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.apple();

  @Test
  public void rescheduleBlockedDatasets() throws Exception {
    WsServerConfig cfg = new WsServerConfig();
    cfg.downloadURI = URI.create("http://gbif.org");
    cfg.exportDir = new File("/tmp/col");
    cfg.backgroundJobs = 3;
    JobExecutor executor = new JobExecutor(cfg.backgroundJobs);
    ExportManager manager = new ExportManager(cfg, PgSetupRule.getSqlSessionFactory(), executor, ImageService.passThru(), null);

    PrintBlockJob job = new PrintBlockJob(TestDataRule.APPLE.key);
    PrintBlockJob job2 = new PrintBlockJob(TestDataRule.APPLE.key);
    manager.submit(job);
    TimeUnit.MILLISECONDS.sleep(5);
    manager.submit(job2);
    TimeUnit.MILLISECONDS.sleep(5);
    assertFalse(job2.didRun);

    TimeUnit.SECONDS.sleep(10);
    assertTrue(job.didRun);
    assertTrue(job2.didRun);
  }

  static class PrintBlockJob extends DatasetBlockingJob {
    boolean didRun = false;

    public PrintBlockJob(int datasetKey) {
      super(datasetKey, TestDataRule.TEST_USER.getKey(), JobPriority.LOW);
    }

    @Override
    protected void runWithLock() throws Exception {
      System.out.println("RUN "+datasetKey);
      TimeUnit.SECONDS.sleep(1);
      didRun = true;
    }
  }
}