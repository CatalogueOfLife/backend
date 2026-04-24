package life.catalogue.matching;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameClassified;

import java.util.*;
import java.util.function.Function;

import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.db.mapper.NameUsageMapper;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Does not store or cache anything, but reads directly from Postgres
 * relying on external code to keep the DB in sync with the source of truth.
 *
 * Supports two modes:
 * - Session mode: a pre-opened SqlSession is provided (e.g. by SyncFactory to expose uncommitted
 *   batch data). The session lifecycle is entirely the caller's responsibility — it is never closed here.
 * - Factory mode: a SqlSessionFactory is provided. A fresh session is opened and immediately
 *   closed for each top-level operation, so no connection is held between calls.
 *
 * Note that all iterators are unsupported.
 */
public class UsageMatcherPgStore implements UsageMatcherStore {
  private final int datasetKey;
  private final SqlSessionFactory sessionFactory; // factory mode
  private final NameUsageMapper num;              // non-null only in session mode
  private final DSID<String> key;

  /** Session mode: uses the given session for all queries. Session lifecycle is the caller's responsibility. */
  public UsageMatcherPgStore(int datasetKey, SqlSession session) {
    this.datasetKey = datasetKey;
    this.sessionFactory = null;
    this.key = DSID.root(datasetKey);
    this.num = session.getMapper(NameUsageMapper.class);
  }

  /** Factory mode: opens and closes a fresh session per top-level operation. */
  public UsageMatcherPgStore(int datasetKey, SqlSessionFactory sessionFactory) {
    this.datasetKey = datasetKey;
    this.sessionFactory = sessionFactory;
    this.key = DSID.root(datasetKey);
    this.num = null;
  }

  private <T> T withMapper(Function<NameUsageMapper, T> action) {
    if (sessionFactory != null) {
      try (var s = sessionFactory.openSession()) {
        return action.apply(s.getMapper(NameUsageMapper.class));
      }
    }
    return action.apply(num);
  }

  @Override
  public int datasetKey() {
    return datasetKey;
  }

  @Override
  public int size() {
    return -1;
  }

  @Override
  public List<SimpleNameClassified<SimpleNameCached>> usagesByCanonicalId(int canonId) {
    return withMapper(m -> {
      var sns = m.listByCanonNIDX(datasetKey, canonId);
      if (sns == null) return null;
      var list = new ArrayList<SimpleNameClassified<SimpleNameCached>>(sns.size());
      for (SimpleNameCached sn : sns) {
        list.add(new SimpleNameClassified<>(sn, buildClassification(m, sn.getParentId())));
      }
      return list;
    });
  }

  private List<SimpleNameCached> buildClassification(NameUsageMapper m, String parentId) {
    var cl = new ArrayList<SimpleNameCached>();
    var visited = new HashSet<String>();
    var cur = parentId;
    while (cur != null && visited.add(cur)) {
      var p = m.getSimpleCached(key.id(cur));
      if (p == null) break;
      cl.add(p);
      cur = p.getParent();
    }
    return cl;
  }

  @Override
  public List<SimpleNameCached> simpleNamesByCanonicalId(int canonId) {
    return withMapper(m -> m.listByCanonNIDX(datasetKey, canonId));
  }

  @Override
  public SimpleNameCached get(String usageID) throws NotFoundException {
    return withMapper(m -> m.getSimpleCached(key.id(usageID)));
  }

  @Override
  public Iterable<SimpleNameCached> all() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Iterable<Integer> allCanonicalIds() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void update(String usageID, TaxGroup group) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void add(SimpleNameCached sn) {
    // nothing cached — straight db
  }

  @Override
  public void updateParentId(String usageID, String parentId) {
    // nothing cached — straight db
  }

  @Override
  public void close() {
    // session lifecycle is managed by the caller in session mode; nothing to close in factory mode
  }
}
