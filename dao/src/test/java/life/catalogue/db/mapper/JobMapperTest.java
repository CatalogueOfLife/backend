package life.catalogue.db.mapper;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.JobInfo;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.User;
import life.catalogue.api.search.JobSearchRequest;
import life.catalogue.api.vocab.JobLane;
import life.catalogue.api.vocab.JobPriority;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.api.vocab.Users;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.Test;

import com.fasterxml.jackson.databind.node.ObjectNode;

import static org.junit.Assert.*;

public class JobMapperTest extends CRUDTestBase<UUID, JobInfo, JobMapper> {

  public JobMapperTest() {
    super(JobMapper.class);
  }

  static JobInfo create(JobStatus status) {
    JobInfo j = new JobInfo();
    j.setKey(UUID.randomUUID());
    j.setJob("TestJob");
    j.setLane(JobLane.DEFAULT);
    j.setStatus(status);
    j.setPriority(JobPriority.MEDIUM);
    j.setDatasetKey(appleKey);
    j.setAttempt(3);
    j.setCreatedBy(Users.DB_INIT);
    j.setCreated(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
    ObjectNode params = ApiModule.MAPPER.createObjectNode();
    params.put("datasetKey", appleKey);
    params.put("force", true);
    j.setParams(params);
    return j;
  }

  @Override
  JobInfo createTestEntity() {
    return create(JobStatus.WAITING);
  }

  @Override
  void updateTestObj(JobInfo j) {
    j.setStatus(JobStatus.FINISHED);
    j.setStep("indexing");
    // attempt and params are updatable, not just inserted: an import or release only creates its
    // metrics record - and a release its new dataset - once it has started
    j.setAttempt(4);
    ObjectNode params = ApiModule.MAPPER.createObjectNode();
    params.put("datasetKey", appleKey);
    params.put("newDatasetKey", 999);
    j.setParams(params);
    j.setStarted(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
    j.setFinished(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
    j.setError("things went wrong");
    j.setResultMd5("2c1b86f0c5d894a5b5e2b25e7f8c937b");
    j.setResultSize(123456789L);
  }

  @Test
  public void searchAndCount() throws Exception {
    mapper().create(create(JobStatus.WAITING));
    mapper().create(create(JobStatus.RUNNING));
    mapper().create(create(JobStatus.FINISHED));
    var failed = create(JobStatus.FAILED);
    failed.setJob("OtherJob");
    failed.setLane(JobLane.SYNC);
    failed.setSectorKey(13);
    failed.setCreatedBy(Users.TESTER);
    mapper().create(failed);
    commit();

    JobSearchRequest req = new JobSearchRequest();
    assertEquals(4, mapper().count(req, User.ADMIN_MAGIC_KEY));
    assertEquals(4, mapper().search(req, User.ADMIN_MAGIC_KEY, new Page()).size());

    req.setStatus(Set.of(JobStatus.WAITING, JobStatus.RUNNING));
    assertEquals(2, mapper().count(req, User.ADMIN_MAGIC_KEY));

    req = new JobSearchRequest();
    req.setJob(Set.of("OtherJob"));
    assertEquals(1, mapper().count(req, User.ADMIN_MAGIC_KEY));
    // job names are matched case insensitively and several can be combined
    req.setJob(Set.of("otherjob"));
    assertEquals(1, mapper().count(req, User.ADMIN_MAGIC_KEY));
    req.setJob(Set.of("otherjob", "testjob"));
    assertEquals(4, mapper().count(req, User.ADMIN_MAGIC_KEY));
    req.setJob(Set.of("NoSuchJob"));
    assertEquals(0, mapper().count(req, User.ADMIN_MAGIC_KEY));

    req = new JobSearchRequest();
    req.setLane(Set.of(JobLane.SYNC));
    assertEquals(1, mapper().count(req, User.ADMIN_MAGIC_KEY));
    req.setLane(Set.of(JobLane.DEFAULT, JobLane.SYNC));
    assertEquals(4, mapper().count(req, User.ADMIN_MAGIC_KEY));
    req.setLane(Set.of(JobLane.IMPORT));
    assertEquals(0, mapper().count(req, User.ADMIN_MAGIC_KEY));

    req = new JobSearchRequest();
    req.setSectorKey(13);
    assertEquals(1, mapper().count(req, User.ADMIN_MAGIC_KEY));
    req.setSectorKey(14);
    assertEquals(0, mapper().count(req, User.ADMIN_MAGIC_KEY));

    req = new JobSearchRequest();
    req.setCreatedAfter(LocalDateTime.now().minusDays(1));
    assertEquals(4, mapper().count(req, User.ADMIN_MAGIC_KEY));
    req.setCreatedAfter(LocalDateTime.now().plusDays(1));
    assertEquals(0, mapper().count(req, User.ADMIN_MAGIC_KEY));

    req = new JobSearchRequest();
    req.setCreatedBefore(LocalDateTime.now().plusDays(1));
    assertEquals(4, mapper().count(req, User.ADMIN_MAGIC_KEY));
    req.setCreatedBefore(LocalDateTime.now().minusDays(1));
    assertEquals(0, mapper().count(req, User.ADMIN_MAGIC_KEY));

    req = new JobSearchRequest();
    req.setCreatedBy(Users.TESTER);
    assertEquals(1, mapper().count(req, User.ADMIN_MAGIC_KEY));

    req = new JobSearchRequest();
    req.setKey(failed.getKey());
    var res = mapper().search(req, User.ADMIN_MAGIC_KEY, new Page());
    assertEquals(1, res.size());
    assertEquals(failed, res.get(0));

    req = new JobSearchRequest();
    req.setDatasetKey(appleKey);
    assertEquals(4, mapper().count(req, User.ADMIN_MAGIC_KEY));
    req.setDatasetKey(-99);
    assertEquals(0, mapper().count(req, User.ADMIN_MAGIC_KEY));

    req = new JobSearchRequest();
    req.setPriority(JobPriority.MEDIUM);
    assertEquals(4, mapper().count(req, User.ADMIN_MAGIC_KEY));
    req.setPriority(JobPriority.HIGH);
    assertEquals(0, mapper().count(req, User.ADMIN_MAGIC_KEY));
  }

  private JobInfo aged(String jobClass, JobStatus status, int daysAgo) {
    JobInfo j = create(status);
    j.setJob(jobClass);
    j.setCreated(LocalDateTime.now().minusDays(daysAgo).truncatedTo(ChronoUnit.MILLIS));
    return j;
  }

  /**
   * The retention policy of the periodic JobCleanup: age, the per class floor and the live job guard.
   * That it never touches a job some metrics point at is covered where those fixtures live, see
   * DatasetImportMapperTest, SectorImportMapperTest and DatasetExportMapperTest.
   */
  @Test
  public void deleteOld() throws Exception {
    // 5 old and one recent, plus an ancient one that is still running
    for (int i = 0; i < 5; i++) {
      mapper().create(aged("AlphaJob", JobStatus.FINISHED, 200 + i));
    }
    mapper().create(aged("AlphaJob", JobStatus.FINISHED, 5));
    mapper().create(aged("AlphaJob", JobStatus.RUNNING, 300));
    commit();
    assertEquals(7, mapper().count(new JobSearchRequest(), User.ADMIN_MAGIC_KEY));

    // newest 2 finished ones of the class are kept whatever their age, so 4 of the 5 old ones go.
    // the keys of exactly those rows come back, so JobCleanup can delete their logs
    var gone = mapper().deleteOld(90, Map.of(), 2, 100);
    commit();
    assertEquals(4, gone.size());
    for (UUID key : gone) {
      assertNull("deleteOld returned a key that is still there", mapper().get(key));
    }
    assertEquals(3, mapper().count(new JobSearchRequest(), User.ADMIN_MAGIC_KEY));

    // nothing left that is both beyond the default age and outside the per class floor
    assertTrue(mapper().deleteOld(90, Map.of(), 2, 100).isEmpty());

    // a per class override applies instead of the default, matched case insensitively
    assertEquals(2, mapper().deleteOld(90, Map.of("alphajob", 1), 0, 100).size());
    commit();

    // the running job survived all of it - a live job is never a candidate, however old its record is
    var left = mapper().search(new JobSearchRequest(), User.ADMIN_MAGIC_KEY, new Page());
    assertEquals(1, left.size());
    assertEquals(JobStatus.RUNNING, left.get(0).getStatus());
  }

  /**
   * The batch limit is applied after every filter, so one call deletes exactly min(limit, remaining).
   * JobCleanup loops while the result equals the limit, which stalls if that is not true.
   */
  @Test
  public void deleteOldBatching() throws Exception {
    for (int i = 0; i < 5; i++) {
      mapper().create(aged("BetaJob", JobStatus.FINISHED, 200 + i));
    }
    commit();

    assertEquals(2, mapper().deleteOld(90, Map.of(), 0, 2).size());
    commit();
    assertEquals(2, mapper().deleteOld(90, Map.of(), 0, 2).size());
    commit();
    assertEquals(1, mapper().deleteOld(90, Map.of(), 0, 2).size());
    commit();
    assertEquals(0, mapper().count(new JobSearchRequest(), User.ADMIN_MAGIC_KEY));
  }

  @Test
  public void cancelStale() throws Exception {
    mapper().create(create(JobStatus.WAITING));
    mapper().create(create(JobStatus.BLOCKED));
    mapper().create(create(JobStatus.RUNNING));
    var done = create(JobStatus.FINISHED);
    mapper().create(done);
    commit();

    assertEquals(3, mapper().cancelStale());
    commit();

    JobSearchRequest req = new JobSearchRequest();
    req.setStatus(Set.of(JobStatus.CANCELED));
    var canceled = mapper().search(req, User.ADMIN_MAGIC_KEY, new Page());
    assertEquals(3, canceled.size());
    for (JobInfo j : canceled) {
      assertNotNull(j.getFinished());
    }
    assertEquals(JobStatus.FINISHED, mapper().get(done.getKey()).getStatus());
  }

  @Test
  public void searchBySectorKey() throws Exception {
    JobInfo a = create(JobStatus.FINISHED);
    a.setSectorKey(10);
    mapper().create(a);

    JobInfo b = create(JobStatus.FINISHED);
    b.setSectorKey(20);
    mapper().create(b);

    JobInfo c = create(JobStatus.FINISHED);
    c.setSectorKey(null);
    mapper().create(c);
    commit();

    var req = new JobSearchRequest();
    req.setSectorKey(10);
    assertEquals(1, mapper().search(req, User.ADMIN_MAGIC_KEY, new Page()).size());
    assertEquals(1, mapper().count(req, User.ADMIN_MAGIC_KEY));

    req.setSectorKey(20);
    assertEquals(1, mapper().search(req, User.ADMIN_MAGIC_KEY, new Page()).size());

    req.setSectorKey(99);
    assertEquals(0, mapper().search(req, User.ADMIN_MAGIC_KEY, new Page()).size());
  }

  /**
   * The job listing shows job keys, which address results and logs on the download host, so the jobs of a private
   * dataset are only listed for those who can read it and for their creator, see gbif/informatics#85 (A6).
   */
  @Test
  public void searchHidesPrivateDatasets() throws Exception {
    var byInit = create(JobStatus.FINISHED);
    var byTester = create(JobStatus.FINISHED);
    byTester.setCreatedBy(Users.TESTER);
    var noDataset = create(JobStatus.FINISHED);
    noDataset.setDatasetKey(null);
    noDataset.setCreatedBy(Users.TESTER);
    mapper().create(byInit);
    mapper().create(byTester);
    mapper().create(noDataset);

    User editor = new User();
    editor.setUsername("editor");
    mapper(UserMapper.class).create(editor);
    mapper(DatasetMapper.class).addEditor(appleKey, editor.getKey(), Users.DB_INIT);
    commit();

    var req = new JobSearchRequest();
    assertEquals("a public dataset hides nothing", 3, mapper().count(req, null));

    var apple = mapper(DatasetMapper.class).get(appleKey);
    apple.setPrivat(true);
    mapper(DatasetMapper.class).update(apple);
    commit();

    assertEquals(1, mapper().count(req, null));
    assertEquals(noDataset.getKey(), mapper().search(req, null, new Page()).get(0).getKey());
    // the creator of a job sees it
    assertEquals(2, mapper().count(req, Users.TESTER));
    assertEquals(2, mapper().count(req, Users.DB_INIT));
    // an editor of the dataset sees all of them
    assertEquals(3, mapper().count(req, editor.getKey()));
    assertEquals(3, mapper().search(req, editor.getKey(), new Page()).size());
    assertEquals(3, mapper().count(req, User.ADMIN_MAGIC_KEY));

    // other filters still apply
    req.setDatasetKey(appleKey);
    assertEquals(0, mapper().count(req, null));
    assertEquals(2, mapper().count(req, editor.getKey()));
  }
}
