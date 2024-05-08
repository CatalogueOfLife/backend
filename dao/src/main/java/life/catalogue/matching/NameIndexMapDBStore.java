package life.catalogue.matching;

import com.google.common.base.Function;

import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.model.IndexName;
import life.catalogue.common.kryo.FastUtilsSerializers;
import life.catalogue.common.kryo.map.MapDbObjectSerializer;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.ExAuthorship;
import org.gbif.nameparser.api.Rank;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.commons.lang3.ArrayUtils;
import org.mapdb.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;
import com.google.common.base.Preconditions;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * NameIndexStore implementation that is backed by a mapdb using kryo serialization.
 */
public class NameIndexMapDBStore implements NameIndexStore {
  private static final Logger LOG = LoggerFactory.getLogger(NameIndexMapDBStore.class);

  private File dbFile;
  private final DBMaker.Maker dbMaker;
  private final Pool<Kryo> pool;
  private DB db;
  private Atomic.Long created; //datetime
  // main nidx instances by their key
  private Map<Integer, IndexName> keys; // main nidx instances by their key
  private Map<String, int[]> names; // group of same names by their canonical name key
  private Map<Integer, int[]> canonical; // canonical group of names by canonicalID

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
      kryo.register(ExAuthorship.class);
      kryo.register(Rank.class);
      kryo.register(LocalDateTime.class);
      kryo.register(ArrayList.class);
      kryo.register(HashMap.class);
      kryo.register(HashSet.class);
      kryo.register(int[].class);
      kryo.register(ObjectArrayList.class, new FastUtilsSerializers.ArrayListSerializer());
      try {
        kryo.register(Class.forName("java.util.Arrays$ArrayList"));
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
      return kryo;
    }
  }
  
  public NameIndexMapDBStore(DBMaker.Maker dbMaker) throws DBException.DataCorruption {
    this(dbMaker, null);
  }

  /**
   * @param dbMaker
   * @param dbFile the db file if the maker creates a file based db. Slightly defeats the purpose, but we wanna deal with corrupted db files
   */
  public NameIndexMapDBStore(DBMaker.Maker dbMaker, @Nullable File dbFile) {
    this.dbFile = dbFile;
    this.dbMaker = dbMaker;
    pool = new NameIndexKryoPool();
  }

  @Override
  public void start() {
    try {
      db = dbMaker.make();
    } catch (DBException.DataCorruption e) {
      if (dbFile != null) {
        LOG.warn("NamesIndex mapdb was corrupt. Remove and rebuild index from scratch. {}", e.getMessage());
        dbFile.delete();
        db = dbMaker.make();
      } else {
        throw e;
      }
    }

    final String dateName = "created";
    if (db.exists(dateName)) {
      created = db.atomicLong(dateName).open();
    } else {
      created = db.atomicLong(dateName).create();
      setCreatedToNow();
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
  public boolean hasStarted() {
    return db != null;
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
        .distinct()
        .boxed()
        .map(this::get)
        .collect(Collectors.toSet());
    }
    return null;
  }

  public int[] debugCanonical(Integer key) {
    return canonical.get(key);
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

  private void setCreatedToNow() {
    created.set(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
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

  @Override
  public List<IndexName> delete(int id, Function<IndexName, String> keyFunc) {
    List<IndexName> removed = new ArrayList<>();
    var n = keys.remove(id);
    removed.add(n);
    if (n != null) {
      final String key = keyFunc.apply(n);
      // remove all index names for a canonical?
      if (n.isCanonical()) {
        var cids = canonical.remove(id);
        if (cids != null) {
          for (var id2 : cids) {
            removed.addAll(delete(id2, keyFunc));
          }
        }
      } else {
        var cids = canonical.remove(n.getCanonicalId());
        if (cids != null) {
          canonical.put(n.getCanonicalId(), remove(cids, id));
        }
      }
      // update names group
      int[] group = remove(names.get(key), id);
      names.put(key, group);
    }
    return removed;
  }

  private static int[] remove(int[] ids, int id) {
    final int pos = ArrayUtils.indexOf(ids, id);
    if (pos != ArrayUtils.INDEX_NOT_FOUND) {
      return ArrayUtils.remove(ids, pos);
    }
    return ids;
  }

  /**
   * @param key make sure this is a pure ASCII key, no chars above 7 bits allowed !!!
   */
  @Override
  public void add(String key, IndexName name) {
    avail();
    check(name);

    LOG.debug("Insert {}{} #{} keyed on >{}<", name.isCanonical() ? "canonical ":"", name.getLabelWithRank(), name.getKey(), key);
    keys.put(name.getKey(), name);

    // update names group
    int[] group;
    if (names.containsKey(key)) {
      group = names.get(key);
      // remove previous version if it already existed.
      final int pos = ArrayUtils.indexOf(group, name.getKey());
      if (pos != ArrayUtils.INDEX_NOT_FOUND) {
        group = ArrayUtils.remove(group, pos);
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
        if (!ArrayUtils.contains(group, name.getKey())) {
          group = ArrayUtils.add(group, name.getKey());
          canonical.put(name.getCanonicalId(), group);
        }
      } else {
        canonical.put(name.getCanonicalId(), new int[]{name.getKey()});
      }
    }
  }

  @Override
  public void compact() {
    for (var entry : canonical.entrySet()) {
      IntSet set = new IntOpenHashSet(entry.getValue());
      canonical.put(entry.getKey(), set.toIntArray());
    }
  }

  @Override
  public LocalDateTime created() {
    return LocalDateTime.ofEpochSecond(created.get(), 0, ZoneOffset.UTC);
  }

  void check(IndexName n){
    Preconditions.checkNotNull(n.getKey(), "key required");
    Preconditions.checkNotNull(n.getCanonicalId(), "canonicalID required");
    Preconditions.checkNotNull(n.getRank(), "rank required");
    Preconditions.checkNotNull(n.getScientificName(), "scientificName required");
  }

  private void avail() throws UnavailableException {
    if (db == null) throw UnavailableException.unavailable("names index");
  }
}
