package life.catalogue.dao;

import com.google.common.base.Preconditions;
import com.google.common.eventbus.Subscribe;
import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.db.mapper.DatasetMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache for Immutable dataset infos that is loaded on demand and never release as the data is immutable
 * and we do not have large amounts of datasets that do not fit into memory.
 *
 * All methods throw a NotFoundException in case the datasetKey does not exist or refers to a deleted dataset.
 * We use the GuavaBus to listen to newly deleted datasets.
 */
public class DatasetInfoCache {
  private SqlSessionFactory factory;
  private final Map<Integer, DatasetInfo> infos = new ConcurrentHashMap<>();
  private final Set<Integer> deleted = ConcurrentHashMap.newKeySet();

  public final static DatasetInfoCache CACHE = new DatasetInfoCache();

  private DatasetInfoCache() { }

  public void setFactory(SqlSessionFactory factory) {
    this.factory = factory;
  }

  public static class DatasetInfo {
    public final int key;
    public final DatasetOrigin origin;
    public final Integer sourceKey;
    public final Integer importAttempt;

    DatasetInfo(int key, DatasetOrigin origin, Integer sourceKey, Integer importAttempt) {
      this.key = key;
      this.origin = Preconditions.checkNotNull(origin, "origin is required");
      this.sourceKey = sourceKey;
      this.importAttempt = importAttempt;
      if (origin == DatasetOrigin.RELEASED) {
        Preconditions.checkNotNull(sourceKey, "sourceKey is required for release " + key);
      }
    }
  }

  private DatasetInfo get(int datasetKey) throws NotFoundException {
    if (deleted.contains(datasetKey)) {
      throw NotFoundException.notFound(Dataset.class, datasetKey);
    }
    return infos.computeIfAbsent(datasetKey, key -> {
      try (SqlSession session = factory.openSession()) {
        return convert(datasetKey, session.getMapper(DatasetMapper.class).get(key));
      }
    });
  }

  private DatasetInfo convert(int key, Dataset d) {
    if (d != null) {
      if (!d.hasDeletedDate()) {
        return new DatasetInfo(key, d.getOrigin(), d.getSourceKey(), d.getImportAttempt());
      }
      deleted.add(key);
    }
    throw NotFoundException.notFound(Dataset.class, key);
  }

  public DatasetInfo info(int datasetKey) throws NotFoundException {
    return get(datasetKey);
  }

  public DatasetOrigin origin(int datasetKey) throws NotFoundException {
    return get(datasetKey).origin;
  }

  public Integer sourceProject(int datasetKey) throws NotFoundException {
    return get(datasetKey).sourceKey;
  }

  public Integer importAttempt(int datasetKey) throws NotFoundException {
    return get(datasetKey).importAttempt;
  }

  /**
   * Makes sure the dataset key exists and is not deleted.
   * @param datasetKey
   * @throws NotFoundException
   */
  public void exists(int datasetKey) throws NotFoundException {
    get(datasetKey);
  }

  @Subscribe
  public void datasetChanged(DatasetChanged event){
    if (event.isDeletion()) {
      deleted.add(event.key);
      infos.remove(event.key);
    }
  }

}
