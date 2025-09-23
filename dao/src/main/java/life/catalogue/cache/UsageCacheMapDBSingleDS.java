package life.catalogue.cache;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.common.kryo.map.MapDbObjectSerializer;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.NameUsageMapper;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mapdb.Atomic;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;

/**
 * UsageCache implementation that is backed by a mapdb using kryo serialization,
 * but only supports a single dataset which is fully preloaded!
 */
public class UsageCacheMapDBSingleDS implements UsageCache {
  private static final Logger LOG = LoggerFactory.getLogger(UsageCacheMapDBSingleDS.class);

  private final DBMaker.Maker dbMaker;
  private final Pool<Kryo> pool;
  private Map<String, SimpleNameCached> usages;
  private Atomic.Integer datasetKeyAtomic;
  private int datasetKey;
  private DB db;

  public static UsageCacheMapDBSingleDS open(File location, int kryoMaxCapacity) throws IOException {
    return new UsageCacheMapDBSingleDS(location, kryoMaxCapacity);
  }

  public static UsageCacheMapDBSingleDS createStarted(File location, int kryoMaxCapacity, int datasetKey, SqlSessionFactory factory) throws IOException {
    UsageCacheMapDBSingleDS cache = new UsageCacheMapDBSingleDS(location, kryoMaxCapacity);
    cache.start();
    cache.datasetKeyAtomic.set(datasetKey);
    cache.datasetKey = datasetKey;
    // load data
    LOG.info("Load all usages of dataset {} into cache", datasetKey);
    try (SqlSession session = factory.openSession()) {
      PgUtils.consume(() -> session.getMapper(NameUsageMapper.class).processDatasetSimpleNidx(datasetKey), cache::put);
    }
    LOG.info("Loades {} usages of dataset {} into cache", cache.size(), datasetKey);
    return cache;
  }

  /**
   * @param location the db file for storing the values
   */
  public UsageCacheMapDBSingleDS(File location, int kryoMaxCapacity) throws IOException {
    LOG.info("Use persistent usage cache at {}", location.getAbsolutePath());
    dbMaker = DBMaker
      .fileDB(location)
      .fileMmapEnableIfSupported();
    pool = new UsageCacheMapDB.UsageCacheKryoPool(kryoMaxCapacity);
  }

  @Override
  public void start() {
    db = dbMaker.make();
    usages = db.hashMap("usages")
      .keySerializer(Serializer.STRING)
      .valueSerializer(new MapDbObjectSerializer<>(SimpleNameCached.class, pool, 128))
      .createOrOpen();
    datasetKeyAtomic = db.atomicInteger("datasetKey").createOrOpen();
    datasetKey = datasetKeyAtomic.get();
  }

  @Override
  public void stop() {
    if (db != null) {
      db.close();
      db = null;
    }
  }

  @Override
  public boolean hasStarted() {
    return db != null;
  }

  public int size() {
    return usages.size();
  }

  @Override
  public void close() {
    stop();
  }

  @Override
  public void updateParent(int datasetKey, String oldParentId, String newParentId) {
    for (var u : usages.values()) {
      if (u.getParent() != null && u.getParent().equals(oldParentId)) {
        u.setParent(newParentId);
      }
    }
  }

  private String extractID(DSID<String> key) {
    if (key.getDatasetKey() == datasetKey) {
      return key.getId();
    }
    throw new IllegalArgumentException("Dataset "+key.getDatasetKey()+" is not supported - this cache only stores usages for dataset "+datasetKey);
  }

  @Override
  public boolean contains(DSID<String> key) {
    return usages.containsKey(extractID(key));
  }

  @Override
  public SimpleNameCached get(DSID<String> key) {
    return usages.get(extractID(key));
  }

  @Override
  public SimpleNameCached put(int datasetKey, SimpleNameCached usage) {
    throw new UnsupportedOperationException("This cache is immutable");
  }

  private void put(SimpleNameCached usage) {
    usages.put(usage.getId(), usage);
  }

  @Override
  public SimpleNameCached remove(DSID<String> key) {
    throw new UnsupportedOperationException("This cache is immutable");
  }

  @Override
  public void clear(int datasetKey) {
    throw new UnsupportedOperationException("This cache is immutable");
  }

  /**
   * Removes all cached content.
   */
  @Override
  public void clear() {
    throw new UnsupportedOperationException("This cache is immutable");
  }

  public int getDatasetKey() {
    return datasetKey;
  }
}
