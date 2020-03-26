package life.catalogue.matching;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.db.mapper.NameMapper;
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
  public int match(int datasetKey, boolean allowInserts) {
    try (SqlSession session = factory.openSession(false)){
      NameMapper nm = session.getMapper(NameMapper.class);
      BulkMatchHandler h = new BulkMatchHandler(updateIssues, ni, factory, datasetKey, allowInserts);
      nm.processDataset(datasetKey).forEach(h);
      LOG.info("Updated {} out of {} name matches for dataset {}", h.updates, h.counter, datasetKey);
      return h.updates;
    }
  }
  
  
  static class BulkMatchHandler implements Consumer<Name>, AutoCloseable {
    int counter = 0;
    int updates = 0;
    private final boolean updateIssues;
    private final int datasetKey;
    private final boolean allowInserts;
    private final SqlSession session;
    private final NameIndex ni;
    private final NameMapper nm;
    private final VerbatimRecordMapper vm;
    private final DSIDValue<Integer> key;
  
    BulkMatchHandler(boolean updateIssues, NameIndex ni, SqlSessionFactory factory, int datasetKey, boolean allowInserts) {
      this.updateIssues = updateIssues;
      this.datasetKey = datasetKey;
      this.allowInserts = allowInserts;
      this.ni = ni;
      this.session = factory.openSession(ExecutorType.BATCH, false);
      this.nm = session.getMapper(NameMapper.class);
      this.vm = session.getMapper(VerbatimRecordMapper.class);
      key = DSID.key(datasetKey, -1);
    }
  
    @Override
    public void accept(Name n) {
      counter++;
      String oldId = n.getNameIndexId();
      NameMatch m = ni.match(n, allowInserts, false);
      
      if (!Objects.equals(oldId, m.hasMatch() ? m.getName().getId() : null)) {
        if (m.hasMatch()) {
          nm.updateMatch(datasetKey, n.getId(), m.getName().getId(), m.getType());
        } else {
          nm.updateMatch(datasetKey, n.getId(), null, null);
        }

        if (updateIssues) {
          IssueContainer v = n.getVerbatimKey() != null ? vm.getIssues(key.id(n.getVerbatimKey())) : null;
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
        
        if (updates++ % 10000 == 0) {
          session.commit();
          LOG.debug("Updated {} out of {} name matches for dataset {}", updates, counter, datasetKey);
        }
      }
    }
    
    static void clearMatchIssues(IssueContainer issues){
      issues.removeIssue(Issue.NAME_MATCH_NONE);
      issues.removeIssue(Issue.NAME_MATCH_AMBIGUOUS);
      issues.removeIssue(Issue.NAME_MATCH_VARIANT);
      issues.removeIssue(Issue.NAME_MATCH_INSERTED);
    }
  
    @Override
    public void close() throws Exception {
      session.commit();
      session.close();
    }
  }
}
