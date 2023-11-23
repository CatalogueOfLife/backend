package life.catalogue.matching;

import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.common.util.LoggingUtils;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.ArchivedNameUsageMapper;
import life.catalogue.db.mapper.ArchivedNameUsageMatchMapper;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.NameMatchMapper;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rematches entire datasets, using 2 separate db connections for read & write
 * In case of projects being matched it will also match any archived name usages.
 */
public class DatasetMatcher extends BaseMatcher {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetMatcher.class);
  private int archived = 0;
  private int datasets = 0;

  public DatasetMatcher(SqlSessionFactory factory, NameIndex ni) {
    super(factory, ni);
  }

  /**
   * Matches all names of an entire dataset and updates its name index id and issues in postgres
   *
   * @param allowInserts if true allows inserts into the names index
   */
  public void match(int datasetKey, boolean allowInserts) throws RuntimeException {
    try {
      LoggingUtils.setDatasetMDC(datasetKey, getClass());
      final int totalBefore = total;
      final int updatedBefore = updated;
      final int nomatchBefore = nomatch;
      final int archivedBefore = archived;

      final boolean doUpdate;
      try (SqlSession session = factory.openSession(true)) {
        doUpdate = session.getMapper(NameMatchMapper.class).exists(datasetKey, null);
      }

      try (SqlSession readOnlySession = factory.openSession(true);
           BulkMatchHandler hn = new BulkMatchHandler(datasetKey, allowInserts, NameMatchMapper.class, doUpdate);
           BulkMatchHandler hu = new BulkMatchHandler(datasetKey, allowInserts, ArchivedNameUsageMatchMapper.class, true);
      ) {
        NameMapper nm = readOnlySession.getMapper(NameMapper.class);
        ArchivedNameUsageMapper anum = readOnlySession.getMapper(ArchivedNameUsageMapper.class);

        final boolean isProject = DatasetInfoCache.CACHE.info(datasetKey).origin == DatasetOrigin.PROJECT;
        LOG.info("{} name matches for {}{}", doUpdate ? "Update" : "Create", isProject ? "project " : "", datasetKey);
        PgUtils.consume(() -> nm.processDataset(datasetKey), hn);
        // also match archived names
        if (isProject) {
          LOG.info("{} name archive matches for project {}", doUpdate ? "Update" : "Create", datasetKey);
          final int totalBeforeArchive = total;
          PgUtils.consume(() -> anum.processArchivedNames(datasetKey), hu);
          archived = archived + total - totalBeforeArchive;
        }
      } catch (RuntimeException e) {
        LOG.error("Failed to rematch dataset {}", datasetKey, e);
        throw e;
      } finally {
        datasets++;
        LOG.info("{} {} name matches for {} names and {} not matching, {} being archived names, for dataset {}", doUpdate ? "Updated" : "Created",
          updated - updatedBefore, total - totalBefore, nomatch - nomatchBefore, archived - archivedBefore, datasetKey);
      }

      if (doUpdate) {
        try (SqlSession session = factory.openSession(false)) {
          int del = session.getMapper(NameMatchMapper.class).deleteOrphans(datasetKey);
          if (del > 0) {
            LOG.info("Removed {} orphaned name matches for {}", del, datasetKey);
          }
          del = session.getMapper(ArchivedNameUsageMatchMapper.class).deleteOrphans(datasetKey);
          if (del > 0) {
            LOG.info("Removed {} orphaned name archive matches for {}", del, datasetKey);
          }
        }
      }
    } finally {
      LoggingUtils.removeDatasetMDC();
    }
  }

  public int getDatasets() {
    return datasets;
  }

}
