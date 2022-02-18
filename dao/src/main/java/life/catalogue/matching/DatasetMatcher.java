package life.catalogue.matching;

import life.catalogue.api.model.IndexName;
import life.catalogue.api.model.NameMatch;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.mapper.ArchivedNameMapper;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.NameMatchMapper;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rematches an entire dataset, using 2 separate db connections for read & write
 */
public class DatasetMatcher {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetMatcher.class);
  private final SqlSessionFactory factory;
  private final NameIndex ni;
  private int total = 0;
  private int updated = 0;
  private int nomatch = 0;
  private int archived = 0;
  private int datasets = 0;

  public DatasetMatcher(SqlSessionFactory factory, NameIndex ni) {
    this.factory = factory;
    this.ni = ni.assertOnline();
  }
  
  /**
   * Matches all names of an entire dataset and updates its name index id and issues in postgres
   * @param allowInserts if true allows inserts into the names index
   * @return number of names which have a changed match to before
   */
  public void match(int datasetKey, boolean allowInserts) {
    final int totalBefore = total;
    final int updatedBefore = updated;
    final int nomatchBefore = nomatch;
    final int archivedBefore = archived;

    boolean update = false;
    try (SqlSession session = factory.openSession(false);
         BulkMatchHandler h = new BulkMatchHandler(datasetKey, allowInserts)
    ) {
      NameMatchMapper nmm = session.getMapper(NameMatchMapper.class);
      NameMapper nm = session.getMapper(NameMapper.class);

      update = nmm.exists(datasetKey);
      final boolean isProject = DatasetInfoCache.CACHE.info(datasetKey).origin == DatasetOrigin.MANAGED;
      LOG.info("{} name matches for {}{}", update ? "Update" : "Create", isProject? "project ":"", datasetKey);
      nm.processDatasetWithNidx(datasetKey).forEach(h);
      // also match archived names
      if (isProject) {
        final int totalBeforeArchive = total;
        session.getMapper(ArchivedNameMapper.class).processDatasetWithNidx(datasetKey).forEach(h);
        archived = archived + total - totalBeforeArchive;
      }
    } catch (Exception e) {
      LOG.error("Failed to rematch dataset {}", datasetKey, e);
    } finally {
      datasets++;
      LOG.info("{} {} name matches for {} names and {} not matching, {} being archived names, for dataset {}", update ? "Updated" : "Created",
        updated-updatedBefore, total-totalBefore, nomatch-nomatchBefore, archived-archivedBefore, datasetKey);
    }

    try (SqlSession session = factory.openSession(false)) {
      if (update) {
        NameMatchMapper nmm = session.getMapper(NameMatchMapper.class);
        int del = nmm.deleteOrphaned(datasetKey);
        if (del > 0) {
          LOG.info("Removed {} orphaned name matches for {}", del, datasetKey);
        }
      }
    }
  }

  public int getTotal() {
    return total;
  }

  public int getUpdated() {
    return updated;
  }

  public int getNomatch() {
    return nomatch;
  }

  public int getDatasets() {
    return datasets;
  }

  class BulkMatchHandler implements Consumer<NameMapper.NameWithNidx>, AutoCloseable {
    private final int datasetKey;
    private final boolean allowInserts;
    private final SqlSession batchSession;
    private final NameMatchMapper nm;
    private int _total = 0;
    private int _updated = 0;
    private int _nomatch = 0;

    BulkMatchHandler(int datasetKey, boolean allowInserts) {
      this.datasetKey = datasetKey;
      this.allowInserts = allowInserts;
      this.batchSession = factory.openSession(ExecutorType.BATCH, false);
      this.nm = batchSession.getMapper(NameMatchMapper.class);
    }
  
    @Override
    public void accept(NameMapper.NameWithNidx n) {
      _total++;
      Integer oldId = n.namesIndexId;
      NameMatch m = ni.match(n, allowInserts, false);
      if (!m.hasMatch()) {
        _nomatch++;
        LOG.debug("No match for {} from dataset {} with {} alternatives: {}", n.toStringComplete(), datasetKey,
          m.getAlternatives() == null ? 0 : m.getAlternatives().size(),
          m.getAlternatives() == null ? "" : m.getAlternatives().stream().map(IndexName::getLabelWithRank).collect(Collectors.joining("; "))
        );
      }
      Integer newKey = m.hasMatch() ? m.getName().getKey() : null;
      if (!Objects.equals(oldId, newKey)) {
        if (oldId == null) {
          nm.create(n, n.getSectorKey(), newKey, m.getType());
        } else if (newKey != null){
          nm.update(n, newKey, m.getType());
        } else {
          nm.delete(n);
        }
        if (_updated++ % 10000 == 0) {
          batchSession.commit();
          LOG.debug("Updated {} name matches for {} names with {} no matches for dataset {}", _updated, _total, _nomatch, datasetKey);
        }
      }
    }

    @Override
    public void close() throws Exception {
      batchSession.commit();
      batchSession.close();
      total += _total;
      updated += _updated;
      nomatch += _nomatch;
    }
  }
}
