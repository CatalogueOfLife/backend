package life.catalogue.matching;

import life.catalogue.api.event.FlushDatasetCache;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.ArchivedNameUsageMapper;
import life.catalogue.db.mapper.ArchivedNameUsageMatchMapper;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.NameMatchMapper;

import javax.annotation.Nullable;

import life.catalogue.matching.nidx.NameIndex;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;

/**
 * Rematches entire datasets, using 2 separate db connections for read & write
 * In case of projects being matched it will also match any archived name usages.
 */
public class DatasetMatcher extends BaseMatcher {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetMatcher.class);
  private final EventBus bus;
  private int archived = 0;
  private int datasets = 0;

  public DatasetMatcher(SqlSessionFactory factory, NameIndex ni, @Nullable EventBus bus) {
    super(factory, ni);
    this.bus = bus;
  }

  /**
   * Matches all names of an entire dataset and updates its name index id and issues in postgres
   *
   * @param allowInserts if true allows inserts into the names index
   * @param onlyMissingMatches if true only names without an existing matching record will be re-matched
   */
  public void match(int datasetKey, boolean allowInserts, boolean onlyMissingMatches) throws RuntimeException {
    try {
      final int totalBefore = total;
      final int updatedBefore = updated;
      final int nomatchBefore = nomatch;
      final int archivedBefore = archived;

      final boolean doUpdate;
      try (SqlSession session = factory.openSession(true)) {
        doUpdate = !onlyMissingMatches && session.getMapper(NameMatchMapper.class).exists(datasetKey, null);
      }

      try (SqlSession readOnlySession = factory.openSession(true);
           BulkMatchHandler hn = new BulkMatchHandler(allowInserts, NameMatchMapper.class, doUpdate);
           BulkMatchHandler hu = new BulkMatchHandler(allowInserts, ArchivedNameUsageMatchMapper.class, true);
      ) {
        NameMapper nm = readOnlySession.getMapper(NameMapper.class);

        final boolean isProject = DatasetInfoCache.CACHE.info(datasetKey).origin == DatasetOrigin.PROJECT;
        LOG.info("{} {}name matches for {}{}", doUpdate ? "Update" : "Create", onlyMissingMatches?"missing ":"", isProject ? "project " : "", datasetKey);
        PgUtils.consume(() -> onlyMissingMatches ?
            nm.processDatasetWithoutMatches(datasetKey) :
            nm.processDataset(datasetKey), hn
        );
        // also match archived names
        if (isProject) {
          ArchivedNameUsageMapper anum = readOnlySession.getMapper(ArchivedNameUsageMapper.class);
          LOG.info("{} {}name archive matches for project {}", doUpdate ? "Update" : "Create", onlyMissingMatches?"missing ":"", datasetKey);
          final int totalBeforeArchive = total;
          PgUtils.consume(() -> anum.processArchivedNames(datasetKey, onlyMissingMatches), hu);
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
            session.commit();
          }
          del = session.getMapper(ArchivedNameUsageMatchMapper.class).deleteOrphans(datasetKey);
          if (del > 0) {
            LOG.info("Removed {} orphaned name archive matches for {}", del, datasetKey);
          }
          session.commit();
        }
      }
    } finally {
      if (bus != null) {
        bus.post(new FlushDatasetCache(datasetKey));
      }
    }
  }

  public int getDatasets() {
    return datasets;
  }

}
