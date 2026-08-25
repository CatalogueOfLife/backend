package life.catalogue.db.mapper;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.JobInfo;
import life.catalogue.api.model.Page;
import life.catalogue.api.search.JobSearchRequest;
import life.catalogue.api.vocab.JobLane;
import life.catalogue.api.vocab.JobPriority;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.api.vocab.Users;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
    // params are updatable, not just inserted: a release completes them once it knows its new dataset key
    ObjectNode params = ApiModule.MAPPER.createObjectNode();
    params.put("datasetKey", appleKey);
    params.put("newDatasetKey", 999);
    params.put("attempt", 7);
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
    assertEquals(4, mapper().count(req));
    assertEquals(4, mapper().search(req, new Page()).size());

    req.setStatus(Set.of(JobStatus.WAITING, JobStatus.RUNNING));
    assertEquals(2, mapper().count(req));

    req = new JobSearchRequest();
    req.setJob(Set.of("OtherJob"));
    assertEquals(1, mapper().count(req));
    // job names are matched case insensitively and several can be combined
    req.setJob(Set.of("otherjob"));
    assertEquals(1, mapper().count(req));
    req.setJob(Set.of("otherjob", "testjob"));
    assertEquals(4, mapper().count(req));
    req.setJob(Set.of("NoSuchJob"));
    assertEquals(0, mapper().count(req));

    req = new JobSearchRequest();
    req.setLane(Set.of(JobLane.SYNC));
    assertEquals(1, mapper().count(req));
    req.setLane(Set.of(JobLane.DEFAULT, JobLane.SYNC));
    assertEquals(4, mapper().count(req));
    req.setLane(Set.of(JobLane.IMPORT));
    assertEquals(0, mapper().count(req));

    req = new JobSearchRequest();
    req.setSectorKey(13);
    assertEquals(1, mapper().count(req));
    req.setSectorKey(14);
    assertEquals(0, mapper().count(req));

    req = new JobSearchRequest();
    req.setCreatedAfter(LocalDateTime.now().minusDays(1));
    assertEquals(4, mapper().count(req));
    req.setCreatedAfter(LocalDateTime.now().plusDays(1));
    assertEquals(0, mapper().count(req));

    req = new JobSearchRequest();
    req.setCreatedBefore(LocalDateTime.now().plusDays(1));
    assertEquals(4, mapper().count(req));
    req.setCreatedBefore(LocalDateTime.now().minusDays(1));
    assertEquals(0, mapper().count(req));

    req = new JobSearchRequest();
    req.setCreatedBy(Users.TESTER);
    assertEquals(1, mapper().count(req));

    req = new JobSearchRequest();
    req.setKey(failed.getKey());
    var res = mapper().search(req, new Page());
    assertEquals(1, res.size());
    assertEquals(failed, res.get(0));

    req = new JobSearchRequest();
    req.setDatasetKey(appleKey);
    assertEquals(4, mapper().count(req));
    req.setDatasetKey(-99);
    assertEquals(0, mapper().count(req));

    req = new JobSearchRequest();
    req.setPriority(JobPriority.MEDIUM);
    assertEquals(4, mapper().count(req));
    req.setPriority(JobPriority.HIGH);
    assertEquals(0, mapper().count(req));
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
    var canceled = mapper().search(req, new Page());
    assertEquals(3, canceled.size());
    for (JobInfo j : canceled) {
      assertNotNull(j.getFinished());
    }
    assertEquals(JobStatus.FINISHED, mapper().get(done.getKey()).getStatus());
  }
}
