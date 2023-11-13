package life.catalogue.matching;

import life.catalogue.api.vocab.DatasetOrigin;
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
    final int totalBefore = total;
    final int updatedBefore = updated;
    final int nomatchBefore = nomatch;
    final int archivedBefore = archived;

    boolean update = false;
    try (SqlSession readOnlySession = factory.openSession(true);
         BulkMatchHandler hn = new BulkMatchHandlerNames(datasetKey, allowInserts);
         BulkMatchHandler hu = new BulkMatchHandlerArchivedUsages(datasetKey, allowInserts)
    ) {
      NameMatchMapper nmm = readOnlySession.getMapper(NameMatchMapper.class);
      NameMapper nm = readOnlySession.getMapper(NameMapper.class);
      ArchivedNameUsageMapper anum = readOnlySession.getMapper(ArchivedNameUsageMapper.class);

      update = nmm.exists(datasetKey);
      final boolean isProject = DatasetInfoCache.CACHE.info(datasetKey).origin == DatasetOrigin.PROJECT;
      LOG.info("{} name matches for {}{}", update ? "Update" : "Create", isProject ? "project " : "", datasetKey);
      PgUtils.consume(() -> nm.processDataset(datasetKey), hn);
      // also match archived names
      if (isProject) {
        final int totalBeforeArchive = total;
        PgUtils.consume(() -> anum.processArchivedNames(datasetKey), hu);
        archived = archived + total - totalBeforeArchive;
      }
    } catch (RuntimeException e) {
      LOG.error("Failed to rematch dataset {}", datasetKey, e);
      throw e;
    } finally {
      datasets++;
      LOG.info("{} {} name matches for {} names and {} not matching, {} being archived names, for dataset {}", update ? "Updated" : "Created",
        updated - updatedBefore, total - totalBefore, nomatch - nomatchBefore, archived - archivedBefore, datasetKey);
    }

    if (update) {
      try (SqlSession session = factory.openSession(false)) {
        int del = session.getMapper(NameMatchMapper.class).deleteOrphans(datasetKey);
        if (del > 0) {
          LOG.info("Removed {} orphaned name matches for {}", del, datasetKey);
        }
        del = session.getMapper(ArchivedNameUsageMatchMapper.class).deleteOrphaned(datasetKey);
        if (del > 0) {
          LOG.info("Removed {} orphaned name archive matches for {}", del, datasetKey);
        }
      }
    }
  }

  public int getDatasets() {
    return datasets;
  }

}
