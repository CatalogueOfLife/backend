package life.catalogue.exporter;

import life.catalogue.es.search.NameUsageSearchService;

import life.catalogue.TestConfigs;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.concurrent.DatasetBlockingJob;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.api.vocab.JobPriority;
import life.catalogue.dao.DatasetExportDao;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.UserDao;
import life.catalogue.img.ImageService;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;
import life.catalogue.release.ProjectRelease;

import java.io.File;
import java.net.URI;
import java.util.concurrent.TimeUnit;

import life.catalogue.release.PublishReleaseListener;

import org.junit.*;

import com.codahale.metrics.MetricRegistry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class ExportManagerIT {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.apple();

  TestConfigs cfg = TestConfigs.build();
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
    executor = new JobExecutor(cfg.job, new MetricRegistry(), null, uDao, null);
    executor.start();
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
    ExportManager manager = new ExportManager(cfg, SqlSessionFactoryRule.getSqlSessionFactory(), executor, ImageService.passThru(), exDao, mock(DatasetImportDao.class), NameUsageSearchService.passThru(), java.net.URI.create("https://www.checklistbank.org"));

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

  private ExportManager manager() {
    cfg.job.downloadURI = URI.create("http://gbif.org/");
    cfg.job.downloadDir = new File("/tmp/col");
    cfg.job.threads = 3;
    return new ExportManager(cfg, SqlSessionFactoryRule.getSqlSessionFactory(), executor, ImageService.passThru(), exDao,
      mock(DatasetImportDao.class), NameUsageSearchService.passThru(), URI.create("https://www.checklistbank.org"));
  }

  /**
   * A request arriving over HTTP carries a bare root id. It has to be normalised before the lookup for an
   * existing export, or it never equals an identical job already sitting in the queue.
   */
  @Test
  public void normalizeRoot() throws Exception {
    ExportManager manager = manager();

    ExportRequest req = new ExportRequest(TestDataRule.APPLE.key, DataFormat.COLDP);
    req.setRoot(new SimpleName("root-1"));
    ExportRequest bare = new ExportRequest(TestDataRule.APPLE.key, DataFormat.COLDP);
    bare.setRoot(new SimpleName("root-1"));

    manager.normalize(req);
    assertEquals("root-1", req.getRoot().getId());
    assertNotNull(req.getRoot().getName());
    assertNotNull(req.getRoot().getRank());
    // the loaded root differs from the bare one it came in as - that is what broke the queue lookup
    assertNotEquals(bare, req);

    // ... but two requests of the same shape normalise to equal requests
    manager.normalize(bare);
    assertEquals(req, bare);

    // extended is reset for formats without extended content (DOT), again before the lookup
    ExportRequest dot = new ExportRequest(TestDataRule.APPLE.key, DataFormat.DOT);
    dot.setExtended(true);
    manager.normalize(dot);
    assertFalse(dot.isExtended());

    ExportRequest coldp = new ExportRequest(TestDataRule.APPLE.key, DataFormat.COLDP);
    coldp.setExtended(true);
    manager.normalize(coldp);
    assertTrue(coldp.isExtended());
  }

  @Test
  public void releaseExports() throws Exception {
    final int datasetKey = TestDataRule.APPLE.key;
    final int userKey = TestDataRule.TEST_USER.getKey();

    cfg.job.downloadURI = URI.create("http://gbif.org/");
    cfg.job.downloadDir = new File("/tmp/col");
    cfg.job.threads = 3;

    ExportManager manager = new ExportManager(cfg, SqlSessionFactoryRule.getSqlSessionFactory(), executor, ImageService.passThru(), exDao, mock(DatasetImportDao.class), NameUsageSearchService.passThru(), java.net.URI.create("https://www.checklistbank.org"));

    // first schedule a block job that runs forever
    for (DataFormat df : PublishReleaseListener.EXPORT_FORMATS) {
      ExportRequest req = new ExportRequest();
      req.setDatasetKey(datasetKey);
      req.setFormat(df);
      manager.submit(req, userKey);
    }
    TimeUnit.SECONDS.sleep(10);
    System.out.println("Export test finished");
  }
}