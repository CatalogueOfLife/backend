package life.catalogue.cache;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.kryo.map.MapDbObjectSerializer;
import life.catalogue.dao.DatasetInfoCache;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;

import it.unimi.dsi.fastutil.ints.*;

/**
 * UsageCache implementation that is backed by a mapdb using kryo serialization.
 * For each dataset key a separate mapdb is used that can be cleared or warmed.
 */
public class UsageCacheMapDB implements UsageCache {
  private static final Logger LOG = LoggerFactory.getLogger(UsageCacheMapDB.class);

  // make this one thread safe
  private final Int2ObjectMap<Map<String, SimpleNameCached>> datasets = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());
  private final DBMaker.Maker dbMaker;
  private final Pool<Kryo> pool;
  private final File dbFile;
  private final boolean expireMutable;
  private DB db;

  /**
   * We use a separate kryo pool for the usage cache to avoid too often changes to the serialisation format
   * that then requires us to rebuilt the mapdb file. Register just the needed classes, no more.
   *
   * The cache implements both AutoCloseable and Managed if used in the DW application to shutdown mapdb nicely.
   */
  static class UsageCacheKryoPool extends Pool<Kryo> {

    public UsageCacheKryoPool(int maxCapacity) {
      super(true, true, maxCapacity);
    }

    @Override
    public Kryo create() {
      Kryo kryo = new Kryo();
      kryo.setRegistrationRequired(true);
      kryo.register(DSID.class);
      kryo.register(SimpleNameCached.class);
      kryo.register(Rank.class);
      kryo.register(MatchType.class);
      kryo.register(TaxonomicStatus.class);
      kryo.register(NomCode.class);
      return kryo;
    }
  }

  /**
   * @param location the db file for storing the values
   * @param expireMutable if true requires mybatis to be setup and the DatasetInfoCache is used to know when mutable datasets should be expired soon (1h?)
   */
  public UsageCacheMapDB(File location, boolean expireMutable, int kryoMaxCapacity) throws IOException {
    this.dbFile = location;
    this.expireMutable = expireMutable;
    LOG.info("Create persistent usage cache at {}", location.getAbsolutePath());
    if (!location.exists()) {
      FileUtils.forceMkdirParent(location);
    } else {
      FileUtils.deleteQuietly(location);
    }
    this.dbMaker = DBMaker
      .fileDB(location)
      .fileMmapEnableIfSupported();
    pool = new UsageCacheKryoPool(kryoMaxCapacity);
  }

  @Override
  public void start() {
    if (dbFile.exists()) {
      FileUtils.deleteQuietly(dbFile);
    }
    db = dbMaker.make();
    LOG.info("UsageCache started with empty cache: {}", dbFile);
  }

  @Override
  public void stop() {
    datasets.clear();
    if (db != null) {
      db.close();
      db = null;
    }
  }

  @Override
  public boolean hasStarted() {
    return db != null;
  }

  @Override
  public void close() {
    try {
      stop();
    } finally {
      FileUtils.deleteQuietly(dbFile);
    }
  }

  private static String dbname(int datasetKey) {
    return "u"+datasetKey;
  }

  private DB.HashMapMaker<String, SimpleNameCached> storeMaker(int datasetKey, boolean expireMutable) {
    String dbname = dbname(datasetKey);
    var maker = db.hashMap(dbname)
             .keySerializer(Serializer.STRING)
             .valueSerializer(new MapDbObjectSerializer<>(SimpleNameCached.class, pool, 128))
             //.counterEnable()
             //.valueInline()
             //.valuesOutsideNodesEnable()
             ;
    if (expireMutable && DatasetInfoCache.CACHE.info(datasetKey).isMutable()) {
      maker.expireAfterCreate(1, TimeUnit.HOURS);
    }
    return maker;
  }

  @Override
  public boolean contains(DSID<String> key) {
    int dkey = key.getDatasetKey();
    return datasets.containsKey(dkey) && datasets.get(dkey).containsKey(key.getId());
  }

  @Override
  public SimpleNameCached get(DSID<String> key) {
    int dkey = key.getDatasetKey();
    if (datasets.containsKey(dkey)) {
      return datasets.get(dkey).get(key.getId());
    }
    return null;
  }

  @Override
  public SimpleNameCached put(int datasetKey, SimpleNameCached usage) {
    if (usage == null || usage.getId() == null) {
      throw new IllegalArgumentException("Usage ID required");
    }
    var store = datasets.computeIfAbsent(datasetKey, (dk) -> {
      LOG.info("Adding usage cache for dataset {}", datasetKey);
      var map = storeMaker(datasetKey, expireMutable).createOrOpen();
      // we create or open the map store in case the same dataset key was used on disk before and clear the map instead
      map.clear();
      return map;
    });
    return store.put(usage.getId(), usage);
  }

  @Override
  public SimpleNameCached remove(DSID<String> key) {
    int dkey = key.getDatasetKey();
    if (datasets.containsKey(dkey)) {
      return datasets.get(dkey).remove(key.getId());
    }
    return null;
  }

  /**
   * Removes all cached content for a specific dataset.
   */
  @Override
  public void clear(int datasetKey) {
    if (datasets.containsKey(datasetKey)) {
      // MapDB lacks a method to delete a named object in v3.0.9 so we remove all records instead !!!
      datasets.get(datasetKey).clear();
      LOG.info("Cleared all usages for datasetKey {} from the cache", datasetKey);
    }
  }

  /**
   * Removes all cached content.
   */
  @Override
  public void clear() {
    stop();
    LOG.warn("Clearing entire usage cache with {} datasets", datasets.size());
    dbFile.delete();
    start();
  }

  /**
   * @return set of all datasets that are being cached.
   */
  public IntSet listDatasets() {
    return new IntOpenHashSet(datasets.keySet());
  }
}
