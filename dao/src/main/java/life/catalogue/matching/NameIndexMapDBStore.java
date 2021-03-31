package life.catalogue.matching;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;
import com.google.common.base.Preconditions;
import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.model.IndexName;
import life.catalogue.common.kryo.map.MapDbObjectSerializer;
import org.apache.commons.lang3.ArrayUtils;
import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.Rank;
import org.mapdb.DB;
import org.mapdb.DBException;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * NameIndexStore implementation that is backed by a mapdb using kryo serialization.
 */
public class NameIndexMapDBStore implements NameIndexStore {
  private static final Logger LOG = LoggerFactory.getLogger(NameIndexMapDBStore.class);

  private File dbFIle;
  private final DBMaker.Maker dbMaker;
  private final Pool<Kryo> pool;
  private DB db;
  private Map<String, int[]> names;
  private Map<Integer, IndexName> keys;
  private Map<Integer, int[]> canonical;

  /**
   * We use a separate kryo pool for the names index to avoid too often changes to the serialisation format
   * that then requires us to rebuilt the names index mapdb file. Register just the needed classes, no more.
   */
  static class NameIndexKryoPool extends Pool<Kryo> {

    public NameIndexKryoPool() {
      super(true, true, 1024);
    }

    @Override
    public Kryo create() {
      Kryo kryo = new Kryo();
      kryo.setRegistrationRequired(true);
      kryo.register(IndexName.class);
      kryo.register(Authorship.class);
      kryo.register(Rank.class);
      kryo.register(LocalDateTime.class);
      kryo.register(ArrayList.class);
      kryo.register(HashMap.class);
      kryo.register(HashSet.class);
      kryo.register(int[].class);
      return kryo;
    }
  }
  
  public NameIndexMapDBStore(DBMaker.Maker dbMaker) throws DBException.DataCorruption {
    this(dbMaker, null);
  }

  /**
   * @param dbMaker
   * @param dbFIle the db file if the maker creates a file based db. Slightly defeats the purpose, but we wanna deal with corrupted db files
   */
  public NameIndexMapDBStore(DBMaker.Maker dbMaker, @Nullable File dbFIle) {
    this.dbFIle = dbFIle;
    this.dbMaker = dbMaker;
    pool = new NameIndexKryoPool();
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

    keys = db.hashMap("keys")
      .keySerializer(Serializer.INTEGER)
      .valueSerializer(new MapDbObjectSerializer<>(IndexName.class, pool, 128))
      .counterEnable()
      //.valueInline()
      //.valuesOutsideNodesEnable()
      .createOrOpen();
    names = db.hashMap("names")
      .keySerializer(Serializer.STRING_ASCII)
      .valueSerializer(Serializer.INT_ARRAY)
      //.valueInline()
      //.valuesOutsideNodesEnable()
      .createOrOpen();
    canonical = db.hashMap("canonical")
      .keySerializer(Serializer.INTEGER)
      .valueSerializer(Serializer.INT_ARRAY)
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
  public IndexName get(Integer key) {
    avail();
    return keys.get(key);
  }

  @Override
  public Collection<IndexName> byCanonical(Integer key) {
    if (canonical.containsKey(key)) {
      return Arrays.stream(canonical.get(key))
        .boxed()
        .map(this::get)
        .collect(Collectors.toList());
    }
    return null;
  }

  @Override
  public Iterable<IndexName> all() {
    return keys.values();
  }

  @Override
  public int count() {
    return keys.size();
  }

  @Override
  public void clear() {
    keys.clear();
    names.clear();
  }

  @Override
  public List<IndexName> get(String key) {
    avail();
    List<IndexName> matches = new ArrayList<>();
    if (names.containsKey(key)) {
      for (int k : names.get(key)) {
        matches.add(keys.get(k));
      }
    }
    return matches;
  }
  
  @Override
  public boolean containsKey(String key) {
    avail();
    return names.containsKey(key);
  }

  /**
   * @param key make sure this is a pure ASCII key, no chars above 7 bits allowed !!!
   */
  @Override
  public void add(String key, IndexName name) {
    avail();
    check(name);

    keys.put(name.getKey(), name);

    // update names
    int[] group;
    if (names.containsKey(key)) {
      group = names.get(key);
      // remove previous version if it already existed.
      final int index = ArrayUtils.indexOf(group, name.getKey());
      if (index != ArrayUtils.INDEX_NOT_FOUND) {
        group = ArrayUtils.remove(group, index);
      }
      group = ArrayUtils.add(group, name.getKey());
    } else {
      group = new int[]{name.getKey()};
    }
    names.put(key, group);

    // update canonical
    if (name.getCanonicalId() != null && !name.getCanonicalId().equals(name.getKey())) {
      if (canonical.containsKey(name.getCanonicalId())) {
        group = canonical.get(name.getCanonicalId());
        group = ArrayUtils.add(group, name.getKey());
      } else {
        group = new int[]{name.getKey()};
      }
      canonical.put(name.getCanonicalId(), group);
    }
  }

  void check(IndexName n){
    Preconditions.checkNotNull(n.getKey());
  }

  private void avail() throws UnavailableException {
    if (db == null) throw UnavailableException.unavailable("names index");
  }
}
