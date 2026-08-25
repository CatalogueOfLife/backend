package life.catalogue.jobs;

import life.catalogue.api.model.User;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.concurrent.GlobalBlockingJob;
import life.catalogue.api.vocab.JobPriority;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.importer.ImportManager;
import life.catalogue.importer.ImportRequest;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Submits import jobs for all plazi datasets and other datasets of type ARTICLE.
 * Throttles the submission so the import manager does not exceed its queue
 */
public class ImportArticleJob extends GlobalBlockingJob {
  private static final Logger LOG = LoggerFactory.getLogger(ImportArticleJob.class);

  // flushing the progress on every single submit would persist a job update each time
  private static final int STEP_BATCH = 50;

  private final SqlSessionFactory factory;
  private final ImportManager importManager;

  public ImportArticleJob(User user, SqlSessionFactory factory, ImportManager importManager) {
    super(user.getKey(), JobPriority.HIGH);
    this.factory = factory;
    this.importManager = importManager;
  }

  @Override
  public void execute() {
    DatasetSearchRequest dreq = new DatasetSearchRequest();
    dreq.setType(List.of(DatasetType.ARTICLE));
    dreq.setOrigin(List.of(DatasetOrigin.EXTERNAL));

    final List<Integer> keys;
    try (SqlSession session = factory.openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      keys = dm.searchKeys(dreq, DatasetMapper.MAGIC_ADMIN_USER_KEY);
    }

    LOG.warn("Importing all {} article datasets", keys.size());
    int counter = 0;
    setStep(progress(counter, keys.size()));
    for (int key : keys) {
      try {
        while (importManager.queueSize() + 5 > importManager.maxQueue()) {
          TimeUnit.MINUTES.sleep(1);
        }
        ImportRequest req = ImportRequest.external(key, getUserKey());
        importManager.submit(req);
        counter++;
        if (counter % STEP_BATCH == 0) {
          setStep(progress(counter, keys.size()));
        }

      } catch (InterruptedException e) {
        LOG.warn("Scheduling article imports interrupted", e);
        break;
      }
    }
    setStep(progress(counter, keys.size()));
    LOG.info("Scheduled {} datasets for importing", counter);
  }

  private static String progress(int scheduled, int total) {
    return "scheduled " + scheduled + " of " + total;
  }
}

