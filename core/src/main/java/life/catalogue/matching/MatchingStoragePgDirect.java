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
  private final int datasetKey;

  public MatchingStoragePgDirect(SqlSessionFactory factory, int datasetKey) {
    this.factory = factory;
    this.datasetKey = datasetKey;
  }

  @Override
  public int datasetKey() {
    return datasetKey;
  }

  @Override
  public SimpleNameCached convert(NameUsageBase nu, Integer canonNidx) {
    return new SimpleNameCached(nu, canonNidx);
  }

  @Override
  public void clear(int canonNidx) {
    // we don't cache anything
  }

  @Override
  public List<SimpleNameCached> get(int canonNidx) {
    try (SqlSession session = factory.openSession(true)) {
      var result = session.getMapper(NameUsageMapper.class).listByCanonNIDX(datasetKey, canonNidx);
      // avoid empty lists which get cached
      return result == null || result.isEmpty() ? null : result;
    }
  }

  @Override
  public void put(int canonNidx, List<SimpleNameCached> before) {
    // we don't cache anything
  }

  @Override
  public List<SimpleNameCached> getClassification(String key) {
    try (SqlSession session = factory.openSession(true)) {
      List<SimpleNameWithNidx> result = session.getMapper(NameUsageMapper.class).classificationNxIds(DSID.of(datasetKey, key));
      return result.stream()
        .map(SimpleNameCached::new)
        .collect(Collectors.toList());
    }
  }

  @Override
  public void clear(String usageKey) {
    // we don't cache anything
  }

}
