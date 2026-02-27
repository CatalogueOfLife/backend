package life.catalogue.cache;

import life.catalogue.api.model.HasID;
import life.catalogue.common.kryo.map.MapDbObjectSerializer;

import java.io.File;
import java.io.IOException;
import java.util.*;

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
  private final boolean reuse;

  /**
   * @param location the db file for storing the values
   * @param reuse if true the cache will open existing data and keep it on the filesystem when closed.
   *              if false a new cache will be created every time and files will be deleted on close.
   */
  public ObjectCacheMapDB(Class<T> clazz, File location, Pool<Kryo> kryoPool, boolean reuse) throws IOException {
    this.reuse = reuse;
    this.clazz = clazz;
    this.dbFile = location;
    this.pool = kryoPool;
    if (location.exists()) {
      if (!reuse) {
        LOG.info("Delete existing {} cache at {}", clazz.getSimpleName(), location.getAbsolutePath());
        location.delete();
      }
    } else {
      FileUtils.forceMkdirParent(location);
    }
    if (location.exists() && reuse) {
      LOG.info("Open existing {} cache at {}", clazz.getSimpleName(), location.getAbsolutePath());
    } else {
      LOG.info("Create persistent {} cache at {}", clazz.getSimpleName(), location.getAbsolutePath());
    }
    db = DBMaker
      .fileDB(location)
      .fileMmapEnableIfSupported()
      .make();
    var builder = db.hashMap("objects")
      .keySerializer(Serializer.STRING)
      .valueSerializer(new MapDbObjectSerializer<>(clazz, pool, 128))
      .counterEnable();
    map = reuse ? builder.createOrOpen() : builder.create();
  }

  @Override
  public void close() {
    try {
      if (db != null) {
        db.close();
      }
    } finally {
      if (reuse) {
        LOG.info("Closing, but keeping persistent {} cache at {}", clazz.getSimpleName(), dbFile.getAbsolutePath());
      } else {
        LOG.info("Deleting persistent {} cache at {}", clazz.getSimpleName(), dbFile.getAbsolutePath());
        if (!FileUtils.deleteQuietly(dbFile)) {
          LOG.warn("Failed to delete {}", dbFile.getAbsolutePath());
        }
      }
    }
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
  public void put(T obj) {
    map.put(obj.getId(), obj);
  }

  @Override
  public void remove(String id) {
    map.remove(id);
  }

  @Override
  public int size() {
    return map.size();
  }

  @NotNull
  @Override
  public Iterator<T> iterator() {
    return map.values().iterator();
  }

  @Override
  public List<T> list() {
    return new ArrayList<>(map.values());
  }
}
