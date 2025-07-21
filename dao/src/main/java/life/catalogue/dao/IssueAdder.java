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

public class IssueAdder implements AutoCloseable {
  private final int datasetKey;
  private final DSID<Integer> vkey;
  private final DSID<String> ukey;
  private final VerbatimSourceMapper vsm;
  private final NameUsageMapper um;
  private final SqlSession session;
  private final int start;
  private int vsIdGen;
  private boolean closed = false;

  public IssueAdder(int datasetKey, SqlSessionFactory factory) {
    this.session = factory.openSession(ExecutorType.BATCH, false);
    this.datasetKey = datasetKey;
    this.ukey = DSID.root(datasetKey);
    this.vkey = DSID.root(datasetKey);
    this.vsm = session.getMapper(VerbatimSourceMapper.class);
    this.um = session.getMapper(NameUsageMapper.class);
    vsIdGen = vsm.getMaxID(datasetKey)+1;
    start = vsIdGen;
  }

  public void addIssues(Integer verbatimSourceKey, String usageID, Set<Issue> issues) {
    if (verbatimSourceKey == null) {
      // create new record attached to used
      var vs = new VerbatimSource(datasetKey, vsIdGen++, null, null, null, null);
      vs.setIssues(issues);
      vsm.create(vs);
      um.updateVerbatimSourceKey(ukey.id(usageID), vs.getId());
      if (vsIdGen-start % 10_000 == 0) {
        session.commit();
      }
    } else {
      // reuse
      vsm.addIssues(vkey.id(verbatimSourceKey), issues);
    }
  }

  public void addIssue(Integer verbatimSourceKey, String usageID, Issue issue) {
    addIssues(verbatimSourceKey, usageID, Set.of(issue));
  }

  public void addIssue(String usageID, Issue issue) {
    var vsKey = vsm.getVSKeyByUsage(ukey.id(usageID));
    addIssue(vsKey, usageID, issue);
  }

  @Override
  public void close() {
    if (!closed) {
      session.commit();
      session.close();
    }
    closed = true;
  }
}
