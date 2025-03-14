package life.catalogue.matching;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.cache.ClassificationCacheCaffein;

import life.catalogue.db.mapper.NameUsageMapper;

import org.apache.ibatis.session.SqlSession;

import java.util.List;

public class MatchingStoragePgCache implements MatchingStorage<SimpleNameCached> {
  private final int datasetKey;
  private final SqlSession session;
  private final NameUsageMapper num;
  private final ClassificationCacheCaffein clCache;
  private final LoadingCache<Integer, List<SimpleNameCached>> byCanonNidx;

  public MatchingStoragePgCache(SqlSession session, int datasetKey, int maxSize) {
    this.session = session;
    this.num = session.getMapper(NameUsageMapper.class);
    this.datasetKey = datasetKey;
    this.clCache = new ClassificationCacheCaffein(datasetKey, session, maxSize);
    this.byCanonNidx = Caffeine.newBuilder()
      .maximumSize(maxSize)
      .build(this::loadUsagesByCanonNidx);
  }

  /**
   * @param canonNidx a names index id wrapped by a datasetKey
   * @return list of matching usages for the requested dataset only
   */
  private List<SimpleNameCached> loadUsagesByCanonNidx(Integer canonNidx) {
    var result = num.listByCanonNIDX(datasetKey, canonNidx);
    // avoid empty lists which get cached
    return result == null || result.isEmpty() ? null : result;
  }

  @Override
  public int datasetKey() {
    return datasetKey;
  }

  @Override
  public List<SimpleNameCached> get(int canonNidx) {
    return byCanonNidx.get(canonNidx);
  }

  @Override
  public void put(int canonNidx, List<SimpleNameCached> usages) {
    byCanonNidx.put(canonNidx, usages);
  }

  @Override
  public List<SimpleNameCached> getClassification(String usage) {
    return clCache.getClassification(usage);
  }

  @Override
  public void clear(String usageKey) {
    clCache.clear(usageKey);
  }

  @Override
  public SimpleNameCached convert(NameUsageBase nu, Integer canonNidx) {
    return new SimpleNameCached(nu, canonNidx);
  }

  @Override
  public void clear(int canonNidx) {
    byCanonNidx.refresh(canonNidx);
  }

}
