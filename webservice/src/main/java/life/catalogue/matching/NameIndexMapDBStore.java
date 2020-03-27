package life.catalogue.matching;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;
import life.catalogue.api.model.Name;
import life.catalogue.common.kryo.ApiKryoPool;
import life.catalogue.common.kryo.map.MapDbObjectSerializer;
import org.mapdb.DB;
import org.mapdb.DBException;
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
  private final Pool<Kryo> pool;
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
  
  static class NameIndexKryoPool extends ApiKryoPool {

    public NameIndexKryoPool(int maximumCapacity) {
      super(maximumCapacity);
    }

    @Override
    public Kryo create() {
      Kryo kryo = super.create();
      kryo.register(NameList.class);
      return kryo;
    }
  }
  
  public NameIndexMapDBStore(DBMaker.Maker dbMaker) throws DBException.DataCorruption {
      this.db = dbMaker.make();
      pool = new NameIndexKryoPool(4);
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
