package life.catalogue.matching;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.DSIDValue;
import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.NameMatch;
import life.catalogue.api.vocab.Issue;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.NameMatchMapper;
import life.catalogue.db.mapper.VerbatimRecordMapper;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Consumer;

public class DatasetMatcher {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetMatcher.class);
  private final SqlSessionFactory factory;
  private final NameIndex ni;
  private final boolean updateIssues;
  private int total = 0;
  private int updated = 0;
  private int datasets = 0;

  /**
   * @param updateIssues if true also updates matching issues in the linked verbatim records
   */
  public DatasetMatcher(SqlSessionFactory factory, NameIndex ni, boolean updateIssues) {
    this.factory = factory;
    this.ni = ni;
    this.updateIssues = updateIssues;
  }
  
  /**
   * Matches all names of an entire dataset and updates its name index id and issues in postgres
   * @param allowInserts if true allows inserts into the names index
   * @return number of names which have a changed match to before
   */
  public void match(int datasetKey, boolean allowInserts) {
    int totalBefore = total;
    int updatedBefore = updated;

    try (SqlSession session = factory.openSession(false);
         BulkMatchHandler h = new BulkMatchHandler(datasetKey, allowInserts)
    ){
      NameMatchMapper nmm = session.getMapper(NameMatchMapper.class);
      NameMapper nm = session.getMapper(NameMapper.class);

      boolean update = nmm.exists(datasetKey);
      LOG.info("{} name matches for {}", update ? "Update" : "Create", datasetKey);
      nm.processDatasetWithNidx(datasetKey).forEach(h);
      LOG.info("Updated {} out of {} name matches for dataset {}", updated-updatedBefore, total-totalBefore, datasetKey);

      if (update) {
        int del = nmm.deleteOrphaned(datasetKey, null);
        LOG.info("Removed {} orphaned name matches for {}", del, datasetKey);
      }
      datasets++;

    } catch (Exception e) {
      LOG.error("Failed to rematch dataset {}", datasetKey, e);
    }
  }

  public int getTotal() {
    return total;
  }

  public int getUpdated() {
    return updated;
  }

  public int getDatasets() {
    return datasets;
  }

  class BulkMatchHandler implements Consumer<NameMapper.NameWithNidx>, AutoCloseable {
    private final int datasetKey;
    private final int updatedStart;
    private final int totalStart;
    private final boolean allowInserts;
    private final SqlSession batchSession;
    private final SqlSession session;
    private final NameMatchMapper nm;
    private final VerbatimRecordMapper vm;
    private final VerbatimRecordMapper vmGet;
    private final DSIDValue<Integer> key;
  
    BulkMatchHandler(int datasetKey, boolean allowInserts) {
      this.datasetKey = datasetKey;
      this.allowInserts = allowInserts;
      this.updatedStart = updated;
      this.totalStart = total;
      this.batchSession = factory.openSession(ExecutorType.BATCH, false);
      this.session = factory.openSession(false);
      this.nm = batchSession.getMapper(NameMatchMapper.class);
      this.vm = batchSession.getMapper(VerbatimRecordMapper.class);
      this.vmGet = session.getMapper(VerbatimRecordMapper.class);
      key = DSID.of(datasetKey, -1);
    }
  
    @Override
    public void accept(NameMapper.NameWithNidx n) {
      total++;
      Integer oldId = n.namesIndexId;
      NameMatch m = ni.match(n, allowInserts, false);

      Integer newKey = m.hasMatch() ? m.getName().getKey() : null;
      if (!Objects.equals(oldId, newKey)) {
        if (oldId == null) {
          nm.create(n, n.getSectorKey(), newKey, m.getType());
        } else if (newKey != null){
          nm.update(n, newKey, m.getType());
        } else {
          nm.delete(n);
        }
        if (updateIssues) {
          IssueContainer v = n.getVerbatimKey() != null ? vmGet.getIssues(key.id(n.getVerbatimKey())) : null;
          if (v != null) {
            int hash = v.getIssues().hashCode();
            clearMatchIssues(v);
            if (m.hasMatch()) {
              if (m.getType().issue != null) {
                v.addIssue(m.getType().issue);
              }
            } else {
              v.addIssue(Issue.NAME_MATCH_NONE);
            }
            // only update verbatim if issues changed
            if (hash != v.getIssues().hashCode()) {
              vm.update(key, v.getIssues());
            }
          }
        }
        
        if ( (updated++ - updatedStart) % 10000 == 0) {
          batchSession.commit();
          LOG.debug("Updated {} out of {} name matches for dataset {}", updated - updatedStart, total-totalStart, datasetKey);
        }
      }
    }
    
    void clearMatchIssues(IssueContainer issues){
      issues.removeIssue(Issue.NAME_MATCH_NONE);
      issues.removeIssue(Issue.NAME_MATCH_AMBIGUOUS);
      issues.removeIssue(Issue.NAME_MATCH_VARIANT);
      issues.removeIssue(Issue.NAME_MATCH_INSERTED);
    }
  
    @Override
    public void close() throws Exception {
      batchSession.commit();
      batchSession.close();
      session.close();
    }
  }
}
