package life.catalogue.matching;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.model.IndexName;
import life.catalogue.common.kryo.map.MapDbObjectSerializer;

import net.openhft.chronicle.map.ChronicleMap;

import net.openhft.chronicle.map.ChronicleMapBuilder;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.Rank;

import org.mapdb.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * NameIndexStore implementation that is backed by a persistent Chronicle map.
 */
public class NameIndexChronicleStore implements NameIndexStore {
  private static final Logger LOG = LoggerFactory.getLogger(NameIndexChronicleStore.class);

  private File dir;
  private final NamesIndexConfig cfg;
  private Atomic.Long created; //datetime
  // main nidx instances by their key
  private final File keysF;
  private final File namesF;
  private final File canonicalF;
  private ChronicleMap<Integer, IndexName> keys; // main nidx instances by their key
  private ChronicleMap<String, int[]> names; // group of same names by their canonical name key
  private ChronicleMap<Integer, int[]> canonical; // canonical group of names by canonicalID

  public NameIndexChronicleStore(NamesIndexConfig cfg) throws IOException {
    this.cfg = cfg;
    this.dir = cfg.file;

    if (dir == null) {
      keysF = null;
      namesF = null;
      canonicalF = null;
    } else {
      if (!dir.exists()) {
        FileUtils.forceMkdir(dir);
      }
      keysF = new File(dir, "keys");
      namesF = new File(dir, "names");
      canonicalF = new File(dir, "canonical");
    }
  }

  private boolean inMem() {
    return dir == null;
  }

  @Override
  public void start() {
    var idn = new IndexName();
    idn.setKey(345632);
    idn.setScientificName("Abies alba");
    idn.setAuthorship("Miller, 1988");
    idn.setCanonicalId(1345);
    idn.setRank(Rank.SPECIES);
    idn.setGenus("Abies");
    idn.setSpecificEpithet("alba");
    idn.setCombinationAuthorship(Authorship.yearAuthors("1988", "Miller"));

    var b1 = ChronicleMapBuilder.of(Integer.class, IndexName.class)
      .name("keys")
      .averageValue(idn)
      .entries(10_000_000);
    var b2 = ChronicleMapBuilder.of(String.class, int[].class)
      .name("names")
      .entries(12_000_000)
      .averageKey("Abies alba");
    var b3 = ChronicleMapBuilder.of(Integer.class, int[].class)
      .name("canonical")
      .entries(4_000_000);

    try {
      keys = inMem() ? b1.create() : b1.createPersistedTo(keysF);
      names = inMem() ? b2.create() : b2.createPersistedTo(namesF);
      canonical = inMem() ? b3.create() : b3.createPersistedTo(canonicalF);

    } catch (IOException e) {
      if (dir != null) {
        LOG.warn("NamesIndex store was corrupt. Remove and rebuild index from scratch. {}", e.getMessage());
        try {
          FileUtils.cleanDirectory(dir);
          keys = inMem() ? b1.create() : b1.createPersistedTo(keysF);
          names = inMem() ? b2.create() : b2.createPersistedTo(namesF);
          canonical = inMem() ? b3.create() : b3.createPersistedTo(canonicalF);
        } catch (IOException ex) {
          throw new RuntimeException(ex);
        }
      } else {
        throw new RuntimeException("Fatal exception when creating a new in memory nidx storage", e);
      }
    }
  }


  @Override
  public void stop() {
    keys.close();
    names.close();
    canonical.close();
  }

  @Override
  public boolean hasStarted() {
    return keys.isOpen();
  }

  @Override
  public IndexName get(Integer key) {
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
    canonical.clear();
  }

  private void setCreatedToNow() {
    created.set(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
  }

  @Override
  public List<IndexName> get(String key) {
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

}
