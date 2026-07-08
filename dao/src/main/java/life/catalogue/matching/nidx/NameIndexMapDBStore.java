package life.catalogue.matching.nidx;

import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.model.IndexName;
import life.catalogue.common.kryo.map.MapDbObjectSerializer;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

import javax.annotation.Nullable;

import org.mapdb.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;

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
  private Map<String, Integer> names; // single-tier: the one names index key for a canonical name bucket

  public NameIndexMapDBStore(DBMaker.Maker dbMaker, int poolSize) throws DBException.DataCorruption {
    this(dbMaker, null, poolSize);
  }

  /**
   * @param dbMaker
   * @param dbFile the db file if the maker creates a file based db. Slightly defeats the purpose, but we wanna deal with corrupted db files
   */
  public NameIndexMapDBStore(DBMaker.Maker dbMaker, @Nullable File dbFile, int poolSize) {
    this.dbFile = dbFile;
    this.dbMaker = dbMaker;
    pool = new NameIndexKryoPool(poolSize);
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
      .valueSerializer(Serializer.INTEGER)
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
    // single-tier index: every entry is its own canonical, there are no qualified child entries
    return Collections.emptyList();
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
    // mutable list: callers (e.g. NameIndexImpl.match/getCanonical) remove() / removeIf() on it
    List<IndexName> matches = new ArrayList<>();
    Integer k = names.get(key);
    if (k != null) {
      matches.add(keys.get(k));
    }
    return matches;
  }
  
  @Override
  public boolean containsKey(String key) {
    avail();
    return names.containsKey(key);
  }

  @Override
  public int maxKey() {
    return keys.keySet().stream().mapToInt(v -> v).max().orElse(0);
  }

  @Override
  public List<IndexName> delete(int id, Function<IndexName, String> keyFunc) {
    List<IndexName> removed = new ArrayList<>();
    var n = keys.remove(id);
    removed.add(n);
    if (n != null) {
      final String key = keyFunc.apply(n);
      // single-tier index: every entry is its own canonical, so there are no qualified child
      // entries to cascade-remove here anymore.
      // only clear the bucket if it still points at the entry being deleted
      Integer cur = names.get(key);
      if (cur != null && cur == id) {
        names.remove(key);
      }
    }
    return removed;
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

    // single-tier index: add() is only ever called after getCanonical(key) found nothing, so the
    // bucket should be empty here. Warn (don't fail) if that invariant is ever violated.
    if (names.containsKey(key)) {
      LOG.warn("Names index bucket >{}< already had key {} - overwriting with new key {}", key, names.get(key), name.getKey());
    }
    names.put(key, name.getKey());
  }

  @Override
  public void compact() {
    // single-tier index: the canonical->children multimap that used to need compacting is gone;
    // nothing left to compact.
  }

  @Override
  public LocalDateTime created() {
    return LocalDateTime.ofEpochSecond(created.get(), 0, ZoneOffset.UTC);
  }

  @Override
  public Pool<Kryo> kryo() {
    return pool;
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
