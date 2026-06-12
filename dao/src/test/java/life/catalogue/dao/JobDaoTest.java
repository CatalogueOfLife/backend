package life.catalogue.dao;

import life.catalogue.api.model.JobResult;
import life.catalogue.api.vocab.JobPriority;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.concurrent.BackgroundJob;

import org.junit.Test;

import static org.junit.Assert.*;

public class JobDaoTest {

  static class ResultJob extends BackgroundJob {
    final JobResult result = new JobResult(getKey());

    ResultJob() {
      super(JobPriority.HIGH, 13);
      result.setMd5("ec3b96bb40601e84e6e9a31035cbb9f9");
      result.setSize(42L);
    }

    @Override
    public void execute() {
    }

    @Override
    public Object getParams() {
      return new Params(3, true);
    }

    @Override
    public JobResult getResult() {
      return result;
    }

    @Override
    public Integer datasetKey() {
      return 3;
    }

    record Params(int datasetKey, boolean force) {
    }
  }

  @Test
  public void buildInfo() {
    var job = new ResultJob();
    var info = JobDao.buildInfo(job);

    assertEquals(job.getKey(), info.getKey());
    assertEquals("ResultJob", info.getJob());
    assertEquals(JobStatus.WAITING, info.getStatus());
    assertEquals(JobPriority.HIGH, info.getPriority());
    assertEquals((Integer) 3, info.getDatasetKey());
    assertNull(info.getSectorKey());
    assertEquals((Integer) 13, info.getCreatedBy());
    assertEquals(job.getCreated(), info.getCreated());
    assertNull(info.getError());
    assertEquals("ec3b96bb40601e84e6e9a31035cbb9f9", info.getResultMd5());
    assertEquals((Long) 42L, info.getResultSize());
    assertEquals(3, info.getParams().get("datasetKey").asInt());
    assertTrue(info.getParams().get("force").asBoolean());

    job.setStatus(JobStatus.FAILED);
    job.setError(new IllegalStateException("boom", new RuntimeException("root cause")));
    info = JobDao.buildInfo(job);
    assertEquals(JobStatus.FAILED, info.getStatus());
    assertEquals("boom", info.getError());
  }
}
