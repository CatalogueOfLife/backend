package life.catalogue.command;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.dao.SectorImportDao;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.DatasetImportMapper;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.SectorImportMapper;
import life.catalogue.db.mapper.SectorMapper;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 */
public class CleanFileRepoCmd extends AbstractMybatisCmd {
  private static final Logger LOG = LoggerFactory.getLogger(CleanFileRepoCmd.class);
  private DatasetImportDao did;

  public CleanFileRepoCmd() {
    super("cleanFileRepo", false, "Remove all import and sector file metrics that are no longer needed");
  }

  @Override
  public void execute() throws Exception {
    // setup
    did = new DatasetImportDao(factory, cfg.metricsRepo);

    // deleted datasets
    try (SqlSession session = factory.openSession()) {
      var dm = session.getMapper(DatasetMapper.class);
      for (var d : dm.listDeletedQuick()) {
        cleanDeletedDataset(d);
      }
    }

    // we dont store deleted sectors in the db
    // TODO: go through the files
  }

  private void cleanDeletedDataset(DatasetRelease d) {
    try {
      if (d.getOrigin().isRelease()) {
        if (d.getProjectKey() == 0 || d.getAttempt() == 0) {
          LOG.warn("Bad project/attempt key ({}/{}) for {} {}", d.getProjectKey(), d.getAttempt(), d.getOrigin(), d.getKey());
        } else {
          LOG.info("Delete file metrics for deleted {} {} of project {} (datasetKey={})", d.getOrigin(), d.getAttempt(), d.getProjectKey(), d.getKey());
          did.getFileMetricsDao().deleteAttempt(d.getProjectKey(), d.getAttempt());
        }

      } else {
        LOG.info("Delete file metrics for deleted {} dataset {}", d.getOrigin(), d.getKey());
        // this also deletes all sector metrics in case of projects, as these are in a subdir of the dataset
        did.getFileMetricsDao().deleteAll(d.getKey());
      }
    } catch (IOException e) {
      LOG.warn("Failed to delete file metrics for dataset {}", d.getKey(), e);
    }
  }

}
