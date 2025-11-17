package life.catalogue.cache;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.kryo.map.MapDbObjectSerializer;

import java.io.File;
import java.util.Map;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;

/**
 * UsageCache implementation that is backed by a mapdb using kryo serialization,
 * but only supports a single dataset which optionally can be fully preloaded!
 */
public class UsageCacheMapDB implements UsageCache {
  private static final Logger LOG = LoggerFactory.getLogger(UsageCacheMapDB.class);

  private final DBMaker.Maker dbMaker;
  private final Pool<Kryo> pool;
  private final int datasetKey;
  private Map<String, SimpleNameCached> usages;
  private DB db;

  /**
   *
   * @param datasetKey
   * @param location the db file for storing the values
   * @param kryoMaxCapacity kryo pool size
   */
  public UsageCacheMapDB(int datasetKey, File location, int kryoMaxCapacity) {
    LOG.info("Use persistent usage cache for dataset {} at {}", datasetKey, location.getAbsolutePath());
    this.datasetKey = datasetKey;
    dbMaker = DBMaker
      .fileDB(location)
      .fileMmapEnableIfSupported();
    db = dbMaker.make();
    pool = new UsageCacheKryoPool(kryoMaxCapacity);
    usages = db.hashMap("usages")
      .keySerializer(Serializer.STRING)
      .valueSerializer(new MapDbObjectSerializer<>(SimpleNameCached.class, pool, 128))
      .createOrOpen();
  }

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
      kryo.register(TaxGroup.class);
      return kryo;
    }
  }

  public int size() {
    return usages.size();
  }

  @Override
  public void close() {
    if (db != null) {
      db.close();
      db = null;
    }
  }

  @Override
  public boolean contains(String key) {
    return usages.containsKey(key);
  }

  @Override
  public SimpleNameCached get(String key) {
    return usages.get(key);
  }

  @Override
  public SimpleNameCached put(SimpleNameCached usage) {
    return usages.put(usage.getId(), usage);
  }

  @Override
  public SimpleNameCached remove(String key) {
    return usages.remove(key);
  }

  /**
   * Removes all cached content.
   */
  @Override
  public void clear() {
    usages.clear();
  }

  @Override
  public int getDatasetKey() {
    return datasetKey;
  }
}
