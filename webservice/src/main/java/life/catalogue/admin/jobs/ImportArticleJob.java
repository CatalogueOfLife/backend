package life.catalogue.admin.jobs;

import com.fasterxml.jackson.annotation.JsonProperty;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.User;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.concurrent.GlobalBlockingJob;
import life.catalogue.concurrent.JobPriority;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.importer.ImportManager;
import life.catalogue.importer.ImportRequest;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Submits import jobs for all plazi datasets and other datasets of type ARTICLE.
 * Throttles the submission so the import manager does not exceed its queue
 */
public class ImportArticleJob extends GlobalBlockingJob {
  private static final Logger LOG = LoggerFactory.getLogger(ImportArticleJob.class);

  private final SqlSessionFactory factory;
  private final ImportManager importManager;
  private final WsServerConfig cfg;

  @JsonProperty
  private int counter;

  public ImportArticleJob(User user, SqlSessionFactory factory, ImportManager importManager, WsServerConfig cfg) {
    super(user.getKey(), JobPriority.HIGH);
    this.factory = factory;
    this.importManager = importManager;
    this.cfg = cfg;
  }

  @Override
  public void execute() {
    DatasetSearchRequest dreq = new DatasetSearchRequest();
    dreq.setType(List.of(DatasetType.ARTICLE));
    dreq.setOrigin(List.of(DatasetOrigin.EXTERNAL));

    final List<Integer> keys;
    try (SqlSession session = factory.openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      keys = dm.searchKeys(dreq);
    }

    LOG.warn("Reimporting all {} datasets from their last local copy", keys.size());
    counter = 0;
    for (int key : keys) {
      try {
        while (importManager.queueSize() + 5 > cfg.importer.maxQueue) {
          TimeUnit.MINUTES.sleep(1);
        }
        // does a local archive exist?
        ImportRequest req = ImportRequest.external(key, getUserKey());
        importManager.submit(req);
        counter++;

      } catch (InterruptedException e) {
        LOG.warn("Scheduling article imports interrupted", e);
        break;
      }
    }
    LOG.info("Scheduled {} datasets for importing", counter);
  }
}

