package life.catalogue.matching;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameWithNidx;
import life.catalogue.db.mapper.NameUsageMapper;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * No cache involved but always queries postgres directly
 */
public class MatchingStoragePgDirect implements MatchingStorage<SimpleNameCached> {

  private final SqlSessionFactory factory;

  public MatchingStoragePgDirect(SqlSessionFactory factory) {
    this.factory = factory;
  }

  @Override
  public void clear(DSID<Integer> key) {  }

  @Override
  public SimpleNameCached convert(NameUsageBase nu, DSID<Integer> canonNidx) {
    return new SimpleNameCached(nu, canonNidx.getId());
  }

  @Override
  public List<SimpleNameCached> get(DSID<Integer> canonNidx) {
    try (SqlSession session = factory.openSession(true)) {
      var result = session.getMapper(NameUsageMapper.class).listByCanonNIDX(canonNidx.getDatasetKey(), canonNidx.getId());
      // avoid empty lists which get cached
      return result == null || result.isEmpty() ? null : result;
    }
  }

  @Override
  public void put(DSID<Integer> canonNidx, List<SimpleNameCached> before) {  }

  @Override
  public List<SimpleNameCached> getClassification(DSID<String> key) {
    try (SqlSession session = factory.openSession(true)) {
      List<SimpleNameWithNidx> result = session.getMapper(NameUsageMapper.class).classificationNxIds(key);
      return result.stream()
        .map(SimpleNameCached::new)
        .collect(Collectors.toList());
    }
  }

  /**
   * Removes all usages from the given dataset from the matcher cache.
   */
  public void clear(int datasetKey) {  }

  /**
   * Wipes the entire cache.
   */
  public void clear() {  }
}
