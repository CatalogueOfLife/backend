package life.catalogue.resources.dataset;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.dw.auth.JwtCodec;
import life.catalogue.dw.jersey.filter.CacheControlResponseFilter;
import life.catalogue.junit.DatasetInfoCacheMockRule;
import life.catalogue.release.review.ReleaseReviewInfo;
import life.catalogue.release.review.ReleaseReviewJob;
import life.catalogue.release.review.ReleaseReviewStore;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import jakarta.ws.rs.container.ContainerRequestContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The review status is derived from files on disk rather than from a table, so this exercises exactly that
 * derivation: an unreviewed release reports NONE together with the release pair the UI needs, and a release
 * whose report file exists reports FINISHED with its public URI.
 *
 * No database - the two mappers involved are mocked, which keeps the test about the resource.
 */
public class DatasetReviewResourceTest {
  static final int PROJECT = 3;
  static final int RELEASE = 1010;
  static final int PREV_RELEASE = 1005;
  static final int ATTEMPT = 42;

  @Rule
  public final DatasetInfoCacheMockRule infoCache = new DatasetInfoCacheMockRule()
    .put(PROJECT, DatasetOrigin.PROJECT, null)
    .put(RELEASE, DatasetOrigin.RELEASE, PROJECT)
    .put(PREV_RELEASE, DatasetOrigin.RELEASE, PROJECT);

  Path reportRoot;
  ReleaseConfig rCfg;
  DatasetReviewResource resource;
  JobExecutor exec;
  ContainerRequestContext ctx;

  @Before
  public void setup() throws Exception {
    reportRoot = Files.createTempDirectory("col-review-test");
    rCfg = new ReleaseConfig();
    rCfg.reportDir = reportRoot.toFile();
    rCfg.reportURI = URI.create("https://download.example.org/releases/");

    Dataset release = new Dataset();
    release.setKey(RELEASE);
    release.setOrigin(DatasetOrigin.RELEASE);
    release.setSourceKey(PROJECT);
    release.setAttempt(ATTEMPT);
    Dataset project = new Dataset();
    project.setKey(PROJECT);
    project.setOrigin(DatasetOrigin.PROJECT);

    DatasetMapper dm = mock(DatasetMapper.class);
    when(dm.get(anyInt())).thenReturn(null);
    when(dm.get(RELEASE)).thenReturn(release);
    when(dm.get(PROJECT)).thenReturn(project);
    when(dm.previousRelease(RELEASE)).thenReturn(PREV_RELEASE);

    SqlSession session = mock(SqlSession.class);
    when(session.getMapper(DatasetMapper.class)).thenReturn(dm);
    SqlSessionFactory factory = mock(SqlSessionFactory.class);
    when(factory.openSession()).thenReturn(session);

    exec = mock(JobExecutor.class);
    when(exec.getQueueByJobClass(ReleaseReviewJob.class)).thenReturn(List.of());

    JwtCodec jwt = new JwtCodec("a-signing-key-long-enough-for-hmac-sha256-in-a-unit-test");
    // no ai config: the review page must still work everywhere, it is the trigger that needs one
    resource = new DatasetReviewResource(factory, rCfg, exec, jwt, null, null);
    ctx = mock(ContainerRequestContext.class);
  }

  @After
  public void cleanup() throws Exception {
    FileUtils.deleteQuietly(reportRoot.toFile());
  }

  @Test
  public void unreviewedRelease() {
    ReleaseReviewInfo info = resource.get(RELEASE, ctx);
    assertEquals(ReleaseReviewInfo.Status.NONE, info.getStatus());
    assertEquals(RELEASE, info.getReleaseKey());
    assertEquals(PROJECT, info.getProjectKey());
    assertEquals(DatasetOrigin.RELEASE, info.getOrigin());
    assertEquals((Integer) ATTEMPT, info.getAttempt());
    // the UI gets the release to compare against from here, so there is no second endpoint for it
    assertEquals((Integer) PREV_RELEASE, info.getPreviousReleaseKey());
    assertNull(info.getReportURI());
    assertNull(info.getSessionId());
  }

  /**
   * Every other GET under a release is cached for days, but the review status changes while a job runs.
   */
  @Test
  public void statusIsNeverCached() {
    resource.get(RELEASE, ctx);
    verify(ctx).setProperty(CacheControlResponseFilter.DONT_CACHE, true);
  }

  /**
   * The report file is the truth, not the sidecar: a review whose sidecar was lost, or left as RUNNING by a
   * server that was killed, must still show up as finished if its report is on disk.
   */
  @Test
  public void finishedIsDrivenByTheReportFile() throws Exception {
    var store = new ReleaseReviewStore(rCfg);
    // note the PROJECT key: reports live under the project, in the release's attempt folder
    File dir = store.dir(PROJECT, ATTEMPT);
    FileUtils.forceMkdir(dir);
    Files.write(store.report(PROJECT, ATTEMPT).toPath(), "<html>review</html>".getBytes(StandardCharsets.UTF_8));

    ReleaseReviewInfo info = resource.get(RELEASE, ctx);
    assertEquals(ReleaseReviewInfo.Status.FINISHED, info.getStatus());
    assertEquals(URI.create("https://download.example.org/releases/" + PROJECT + "/" + ATTEMPT + "/review.html"),
      info.getReportURI());
  }

  /**
   * A sidecar claiming a running job with no such job left in the queue is a review that did not survive a
   * restart. It must read as failed so the UI offers a retry instead of spinning forever.
   */
  @Test
  public void runningWithoutAJobIsFailed() throws Exception {
    var store = new ReleaseReviewStore(rCfg);
    var stale = new ReleaseReviewInfo();
    stale.setReleaseKey(RELEASE);
    stale.setProjectKey(PROJECT);
    stale.setAttempt(ATTEMPT);
    stale.setStatus(ReleaseReviewInfo.Status.RUNNING);
    store.write(PROJECT, ATTEMPT, stale);

    ReleaseReviewInfo info = resource.get(RELEASE, ctx);
    assertEquals(ReleaseReviewInfo.Status.FAILED, info.getStatus());
    assertNull(info.getReportURI());
  }

  @Test
  public void onlyReleasesCanBeReviewed() {
    assertThrows(IllegalArgumentException.class, () -> resource.get(PROJECT, ctx));
  }

  /**
   * Guards the sidecar round trip through the public Jackson mapper, which is what the job writes and the
   * resource reads back across JVM restarts.
   */
  @Test
  public void sidecarRoundtrip() throws Exception {
    var store = new ReleaseReviewStore(rCfg);
    var info = new ReleaseReviewInfo();
    info.setReleaseKey(RELEASE);
    info.setProjectKey(PROJECT);
    info.setAttempt(ATTEMPT);
    info.setOrigin(DatasetOrigin.XRELEASE);
    info.setStatus(ReleaseReviewInfo.Status.FAILED);
    info.setSessionId("sess_123");
    info.setModel("claude-opus-5");
    info.setListCost("12.34 USD");
    info.setError("boom");
    store.write(PROJECT, ATTEMPT, info);

    var read = store.read(PROJECT, ATTEMPT);
    assertEquals(ReleaseReviewInfo.Status.FAILED, read.getStatus());
    assertEquals("sess_123", read.getSessionId());
    assertEquals("claude-opus-5", read.getModel());
    assertEquals("12.34 USD", read.getListCost());
    assertEquals("boom", read.getError());
    assertEquals(DatasetOrigin.XRELEASE, read.getOrigin());
  }
}
