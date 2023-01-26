package life.catalogue.cache;

import life.catalogue.api.model.HasID;
import life.catalogue.common.kryo.map.MapDbObjectSerializer;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;

/**
 * ObjectCache implementation that is backed by a mapdb using kryo serialization.
 * Creating a new cache will always wipe any existing data and not reuse it.
 */
public class ObjectCacheMapDB<T extends HasID<String>> implements ObjectCache<T> {
  private static final Logger LOG = LoggerFactory.getLogger(ObjectCacheMapDB.class);

  private final Pool<Kryo> pool;
  private final File dbFile;
  private final DB db;
  private final Map<String, T> map;
  private final Class<T> clazz;

  /**
   * @param location the db file for storing the values
   */
  public ObjectCacheMapDB(Class<T> clazz, File location, Pool<Kryo> kryoPool) throws IOException {
    this.clazz = clazz;
    this.dbFile = location;
    this.pool = kryoPool;
    if (location.exists()) {
      LOG.info("Delete existing cache at {}", location.getAbsolutePath());
      location.delete();
    } else {
      FileUtils.forceMkdirParent(location);
    }
    LOG.info("Create persistent usage cache at {}", location.getAbsolutePath());
    db = DBMaker
      .fileDB(location)
      .fileMmapEnableIfSupported()
      .make();
    map = db.hashMap("objects")
            .keySerializer(Serializer.STRING)
            .valueSerializer(new MapDbObjectSerializer<>(clazz, pool, 128))
            .counterEnable()
            .create();
  }

  @Override
  public void close() {
    if (db != null) {
      db.close();
    }
    dbFile.delete();
  }


  @Override
  public boolean contains(String id) {
    return map.containsKey(id);
  }

  @Override
  public T get(String id) {
    return map.get(id);
  }

  @Override
  public T put(T obj) {
    return map.put(obj.getId(), obj);
  }

  @Override
  public T remove(String id) {
    return map.remove(id);
  }

  @Override
  public int size() {
    return map.size();
  }

  @Override
  public void clear() {
    LOG.warn("Clearing entire {} cache with {} objects", clazz.getSimpleName(), map.size());
    map.clear();
  }

  @NotNull
  @Override
  public Iterator<T> iterator() {
    return map.values().iterator();
  }
}
