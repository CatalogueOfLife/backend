package life.catalogue.dao;

import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Origin;
import life.catalogue.db.mapper.DatasetMapper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.base.Preconditions;
import com.google.common.eventbus.Subscribe;

/**
 * Cache for Immutable dataset infos that is loaded on demand and never release as the data is immutable
 * and we do not have large amounts of datasets that do not fit into memory.
 *
 * All methods throw a NotFoundException in case the datasetKey does not exist or refers to a deleted dataset,
 * unless an optional allowDeleted parameter is given as true.
 * We use the GuavaBus to listen to newly deleted datasets.
 */
public class DatasetInfoCache {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetInfoCache.class);

  private SqlSessionFactory factory;
  private final Map<Integer, DatasetInfo> infos = new ConcurrentHashMap<>();
  // label = dataset title + version
  public final LoadingCache<Integer, String> labels = Caffeine.newBuilder()
                                                              .maximumSize(10000)
                                                              .expireAfterWrite(24, TimeUnit.HOURS)
                                                              .build(this::buildLabel);

  public final static DatasetInfoCache CACHE = new DatasetInfoCache();

  private DatasetInfoCache() { }

  public void setFactory(SqlSessionFactory factory) {
    this.factory = factory;
  }

  private String buildLabel(int key) {
    if (factory == null) {
      LOG.warn("No session factory created, cannot lookup dataset title for key {}", key);

    } else {
      try (SqlSession session = factory.openSession()) {
        var d = session.getMapper(DatasetMapper.class).get(key);
        if (d != null) {
          return d.getLabel();
        }
      }
    }
    return null;
  }

  public static class DatasetInfo {
    public final int key;
    public final DatasetOrigin origin;
    public final Integer sourceKey;
    public final UUID publisherKey;
    public final boolean deleted; // this can change, so we listen to deletion events. But once deleted it can never be reverted.

    DatasetInfo(int key, DatasetOrigin origin, Integer sourceKey, UUID publisherKey, boolean deleted) {
      this.key = key;
      this.origin = Preconditions.checkNotNull(origin, "origin is required");
      this.sourceKey = sourceKey;
      this.publisherKey = publisherKey;
      this.deleted = deleted;
      if (origin.isRelease()) {
        Preconditions.checkNotNull(sourceKey, "sourceKey is required for release " + key);
      }
    }

    public boolean isMutable(){
      return origin == DatasetOrigin.PROJECT;
    }

    public DatasetInfo requireOrigin(DatasetOrigin... origins){
      for (var o : origins) {
        if (o == this.origin) return this;
      }
      throw new IllegalArgumentException("Dataset "+key+" is not of origin " + Arrays.toString(origins));
    }

    @Override
    public String toString() {
      return "DS " + key +
        " origin=" + origin +
        " source=" + sourceKey;
    }
  }

  private DatasetInfo get(int datasetKey, boolean allowDeleted) throws NotFoundException {
    DatasetInfo info = infos.computeIfAbsent(datasetKey, key -> {
      try (SqlSession session = factory.openSession()) {
        return convert(datasetKey, session.getMapper(DatasetMapper.class).get(key));
      }
    });
    if (info.deleted && !allowDeleted) {
      throw NotFoundException.notFound(Dataset.class, datasetKey);
    }
    return info;
  }

  private DatasetInfo convert(int key, Dataset d) {
    if (d == null) {
      throw NotFoundException.notFound(Dataset.class, key);
    }
    return new DatasetInfo(key, d.getOrigin(), d.getSourceKey(), d.getGbifPublisherKey(), d.hasDeletedDate());
  }

  /**
   * Returns the dataset info for a given dataset key
   * @return the dataset info for a given dataset
   * @throws NotFoundException if dataset key does not exist or was deleted
   */
  public DatasetInfo info(int datasetKey) throws NotFoundException {
    return info(datasetKey, false);
  }

  /**
   * Returns the dataset info for a given dataset key
   * @return the dataset info for a given dataset
   * @param allowDeleted if true infos about deleted datasets are included and do not throw an exception
   * @throws NotFoundException if dataset key does not exist or was deleted if allowDeleted=false
   */
  public DatasetInfo info(int datasetKey, boolean allowDeleted) throws NotFoundException {
    return get(datasetKey, allowDeleted);
  }

  /**
   * Returns the dataset info for a given dataset key
   * and requires the origin to be of the given type.
   * @return the dataset info for a given dataset
   * @throws NotFoundException
   * @throws IllegalArgumentException if the datasetKey does not refer to the given origin
   */
  public DatasetInfo info(int datasetKey, DatasetOrigin... origin) throws NotFoundException, IllegalArgumentException {
    var info = info(datasetKey);
    if (origin != null) {
      Set<DatasetOrigin> origins = new HashSet<>();
      CollectionUtils.addAll(origins, origin);
      if (!origins.contains(info.origin)) {
        throw new IllegalArgumentException("Wrong origin " + info.origin + " of dataset " + datasetKey);
      }
    }
    return info;
  }

  /**
   * @return the source=project key for releases or the given key for all other origins
   * @throws NotFoundException
   */
  public int keyOrProjectKey(int datasetKey) throws NotFoundException {
    return keyOrProjectKey(get(datasetKey, true));
  }

  public int keyOrProjectKey(DatasetInfo info) throws NotFoundException {
    return info.origin.isRelease() ? info.sourceKey : info.key;
  }

  /**
   * @return the GBIF publisher key for the given dataset
   * @throws NotFoundException if dataset does not exist
   */
  public UUID publisher(int datasetKey) throws NotFoundException {
    return get(datasetKey, true).publisherKey;
  }

  /**
   * Makes sure the dataset key exists and is not deleted.
   * @param datasetKey
   * @throws NotFoundException
   */
  public void exists(int datasetKey) throws NotFoundException {
    get(datasetKey, false);
  }

  /**
   * Removes all entries from the cache.
   */
  public void clear() {
    infos.clear();
  }

  public int size() {
    return infos.size();
  }

  @Subscribe
  public void datasetChanged(DatasetChanged event){
    if (event.isDeletion()) {
      var info = get(event.key, true);
      // mark it as deleted in the cache
      infos.put(event.key, new DatasetInfo(info.key, info.origin, info.sourceKey, info.publisherKey, true));
    }
  }

}
