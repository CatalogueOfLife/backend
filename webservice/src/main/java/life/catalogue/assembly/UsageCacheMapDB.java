package life.catalogue.assembly;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleNameWithPub;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.Managed;
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
import org.mapdb.DBException;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * UsageCache implementation that is backed by a mapdb using kryo serialization.
 * For each dataset key a separate mapdb is used that can be cleared or warmed.
 */
public class UsageCacheMapDB implements UsageCache, Managed {
  private static final Logger LOG = LoggerFactory.getLogger(UsageCacheMapDB.class);

  private final Int2ObjectMap<Map<String, SimpleNameWithPub>> datasets = new Int2ObjectOpenHashMap<>();
  private final DBMaker.Maker dbMaker;
  private final Pool<Kryo> pool;
  private final File dbFile;
  private final boolean expireMutable;
  private DB db;

  /**
   * We use a separate kryo pool for the usage cache to avoid too often changes to the serialisation format
   * that then requires us to rebuilt the mapdb file. Register just the needed classes, no more.
   */
  static class UsageCacheKryoPool extends Pool<Kryo> {

    public UsageCacheKryoPool() {
      super(true, true, 128);
    }

    @Override
    public Kryo create() {
      Kryo kryo = new Kryo();
      kryo.setRegistrationRequired(true);
      kryo.register(DSID.class);
      kryo.register(SimpleNameWithPub.class);
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
  public UsageCacheMapDB(File location, boolean expireMutable) throws IOException {
    this.dbFile = location;
    this.expireMutable = expireMutable;
    if (!location.exists()) {
      FileUtils.forceMkdirParent(location);
      LOG.info("Create persistent usage cache at {}", location.getAbsolutePath());
    } else {
      LOG.info("Use persistent usage cache at {}", location.getAbsolutePath());
    }
    this.dbMaker = DBMaker
      .fileDB(location)
      .fileMmapEnableIfSupported();
    pool = new UsageCacheKryoPool();
  }

  @Override
  public void start() {
    try {
      db = dbMaker.make();
    } catch (DBException.DataCorruption e) {
      if (dbFile != null) {
        LOG.warn("UsageCache mapdb was corrupt. Remove and start from scratch. {}", e.getMessage());
        dbFile.delete();
        db = dbMaker.make();
      } else {
        throw e;
      }
    }

    for (String dbname : db.getAllNames()) {
      int dkey = keyFromDbName(dbname);
      var store = storeMaker(dkey, expireMutable).open();
      datasets.put(dkey, store);
    }
    LOG.info("UsageCache opened with cache for {} datasets", datasets.size());
  }

  @Override
  public void stop() {
    if (db != null) {
      db.close();
      db = null;
    }
  }

  private static String dbname(int datasetKey) {
    return "u"+datasetKey;
  }
  private static int keyFromDbName(String dbname) {
    return Integer.parseInt(dbname.substring(1));
  }

  private DB.HashMapMaker<String, SimpleNameWithPub> storeMaker(int datasetKey, boolean expireMutable) {
    String dbname = dbname(datasetKey);
    var maker = db.hashMap(dbname)
             .keySerializer(Serializer.STRING)
             .valueSerializer(new MapDbObjectSerializer<>(SimpleNameWithPub.class, pool, 128))
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
  public SimpleNameWithPub get(DSID<String> key) {
    int dkey = key.getDatasetKey();
    if (datasets.containsKey(dkey)) {
      return datasets.get(dkey).get(key.getId());
    }
    return null;
  }

  @Override
  public SimpleNameWithPub put(int datasetKey, SimpleNameWithPub usage) {
    if (usage == null || usage.getId() == null) {
      throw new IllegalArgumentException("Usage ID required");
    }
    Map<String, SimpleNameWithPub> store;
    if (datasets.containsKey(datasetKey)) {
      store = datasets.get(datasetKey);
    } else {
      LOG.info("Creating new usage cache for dataset {}", datasetKey);
      store = storeMaker(datasetKey, expireMutable).create();
      datasets.put(datasetKey, store);
    }
    return store.put(usage.getId(), usage);
  }

  @Override
  public SimpleNameWithPub remove(DSID<String> key) {
    int dkey = key.getDatasetKey();
    if (datasets.containsKey(dkey)) {
      return datasets.get(dkey).remove(key.getId());
    }
    return null;
  }

  @Override
  public void clear(int datasetKey) {
    if (datasets.containsKey(datasetKey)) {
      datasets.get(datasetKey).clear();
    }
    LOG.info("Cleared all usages for datasetKey {} from the cache", datasetKey);
  }

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
