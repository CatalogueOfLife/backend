package life.catalogue.cache;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Pool;

import life.catalogue.api.model.HasID;
import life.catalogue.common.kryo.map.MapDbObjectSerializer;

import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.mapdb.*;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

/**
 * ObjectCache implementation that is backed by a mapdb using kryo serialization.
 * Creating a new cache will always wipe any existing data and not reuse it.
 */
public class ObjectCacheRocksDB<T extends HasID<String>> implements ObjectCache<T> {
  private static final Logger LOG = LoggerFactory.getLogger(ObjectCacheRocksDB.class);

  private final int bufferSize;
  private final Pool<Kryo> pool;
  private final File dbFile;
  private final RocksDB db;
  private final Class<T> clazz;
  private final byte[] containsDummy = new byte[8];

  /**
   * @param location the directory for storing the values
   */
  public ObjectCacheRocksDB(Class<T> clazz, File location, Pool<Kryo> kryoPool) throws IOException, RocksDBException {
    // a static method that loads the RocksDB C++ library.
    RocksDB.loadLibrary();
    this.bufferSize = 128;
    this.clazz = clazz;
    this.dbFile = location;
    this.pool = kryoPool;
    if (location.exists()) {
      LOG.info("Delete existing {} cache at {}", clazz.getSimpleName(), location.getAbsolutePath());
      location.delete();
    } else {
      FileUtils.forceMkdirParent(location);
    }
    LOG.info("Create persistent {} cache at {}", clazz.getSimpleName(), location.getAbsolutePath());

    // the Options class contains a set of configurable DB options
    // that determines the behaviour of the database.
    var options = new Options().setCreateIfMissing(true);
    db = RocksDB.open(options, location.getAbsolutePath());
  }

  @Override
  public void close() {
    try {
      if (db != null) {
        db.close();
      }
    } finally {
      FileUtils.deleteQuietly(dbFile);
    }
  }

  private static byte[] key(String key){
    return key.getBytes(StandardCharsets.UTF_8);
  }

  private byte[] write(T value){
    Kryo kryo = pool.obtain();
    try {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream(bufferSize);
      Output output = new Output(buffer, bufferSize);
      kryo.writeObject(output, value);
      output.close();
      //TODO: do we need to track the length of bytes?
      return buffer.toByteArray();
    } finally {
      pool.free(kryo);
    }
  }

  private T read(byte[] bytes){
    Kryo kryo = pool.obtain();
    try {
      return kryo.readObject(new Input(bytes), clazz);
    } finally {
      pool.free(kryo);
    }
  }

  @Override
  public boolean contains(String id) {
    try {
      int size = db.get(key(id), containsDummy);
      return size > 0;
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public T get(String id) {
    try {
      return read(db.get(key(id)));
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void put(T obj) {
    try {
      db.put(key(obj.getId()), write(obj));
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void remove(String id) {
    try {
      db.delete(key(id));
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public int size() {
    //TODO: impl somehow
    return -1;
  }

  @NotNull
  @Override
  public Iterator<T> iterator() {
    //TODO: impl somehow
    return null;
  }
}
