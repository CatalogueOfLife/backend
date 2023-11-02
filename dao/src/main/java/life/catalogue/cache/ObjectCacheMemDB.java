package life.catalogue.cache;

import life.catalogue.api.model.HasID;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.rocksdb.RocksDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Pool;

/**
 * ObjectCache implementation that is backed by a mapdb using kryo serialization.
 * Creating a new cache will always wipe any existing data and not reuse it.
 */
public class ObjectCacheMemDB<T extends HasID<String>> implements ObjectCache<T> {
  private static final Logger LOG = LoggerFactory.getLogger(ObjectCacheMemDB.class);

  private final int bufferSize;
  private final Pool<Kryo> pool;
  private final Class<T> clazz;
  private final Map<String, byte[]> db;

  public ObjectCacheMemDB(Class<T> clazz, Pool<Kryo> kryoPool) {
    // a static method that loads the RocksDB C++ library.
    RocksDB.loadLibrary();
    this.bufferSize = 128;
    this.clazz = clazz;
    this.pool = kryoPool;
    this.db = new HashMap<>();
  }

  @Override
  public void close() {
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
    return db.containsKey(id);
  }

  @Override
  public T get(String id) {
    return read(db.get(id));
  }

  @Override
  public void put(T obj) {
    db.put(obj.getId(), write(obj));
  }

  @Override
  public void remove(String id) {
    db.remove(id);
  }

  @Override
  public int size() {
    return db.size();
  }

  @NotNull
  @Override
  public Iterator<T> iterator() {
    return null;
  }
}
