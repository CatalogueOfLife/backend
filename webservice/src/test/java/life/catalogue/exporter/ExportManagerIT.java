package life.catalogue.exporter;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.concurrent.DatasetBlockingJob;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.concurrent.JobPriority;
import life.catalogue.dao.DatasetExportDao;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.UserDao;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.img.ImageService;
import life.catalogue.release.ProjectRelease;

import java.io.File;
import java.net.URI;
import java.util.concurrent.TimeUnit;

import org.junit.*;

import com.codahale.metrics.MetricRegistry;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class ExportManagerIT {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.apple();

  WsServerConfig cfg = new WsServerConfig();
  JobExecutor executor;
  DatasetExportDao exDao;
  User user = new User();

  @Before
  public void init() throws Exception {
    user.setKey(1);
    user.setUsername("foo");
    user.setLastname("Bar");
    UserDao uDao = mock(UserDao.class);
    doReturn(user).when(uDao).get(any());
    exDao = mock(DatasetExportDao.class);
    executor = new JobExecutor(cfg.job, new MetricRegistry(), null, uDao);
  }

  @After
  public void stop() throws Exception {
    executor.stop();
  }

  @Test
  public void rescheduleBlockedDatasets() throws Exception {
    cfg.job.downloadURI = URI.create("http://gbif.org/");
    cfg.job.downloadDir = new File("/tmp/col");
    cfg.job.threads = 3;
    ExportManager manager = new ExportManager(cfg, SqlSessionFactoryRule.getSqlSessionFactory(), executor, ImageService.passThru(), exDao, mock(DatasetImportDao.class));

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

  @Test
  public void releaseExports() throws Exception {
    final int datasetKey = TestDataRule.APPLE.key;
    final int userKey = TestDataRule.TEST_USER.getKey();

    cfg.job.downloadURI = URI.create("http://gbif.org/");
    cfg.job.downloadDir = new File("/tmp/col");
    cfg.job.threads = 3;

    ExportManager manager = new ExportManager(cfg, SqlSessionFactoryRule.getSqlSessionFactory(), executor, ImageService.passThru(), exDao, mock(DatasetImportDao.class));

    // first schedule a block job that runs forever
    for (DataFormat df : ProjectRelease.EXPORT_FORMATS) {
      ExportRequest req = new ExportRequest();
      req.setDatasetKey(datasetKey);
      req.setFormat(df);
      manager.submit(req, userKey);
    }
    TimeUnit.SECONDS.sleep(10);
    System.out.println("Export test finished");
  }
}