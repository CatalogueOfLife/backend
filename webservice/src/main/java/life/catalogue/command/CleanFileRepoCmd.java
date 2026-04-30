package life.catalogue.command;

import life.catalogue.api.model.DatasetRelease;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.db.mapper.DatasetMapper;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Removes on-disk import and sector metric files that are no longer needed.
 * For deleted release datasets only private releases have their metric files removed;
 * for deleted external and project datasets all metric files (including sector sub-directories)
 * are deleted.
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
        } else if (d.isPrivat()){
          LOG.info("Delete file metrics for private deleted {} {} of project {} (datasetKey={})", d.getOrigin(), d.getAttempt(), d.getProjectKey(), d.getKey());
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
