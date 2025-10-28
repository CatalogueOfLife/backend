package life.catalogue.dao;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.VerbatimSource;
import life.catalogue.api.vocab.Issue;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.VerbatimSourceMapper;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Convenience class for adding issues to project or releases.
 * It manages missing verbatim source records under the hood.
 *
 * As it relies on a serial verbatim source id generator looking at the current maximum at startup,
 * do not use this class in parallel for the same dataset!
 */
public class IssueAdder {
  private final int datasetKey;
  private final DSID<Integer> vkey;
  private final DSID<String> ukey;
  private final VerbatimSourceMapper vsm;
  private final NameUsageMapper um;
  private AtomicInteger vsIdGen;

  public IssueAdder(int datasetKey, SqlSession session) {
    DaoUtils.requireProjectOrRelease(datasetKey);
    this.datasetKey = datasetKey;
    this.ukey = DSID.root(datasetKey);
    this.vkey = DSID.root(datasetKey);
    this.vsm = session.getMapper(VerbatimSourceMapper.class);
    this.um = session.getMapper(NameUsageMapper.class);
    vsIdGen = new AtomicInteger(vsm.getMaxID(datasetKey)+1);
  }

  public void addIssues(Integer verbatimSourceKey, String usageID, Set<Issue> issues) {
    if (verbatimSourceKey == null) {
      // create new record attached to used
      var vs = new VerbatimSource(datasetKey, vsIdGen.incrementAndGet(), null, null, null, null);
      vs.setIssues(issues);
      vsm.create(vs);
      um.updateVerbatimSourceKey(ukey.id(usageID), vs.getId());
    } else {
      // reuse
      vsm.addIssues(vkey.id(verbatimSourceKey), issues);
    }
  }

  public void addIssue(Integer verbatimSourceKey, String usageID, Issue issue) {
    addIssues(verbatimSourceKey, usageID, Set.of(issue));
  }

  public void addIssue(String usageID, Issue issue) {
    addIssues(usageID, Set.of(issue));
  }

  public void addIssues(String usageID, Set<Issue> issues) {
    var vsKey = vsm.getVSKeyByUsage(ukey.id(usageID));
    addIssues(vsKey, usageID, issues);
  }
}
