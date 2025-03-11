package life.catalogue.matching;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.DSIDValue;
import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.cache.CacheLoader;
import life.catalogue.cache.UsageCache;
import life.catalogue.db.mapper.NameUsageMapper;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MatchingStorageGlobalCache implements MatchingStorage {
  private final static Logger LOG = LoggerFactory.getLogger(MatchingStorageGlobalCache.class);

  private final SqlSessionFactory factory;
  private final UsageCache uCache;
  private final CacheLoader defaultLoader;
  private final Map<Integer, CacheLoader> loaders = new HashMap<>();
  // key = datasetKey + canonical nidx
  private final LoadingCache<DSID<Integer>, List<SimpleNameCached>> usages = Caffeine.newBuilder()
    .maximumSize(100_000)
    .build(this::loadUsagesByNidx);

  public MatchingStorageGlobalCache(SqlSessionFactory factory, UsageCache uCache) {
    this.factory = factory;
    this.uCache = uCache;
    this.defaultLoader = new CacheLoader.MybatisLoader(factory);
  }

  /**
   * @param nidx a names index id wrapped by a datasetKey
   * @return list of matching usages for the requested dataset only
   */
  private List<SimpleNameCached> loadUsagesByNidx(@NonNull DSID<Integer> nidx) {
    try (SqlSession session = factory.openSession(true)) {
      var result = session.getMapper(NameUsageMapper.class).listByCanonNIDX(nidx.getDatasetKey(), nidx.getId());
      // avoid empty lists which get cached
      return result == null || result.isEmpty() ? null : result;
    }
  }


  /**
   * Registers a usage loader for the specific dataset to be used instead of the default one which opens a new database session each time
   * @param datasetKey
   * @param loader
   */
  public void registerLoader(int datasetKey, CacheLoader loader) {
    LOG.info("Registering new usage loader for dataset {}: {}", datasetKey, loader.getClass());
    loaders.put(datasetKey, loader);
  }

  public void removeLoader(int datasetKey) {
    LOG.info("Remove usage loader for dataset {}", datasetKey);
    loaders.remove(datasetKey);
  }


  @Override
  public void start() throws Exception {

  }

  @Override
  public void stop() throws Exception {

  }

  @Override
  public boolean hasStarted() {
    return false;
  }

  @Override
  public void invalidate(int datasetKey, int canonicalId) {
    usages.invalidate(new MatchingService.CanonNidxMatch(datasetKey, canonicalId, MatchType.EXACT));
  }

  @Override
  public void invalidate(DSID<Integer> key) {

  }

  @Override
  public NameUsageBase getUsage(DSID<String> src) {
    try (SqlSession session = factory.openSession()) {
      return session.getMapper(NameUsageMapper.class).get(src);
    }
  }

  @Override
  public List<SimpleNameCached> get(DSID<Integer> canonNidx) {
    return usages.get(canonNidx);
  }

  @Override
  public void put(DSID<Integer> canonNidx, List<SimpleNameCached> before) {

  }

  @Override
  public List<SimpleNameCached> getClassification(DSID<String> src) {
    return uCache.getClassification(src, loaders.getOrDefault(src.getDatasetKey(), defaultLoader));
  }

  /**
   * Removes a single entry from the matcher cache.
   * If it is not cached yet, nothing will happen.
   * @param nidx any names index id
   */
  public void clear(int datasetKey, int nidx) {
//    var n = nameIndex.get(nidx);
//    if (n != null) {
//      if (n.getCanonicalId() != null && !n.isCanonical()) {
//        nidx = n.getCanonicalId();
//      }
//      storage.invalidate(DSID.of(datasetKey, nidx));
//    }
  }

  /**
   * Removes all usages from the given dataset from the matcher cache.
   */
  public void clear(int datasetKey) {
    int count = 0;
    for (var k : usages.asMap().keySet()) {
      if (datasetKey == k.getDatasetKey()) {
        usages.invalidate(k);
        count++;
      }
    }
    LOG.info("Cleared all {} usages for datasetKey {} from the cache", count, datasetKey);
  }

  /**
   * Wipes the entire cache.
   */
  public void clear() {
    usages.invalidateAll();
    uCache.clear();
    LOG.warn("Cleared entire cache");
  }
}
