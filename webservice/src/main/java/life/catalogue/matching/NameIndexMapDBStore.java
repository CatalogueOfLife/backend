package life.catalogue.matching;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;
import life.catalogue.api.model.IndexName;
import life.catalogue.common.kryo.ApiKryoPool;
import life.catalogue.common.kryo.map.MapDbObjectSerializer;
import org.mapdb.DB;
import org.mapdb.DBException;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * NameIndexStore implementation that is backed by a mapdb using kryo serialization.
 */
public class NameIndexMapDBStore implements NameIndexStore {
  private static final Logger LOG = LoggerFactory.getLogger(NameIndexMapDBStore.class);

  private File dbFIle;
  private final DBMaker.Maker dbMaker;
  private final Pool<Kryo> pool;
  private DB db;
  private Map<String, NameList> names;

  static class NameList extends ArrayList<IndexName> {
    NameList() {
      super(1);
    }
    
    NameList(int initialCapacity) {
      super(initialCapacity);
    }
    
    NameList(ArrayList<IndexName> names) {
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
    this(dbMaker, null);
  }

  /**
   * @param dbMaker
   * @param dbFIle the db file if the maker creates a file based db. Slightly defeats the purpose, but we wanna deal with coruppted db files
   */
  public NameIndexMapDBStore(DBMaker.Maker dbMaker, @Nullable File dbFIle) {
    this.dbFIle = dbFIle;
    this.dbMaker = dbMaker;
    pool = new NameIndexKryoPool(4);
  }

  @Override
  public void start() {
    try {
      db = dbMaker.make();
    } catch (DBException.DataCorruption e) {
      if (dbFIle != null) {
        LOG.warn("NamesIndex mapdb was corrupt. Remove and rebuild index from scratch. {}", e.getMessage());
        dbFIle.delete();
        db = dbMaker.make();
      } else {
        throw e;
      }
    }

    names = db.hashMap("names")
      .keySerializer(Serializer.STRING_ASCII)
      .valueSerializer(new MapDbObjectSerializer<>(NameList.class, pool, 128))
      //.counterEnable()
      //.valueInline()
      //.valuesOutsideNodesEnable()
      .createOrOpen();
  }


  @Override
  public void stop() {
    if (db != null) {
      db.close();
      db = null;
    }
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
  public ArrayList<IndexName> get(String key) {
    avail();
    return names.get(key);
  }
  
  @Override
  public boolean containsKey(String key) {
    avail();
    return names.containsKey(key);
  }
  
  @Override
  public void put(String key, ArrayList<IndexName> group) {
    avail();
    names.put(key, new NameList(group));
  }

  private void avail() throws UnavailableException {
    if (db == null) throw new UnavailableException("Names Index is offline");
  }
}
