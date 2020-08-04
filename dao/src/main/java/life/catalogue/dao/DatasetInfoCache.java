package life.catalogue.dao;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.db.mapper.DatasetMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.Map;
import java.util.Optional;

/**
 * Cache for Immutable dataset infos that is loaded on demand and never release as the data is immutable
 * and we do not have large amounts of datasets that do not fit into memory.
 */
public class DatasetInfoCache {
  private final DatasetInfo EMPTY = new DatasetInfo(-1, null, null, null);
  private SqlSessionFactory factory;
  private final Map<Integer, DatasetInfo> infos = new Int2ObjectOpenHashMap<>();

  public final static DatasetInfoCache CACHE = new DatasetInfoCache();

  private DatasetInfoCache() { }

  public void setFactory(SqlSessionFactory factory) {
    this.factory = factory;
  }

  static class DatasetInfo {
    final int key;
    final DatasetOrigin origin;
    final Integer sourceKey;
    final Integer releaseAttempt;

    DatasetInfo(int key, DatasetOrigin origin, Integer sourceKey, Integer releaseAttempt) {
      this.key = key;
      this.origin = origin;
      this.sourceKey = sourceKey;
      this.releaseAttempt = releaseAttempt;
    }
  }

  private Optional<DatasetInfo> get(int datasetKey) {
    return Optional.ofNullable(infos.computeIfAbsent(datasetKey, key -> {
      try (SqlSession session = factory.openSession()) {
        DatasetInfo info = null;
        Dataset d = session.getMapper(DatasetMapper.class).get(key);
        if (d != null) {
          info = new DatasetInfo(d.getKey(), d.getOrigin(), d.getSourceKey(), 1);
        }
        return info;
      }
    }));
  }

  public DatasetOrigin origin(int datasetKey) throws NotFoundException {
    return get(datasetKey).orElse(EMPTY).origin;
  }

  public Integer sourceProject(int datasetKey) throws NotFoundException {
    return get(datasetKey).orElse(EMPTY).sourceKey;
  }

  public Integer releaseAttempt(int datasetKey) throws NotFoundException {
    return get(datasetKey).orElse(EMPTY).releaseAttempt;
  }
}
