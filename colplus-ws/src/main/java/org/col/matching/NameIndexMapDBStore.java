package org.col.matching;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.pool.KryoPool;
import org.col.api.model.Name;
import org.col.common.kryo.ApiKryoFactory;
import org.col.common.kryo.map.MapDbObjectSerializer;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NameIndexStore implementation that is backed by a mapdb using kryo serialization.
 */
public class NameIndexMapDBStore implements NameIndexStore {
  private static final Logger LOG = LoggerFactory.getLogger(NameIndexMapDBStore.class);
  
  private final DB db;
  private final KryoPool pool;
  private final Map<String, NameList> names;
  
  static class NameList extends ArrayList<Name> {
    NameList() {
      super(1);
    }
    
    NameList(int initialCapacity) {
      super(initialCapacity);
    }
    
    NameList(ArrayList<Name> names) {
      super(names);
    }
  }
  
  static class NameIndexKryoFactory extends ApiKryoFactory {
    @Override
    public Kryo create() {
      Kryo kryo = super.create();
      kryo.register(NameList.class);
      return kryo;
    }
  }
  
  public NameIndexMapDBStore(DBMaker.Maker dbMaker) {
      this.db = dbMaker.make();
      pool = new KryoPool.Builder(new NameIndexKryoFactory())
          .softReferences()
          .build();
      names = db.hashMap("names")
          .keySerializer(Serializer.STRING_ASCII)
          .valueSerializer(new MapDbObjectSerializer<>(NameList.class, pool, 128))
          //.counterEnable()
          //.valueInline()
          //.valuesOutsideNodesEnable()
          .createOrOpen();
    }
  
  
  @Override
  public int count() {
    AtomicInteger counter = new AtomicInteger(0);
    names.values().forEach(vl -> {
      counter.addAndGet(vl.size());
    });
    return counter.get();
  }
  
  @Override
  public ArrayList<Name> get(String key) {
    return names.get(key);
  }
  
  @Override
  public boolean containsKey(String key) {
    return names.containsKey(key);
  }
  
  @Override
  public void put(String key, ArrayList<Name> group) {
    names.put(key, new NameList(group));
  }
  
  @Override
  public void close() throws Exception {
    db.close();
  }
  
}
