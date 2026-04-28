package life.catalogue.junit;

import life.catalogue.api.model.DatasetSimple;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.mapper.DatasetMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.rules.ExternalResource;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * JUnit rule that injects a mocked {@link SqlSessionFactory} into the {@link DatasetInfoCache#CACHE}
 * singleton so {@code DatasetInfoCache.info(key)} calls don't hit a real database during unit tests.
 *
 * Tests can pre-register {@link DatasetSimple} entries via {@link #put(int, DatasetOrigin, Integer)}
 * (or the more detailed {@link #put(DatasetSimple)}). Lookups for unknown keys return null, which makes
 * {@code DatasetInfoCache.convert} throw {@code NotFoundException} — register every key the test exercises.
 *
 * The rule clears the cache and restores the previous factory on teardown so tests don't leak state.
 */
public class DatasetInfoCacheMockRule extends ExternalResource {

  private final Map<Integer, DatasetSimple> datasets = new HashMap<>();
  private SqlSessionFactory factory;
  private SqlSession session;
  private DatasetMapper mapper;

  /** Register a dataset by key, origin and optional sourceKey (for releases). */
  public DatasetInfoCacheMockRule put(int key, DatasetOrigin origin, Integer sourceKey) {
    DatasetSimple d = new DatasetSimple();
    d.setKey(key);
    d.setOrigin(origin);
    d.setSourceKey(sourceKey);
    return put(d);
  }

  /** Register a dataset by key, origin, sourceKey and gbifPublisherKey. */
  public DatasetInfoCacheMockRule put(int key, DatasetOrigin origin, Integer sourceKey, UUID gbifPublisherKey) {
    DatasetSimple d = new DatasetSimple();
    d.setKey(key);
    d.setOrigin(origin);
    d.setSourceKey(sourceKey);
    d.setGbifPublisherKey(gbifPublisherKey);
    return put(d);
  }

  /** Register a fully populated DatasetSimple. */
  public DatasetInfoCacheMockRule put(DatasetSimple d) {
    datasets.put(d.getKey(), d);
    if (mapper != null) {
      when(mapper.getSimple(d.getKey())).thenReturn(d);
    }
    return this;
  }

  /** @return the mocked factory injected into DatasetInfoCache.CACHE. */
  public SqlSessionFactory getFactory() {
    return factory;
  }

  @Override
  protected void before() {
    factory = mock(SqlSessionFactory.class);
    session = mock(SqlSession.class);
    mapper = mock(DatasetMapper.class);
    when(factory.openSession()).thenReturn(session);
    when(session.getMapper(DatasetMapper.class)).thenReturn(mapper);
    when(mapper.getSimple(anyInt())).thenAnswer(inv -> datasets.get((Integer) inv.getArgument(0)));
    // clear so previous test's entries don't leak, then inject the mocked factory
    DatasetInfoCache.CACHE.clear();
    DatasetInfoCache.CACHE.setFactory(factory);
  }

  @Override
  protected void after() {
    DatasetInfoCache.CACHE.clear();
  }
}
