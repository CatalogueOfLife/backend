package life.catalogue.matching;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameClassified;

import java.util.*;
import java.util.stream.Collectors;

import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.db.mapper.NameUsageMapper;

import org.apache.ibatis.session.SqlSession;

/**
 * Does not store or cache anything, but reads directly from Postgres
 * relying on external code to keep the DB in sync with the source of truth.
 *
 * Note that the all iterators are unsupported.
 */
public class UsageMatcherPgStore implements UsageMatcherStore {
  private final int datasetKey;
  private final boolean closeSession;
  private final SqlSession session;
  private final NameUsageMapper num;
  private final DSID<String> key;

  public UsageMatcherPgStore(int datasetKey, SqlSession session, boolean closeSession) {
    this.datasetKey = datasetKey;
    this.closeSession = closeSession;
    this.key = DSID.root(datasetKey);
    this.session = session;
    this.num = session.getMapper(NameUsageMapper.class);
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
    var sns = num.listByCanonNIDX(datasetKey, canonId);
    if (sns != null) {
      List<SimpleNameClassified<SimpleNameCached>> list = new ArrayList<>(sns.size());
      for (SimpleNameCached sn : sns) {
        var cl = getClassification(sn.getParentId());
        list.add(new SimpleNameClassified<>(sn, cl));
      }
      return list;
    }
    return null;
  }

  @Override
  public List<SimpleNameCached> simpleNamesByCanonicalId(int canonId) {
    return num.listByCanonNIDX(datasetKey, canonId);
  }

  @Override
  public SimpleNameCached get(String usageID) throws NotFoundException {
    return num.getSimpleCached(key.id(usageID));
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
    // nothing cached - straight db
  }

  @Override
  public void updateUsageID(String oldID, String newID) {
    // nothing cached - straight db
  }

  @Override
  public void updateParentId(String usageID, String parentId) {
    // nothing cached - straight db
  }

  @Override
  public void close() {
    if (closeSession) {
      session.close();
    }
  }

}
