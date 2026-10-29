package life.catalogue.basgroup;

import life.catalogue.api.vocab.JobPriority;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.concurrent.DatasetBlockingJob;
import life.catalogue.dao.DaoUtils;
import life.catalogue.db.mapper.NameUsageMapper;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Cleans up the name relations of a project or release, entirely or below a root taxon,
 * as an extended release does before it groups homotypic names, see {@link NameRelationCleaner}.
 * External datasets keep their relations as imported, only flagged by the importer.
 */
public class NameRelationCleanerJob extends DatasetBlockingJob {
  private final SqlSessionFactory factory;
  @Nullable
  private final String taxonID;
  private Integer removed;

  /**
   * @param taxonID optional root taxon limiting the cleanup to its subtree
   * @throws life.catalogue.api.exception.NotFoundException if the root taxon does not exist
   * @throws IllegalArgumentException if the dataset is no project or release or the root no accepted name
   */
  public NameRelationCleanerJob(SqlSessionFactory factory, int datasetKey, int userKey, @Nullable String taxonID) {
    super(datasetKey, userKey, JobPriority.MEDIUM);
    DaoUtils.requireProjectOrRelease(datasetKey);
    if (taxonID != null) {
      try (SqlSession session = factory.openSession()) {
        HomotypicConsolidationJob.requireAcceptedRoot(session.getMapper(NameUsageMapper.class), datasetKey, taxonID);
      }
    }
    this.factory = factory;
    this.taxonID = taxonID;
    this.logToFile = true;
  }

  /**
   * @param taxonID the root taxon to clean, null when the entire dataset is cleaned
   */
  public record CleanerParams(int datasetKey, String taxonID) {
  }

  @Override
  public Object getParams() {
    return new CleanerParams(datasetKey, taxonID);
  }

  @Override
  protected void runWithLock() throws Exception {
    removed = new NameRelationCleaner(factory, datasetKey, taxonID).clean();
  }

  @Override
  protected void onFinishLocked() throws Exception {
    // a successful job's step is cleared before onFinish runs, so the outcome has to be set here to be kept
    if (getStatus() == JobStatus.FINISHED && removed != null) {
      setStep("removed " + removed + " name relations");
    }
  }
}
