package life.catalogue.admin.jobs;

import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.common.date.DateUtils;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.DatasetBlockingJob;
import life.catalogue.concurrent.JobPriority;
import life.catalogue.dao.DaoUtils;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.VerbatimSourceMapper;
import life.catalogue.es.NameUsageIndexService;

import life.catalogue.release.TreeCleanerAndValidator;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * Resets all dataset sector counts for an entire catalogue, see param datasetKey,
 * and rebuilds the counts from the currently mapped sectors
 */
public class ValidationJob extends DatasetBlockingJob {
  private static final Logger LOG = LoggerFactory.getLogger(ValidationJob.class);

  private final SqlSessionFactory factory;
  private final NameUsageIndexService indexService;

  public ValidationJob(int userKey, SqlSessionFactory factory, NameUsageIndexService indexService, int datasetKey) {
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
    try (SqlSession session = factory.openSession(true);
         var consumer = new TreeCleanerAndValidator(factory, datasetKey, false)
    ) {
      var num = session.getMapper(NameUsageMapper.class);
      TreeTraversalParameter params = new TreeTraversalParameter();
      params.setDatasetKey(datasetKey);
      params.setSynonyms(false);

      PgUtils.consume(() -> num.processTreeLinneanUsage(params, true, false), consumer);
      LOG.info("Maximum depth of {} found for accepted tree of project {}", consumer.getMaxDepth(), datasetKey);
      LOG.info("{} usages out of {} flagged with issues during validation", consumer.getFlagged(), consumer.getCounter());

      // reindex entire dataset
      LOG.info("Reindex project {}", datasetKey);
      indexService.indexDataset(datasetKey);

    } catch (Exception e) {
      LOG.error("Name validation & cleaning failed", e);
    }
    DateUtils.logDuration(LOG, TreeCleanerAndValidator.class, start);
  }

  @Override
  public boolean isDuplicate(BackgroundJob other) {
    if (other instanceof ValidationJob) {
      ValidationJob job = (ValidationJob) other;
      return datasetKey == job.datasetKey;
    }
    return false;
  }
}
