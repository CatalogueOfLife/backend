package life.catalogue.jobs;

import life.catalogue.api.model.User;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.concurrent.GlobalBlockingJob;
import life.catalogue.concurrent.JobPriority;
import life.catalogue.config.ImporterConfig;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.importer.ImportManager;
import life.catalogue.importer.ImportRequest;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Joiner;

/**
 * Submits import jobs for all existing archives of external datasets.
 * Throttles the submission so the import manager does not exceed its queue
 */
public class ReimportJob extends GlobalBlockingJob {
  private static final Logger LOG = LoggerFactory.getLogger(ReimportJob.class);

  private final SqlSessionFactory factory;
  private final ImportManager importManager;
  private final ImporterConfig iCfg;
  private final NormalizerConfig nCfg;

  @JsonProperty
  private int counter;

  public ReimportJob(User user, SqlSessionFactory factory, ImportManager importManager, ImporterConfig iCfg, NormalizerConfig nCfg) {
    super(user.getKey(), JobPriority.HIGH);
    this.factory = factory;
    this.importManager = importManager;
    this.iCfg = iCfg;
    this.nCfg = nCfg;
  }

  @Override
  public void execute() {
    final List<Integer> keys;
    try (SqlSession session = factory.openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      keys = dm.keys(DatasetOrigin.EXTERNAL);
    }

    LOG.warn("Reimporting all {} datasets from their last local copy", keys.size());
    final List<Integer> missed = new ArrayList<>();
    counter = 0;
    for (int key : keys) {
      try {
        while (importManager.queueSize() + 5 > iCfg.maxQueue) {
          TimeUnit.MINUTES.sleep(1);
        }
        // does a local archive exist?
        File latestArchive = nCfg.lastestArchive(key);
        if (latestArchive != null && latestArchive.exists()) {
          int attempt = nCfg.attemptFromArchive(latestArchive);
          LOG.debug("Import local archive for dataset {} from last attempt {}", key, attempt);
          ImportRequest req = ImportRequest.reimport(key, attempt, getUserKey());
          importManager.submit(req);
          counter++;
        } else {
          missed.add(key);
          LOG.warn("No local archive exists for dataset {}. Do not reimport", key);
        }

      } catch (IllegalArgumentException e) {
        missed.add(key);
        LOG.warn("Cannot reimport dataset {}", key, e);

      } catch (InterruptedException e) {
        LOG.warn("Reimporting interrupted", e);
        break;

      } catch (IOException e) {
        LOG.warn("Error reimporting dataset {}", key, e);
      }
    }
    LOG.info("Scheduled {} datasets out of {} for reimporting. Missed {} datasets without an archive or other reasons", counter, keys.size(), missed.size());
    LOG.info("Missed keys: {}", Joiner.on(", ").join(missed));
  }
}

