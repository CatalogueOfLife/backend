package life.catalogue.jobs;

import life.catalogue.common.date.DateUtils;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.DatasetBlockingJob;
import life.catalogue.api.vocab.JobPriority;
import life.catalogue.dao.DaoUtils;
import life.catalogue.db.mapper.VerbatimSourceMapper;
import life.catalogue.es.indexing.NameUsageIndexService;
import life.catalogue.release.TreeCleanerAndValidator;

import java.time.LocalDateTime;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates a given project using the TreeCleanerAndValidator
 */
public class ProjectValidationJob extends DatasetBlockingJob {
  private static final Logger LOG = LoggerFactory.getLogger(ProjectValidationJob.class);

  private final SqlSessionFactory factory;
  private final NameUsageIndexService indexService;

  public ProjectValidationJob(int userKey, SqlSessionFactory factory, NameUsageIndexService indexService, int datasetKey) {
    super(datasetKey, userKey, JobPriority.HIGH);
    this.factory = factory;
    this.indexService = indexService;
    DaoUtils.requireProject(datasetKey);
  }

  @Override
  protected void runWithLock() throws Exception {
    LOG.info("Remove existing issues from project {}", datasetKey);
    // remove all existing issues
    try (SqlSession session = factory.openSession(true)){
      var vsm = session.getMapper(VerbatimSourceMapper.class);
      vsm.removeAllIssues(datasetKey);
    }

    LOG.info("Clean and validate entire project {}", datasetKey);
    final LocalDateTime start = LocalDateTime.now();
    // a failure fails the job - the issues have been removed already and must not look validated
    try (SqlSession session = factory.openSession(true)) {
      var consumer = new TreeCleanerAndValidator(session, datasetKey, false);
      consumer.validate(session);
      LOG.info("Maximum depth of {} found for accepted tree of project {}", consumer.getMaxDepth(), datasetKey);
      LOG.info("{} usages out of {} flagged with issues during validation", consumer.getFlagged(), consumer.getCounter());
    }
    DateUtils.logDuration(LOG, TreeCleanerAndValidator.class, start);

    // reindex entire dataset
    LOG.info("Reindex project {}", datasetKey);
    indexService.indexDataset(datasetKey);
  }

  @Override
  public boolean isDuplicate(BackgroundJob other) {
    if (other instanceof ProjectValidationJob) {
      ProjectValidationJob job = (ProjectValidationJob) other;
      return datasetKey == job.datasetKey;
    }
    return false;
  }
}
