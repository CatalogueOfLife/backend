package life.catalogue.dao;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.JobInfo;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.search.JobSearchRequest;
import life.catalogue.common.lang.Exceptions;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.db.mapper.JobMapper;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persistence for the generic job table.
 * Every job submitted to the JobExecutor is recorded with its full lifecycle,
 * no matter if it has specific satellite tables (imports, syncs, exports) or not.
 */
public class JobDao {
  private static final Logger LOG = LoggerFactory.getLogger(JobDao.class);

  private final SqlSessionFactory factory;

  public JobDao(SqlSessionFactory factory) {
    this.factory = factory;
  }

  public JobInfo get(UUID key) {
    try (SqlSession session = factory.openSession(true)) {
      return session.getMapper(JobMapper.class).get(key);
    }
  }

  public ResultPage<JobInfo> search(JobSearchRequest req, Page page) {
    final Page p = page == null ? new Page() : page;
    try (SqlSession session = factory.openSession(true)) {
      JobMapper jm = session.getMapper(JobMapper.class);
      List<JobInfo> result = jm.search(req, p);
      return new ResultPage<>(p, result, () -> jm.count(req));
    }
  }

  public void create(BackgroundJob job) {
    try (SqlSession session = factory.openSession(true)) {
      session.getMapper(JobMapper.class).create(buildInfo(job));
    }
  }

  public void update(BackgroundJob job) {
    try (SqlSession session = factory.openSession(true)) {
      session.getMapper(JobMapper.class).update(buildInfo(job));
    }
  }

  public void delete(UUID key) {
    try (SqlSession session = factory.openSession(true)) {
      session.getMapper(JobMapper.class).delete(key);
    }
  }

  /**
   * Marks all waiting, blocked or running jobs as canceled.
   * To be called on startup before any new job is submitted,
   * cleaning up jobs that did not survive the last shutdown.
   * @return the jobs that have been cancelled
   */
  public List<JobInfo> cancelStale() {
    try (SqlSession session = factory.openSession(true)) {
      JobMapper jm = session.getMapper(JobMapper.class);
      List<JobInfo> stale = jm.listStale();
      if (!stale.isEmpty()) {
        jm.cancelStale();
        LOG.warn("Canceled {} stale jobs from a previous server run", stale.size());
      }
      return stale;
    }
  }

  /**
   * Builds the generic, persistable job representation from a live background job.
   */
  public static JobInfo buildInfo(BackgroundJob job) {
    JobInfo info = new JobInfo();
    info.setKey(job.getKey());
    info.setJob(job.getJobName());
    info.setStatus(job.getStatus());
    info.setStep(job.getStep());
    info.setPriority(job.getPriority());
    info.setDatasetKey(job.datasetKey());
    info.setSectorKey(job.sectorKey());
    info.setCreatedBy(job.getUserKey());
    info.setCreated(job.getCreated());
    info.setStarted(job.getStarted());
    info.setFinished(job.getFinished());
    if (job.getError() != null) {
      info.setError(Exceptions.getFirstMessage(job.getError()));
    }
    if (job.getParams() != null) {
      info.setParams(ApiModule.MAPPER.valueToTree(job.getParams()));
    }
    var result = job.getResult();
    if (result != null) {
      info.setResultMd5(result.getMd5());
      info.setResultSize(result.getSize());
    }
    return info;
  }
}
