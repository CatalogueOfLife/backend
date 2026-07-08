package life.catalogue.matching.nidx;

import life.catalogue.api.model.IndexName;
import life.catalogue.common.kryo.Pools;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.Rank;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import jakarta.validation.constraints.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Pool;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.hash.serialization.BytesReader;
import net.openhft.chronicle.hash.serialization.BytesWriter;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.map.ChronicleMapBuilder;

/**
 * NameIndexStore implementation that is backed by a persistent Chronicle map.
 */
public class NameIndexChronicleStore implements NameIndexStore {
  private static final Logger LOG = LoggerFactory.getLogger(NameIndexChronicleStore.class);
  // the marshaller below is static and field-free (required so Chronicle can persist it), so the kryo
  // pool it uses must be reachable statically. Initialised to the default size and reconfigured from
  // config in the constructor - names index stores are managed singletons, so sharing one pool is fine.
  private static volatile NameIndexKryoPool POOL = new NameIndexKryoPool(NamesIndexConfig.DEFAULT_KRYO_POOL_SIZE);
  private static final IndexNameBytesMarshaller MARSHALLER = new IndexNameBytesMarshaller();

  private File dir;
  private final NamesIndexConfig cfg;
  private long created; //datetime
  // main nidx instances by their key
  private final File keysF;
  private final File namesF;
  private ChronicleMap<Integer, IndexName> keys; // main nidx instances by their key
  private ChronicleMap<String, int[]> names; // group of same names by their canonical name key
  private boolean started = false;

  public NameIndexChronicleStore(NamesIndexConfig cfg) throws IOException {
    this.cfg = cfg;
    POOL = new NameIndexKryoPool(cfg.kryoPoolSize);
    this.dir = cfg.file;
    this.created = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
    if (dir == null) {
      keysF = null;
      namesF = null;
    } else {
      if (!dir.exists()) {
        FileUtils.forceMkdir(dir);
      }
      keysF = new File(dir, "keys");
      namesF = new File(dir, "names");
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
    idn.setCreatedBy(2);
    idn.setModifiedBy(3);
    idn.setCreated(LocalDateTime.now());
    idn.setModified(LocalDateTime.now());
    idn.setCombinationAuthorship(Authorship.yearAuthors("1988", "Miller"));

    var b1 = ChronicleMapBuilder.of(Integer.class, IndexName.class)
      .name("keys")
      .valueMarshaller(MARSHALLER)
      .averageValue(idn)
      .entries(cfg.maxEntries);
    var b2 = ChronicleMapBuilder.of(String.class, int[].class)
      .name("names")
      .entries(cfg.maxEntries/2)
      .averageKey("Abies alba")
      .averageValue(new int[]{3456,2345,657});

    try {
      keys = inMem() ? b1.create() : b1.createPersistedTo(keysF);
      names = inMem() ? b2.create() : b2.createPersistedTo(namesF);

    } catch (IOException e) {
      if (dir != null) {
        LOG.warn("NamesIndex store was corrupt. Remove and rebuild index from scratch. {}", e.getMessage());
        try {
          FileUtils.cleanDirectory(dir);
          keys = inMem() ? b1.create() : b1.createPersistedTo(keysF);
          names = inMem() ? b2.create() : b2.createPersistedTo(namesF);
        } catch (IOException ex) {
          throw new RuntimeException(ex);
        }
      } else {
        throw new RuntimeException("Fatal exception when creating a new in memory nidx storage", e);
      }
    }
    started = true;
    // log fill vs capacity of both maps - the half-sized names map is the first to overflow as the
    // index grows, so its fill is worth watching against maxEntries.
    LOG.info("Names index chronicle store started: keys={}/{}, names={}/{} (entries/capacity)",
      keys.size(), cfg.maxEntries, names.size(), cfg.maxEntries / 2);
  }

  @Override
  public void stop() {
    started = false;
    keys.close();
    names.close();
  }

  @Override
  public boolean hasStarted() {
    return keys != null && started;
  }

  @Override
  public IndexName get(Integer key) {
    assertOnline();
    return keys.get(key);
  }

  @Override
  public Collection<IndexName> byCanonical(Integer key) {
    // single-tier index: every entry is its own canonical, there are no qualified child entries
    return Collections.emptyList();
  }

  @Override
  public Iterable<IndexName> all() {
    assertOnline();
    return keys.values();
  }

  @Override
  public int maxKey() {
    return keys.keySet().stream().mapToInt(v -> v).max().orElse(0);
  }

  @Override
  public int count() {
    assertOnline();
    return keys.size();
  }

  @Override
  public void clear() {
    assertOnline();
    keys.clear();
    names.clear();
  }

  @Override
  public List<IndexName> get(String key) {
    assertOnline();
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
    assertOnline();
    return names.containsKey(key);
  }

  @Override
  public List<IndexName> delete(int id, Function<IndexName, String> keyFunc) {
    assertOnline();
    List<IndexName> removed = new ArrayList<>();
    var n = keys.remove(id);
    removed.add(n);
    if (n != null) {
      final String key = keyFunc.apply(n);
      // single-tier index: every entry is its own canonical, so there are no qualified child
      // entries to cascade-remove here anymore.
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
    assertOnline();
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
    // single-tier index: every entry is its own canonical (canonicalId == key), so there is no
    // separate canonical->children multimap left to maintain here anymore.
  }

  @Override
  public void compact() {
    // single-tier index: the canonical->children multimap that used to need compacting is gone;
    // nothing left to compact.
  }

  @Override
  public LocalDateTime created() {
    return LocalDateTime.ofEpochSecond(created, 0, ZoneOffset.UTC);
  }

  @Override
  public Pool<Kryo> kryo() {
    return POOL;
  }

  void check(IndexName n){
    Preconditions.checkNotNull(n.getKey(), "key required");
    Preconditions.checkNotNull(n.getCanonicalId(), "canonicalID required");
    Preconditions.checkNotNull(n.getRank(), "rank required");
    Preconditions.checkNotNull(n.getScientificName(), "scientificName required");
  }

  final static class IndexNameBytesMarshaller implements BytesWriter<IndexName>, BytesReader<IndexName> {

    @NotNull
    @Override
    public IndexName read(Bytes in, @Nullable IndexName using) {
      if (using != null) {
        System.out.println("WARN: IndexName instance existing: " + using);
      }
      return Pools.with(POOL, kryo -> {
        int size = in.readInt();
        byte[] bytes = new byte[size];
        in.read(bytes);
        return kryo.readObject(new Input(bytes), IndexName.class);
      });
    }

    @Override
    public void write(Bytes out, @NotNull IndexName value) {
      Pools.run(POOL, kryo -> {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(128);
        Output output = new Output(buffer, 128);
        kryo.writeObject(output, value);
        output.close();
        byte[] bytes = buffer.toByteArray();
        out.writeInt(bytes.length);
        out.write(bytes);
      });
    }
  }

}
