package life.catalogue.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.db.mapper.NameUsageMapper;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

public class ClassificationCacheCaffein implements ClassificationCache {
  private final NameUsageMapper mapper;
  private final int datasetKey;
  private final LoadingCache<String, SimpleNameCached> usages;

  public ClassificationCacheCaffein(int datasetKey, SqlSession session, int maxSize) {
    this.mapper = session.getMapper(NameUsageMapper.class);
    this.datasetKey = datasetKey;
    usages = Caffeine.newBuilder()
      .maximumSize(maxSize)
      .build(this::load);
  }

  private SimpleNameCached load(String id) {
    return mapper.getSimpleCached(DSID.of(datasetKey, id));
  }

  @Override
  public boolean contains(String key) {
    return usages.getIfPresent(key) != null;
  }

  @Override
  public SimpleNameCached get(String key) {
    return usages.get(key);
  }

  @Override
  public void put(SimpleNameCached usage) {
    usages.put(usage.getId(), usage);;
  }

  @Override
  public void clear(String key) {
    usages.invalidate(key);;
  }

  @Override
  public void close() {  }
}
