package life.catalogue.matching.nidx;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.map.ChronicleMapBuilder;

/**
 * NameIndexStore implementation that is backed by a single persistent Chronicle map keyed by the
 * normalized canonical bucket key, with the names index id (nidx) as its value.
 */
public class NameIndexChronicleStore implements NameIndexStore {
  private static final Logger LOG = LoggerFactory.getLogger(NameIndexChronicleStore.class);

  private final File dir;
  private final NamesIndexConfig cfg;
  private long created; //datetime
  private final File namesF;
  private ChronicleMap<String, Integer> names; // normalized canonical bucket key -> nidx
  // the max nidx held, maintained on add(). add is the only writer.
  private final AtomicInteger maxKey = new AtomicInteger(0);
  private boolean started = false;

  public NameIndexChronicleStore(NamesIndexConfig cfg) throws IOException {
    this.cfg = cfg;
    this.dir = cfg.file;
    this.created = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
    if (dir == null) {
      namesF = null;
    } else {
      if (!dir.exists()) {
        FileUtils.forceMkdir(dir);
      }
      namesF = new File(dir, "names");
    }
  }

  private boolean inMem() {
    return dir == null;
  }

  @Override
  public void start() {
    var b = ChronicleMapBuilder.of(String.class, Integer.class)
      .name("names")
      .entries(cfg.maxEntries)
      .averageKey("Abies alba");

    try {
      names = inMem() ? b.create() : b.createPersistedTo(namesF);
    } catch (IOException e) {
      if (dir != null) {
        LOG.warn("NamesIndex store was corrupt. Remove and rebuild index from scratch. {}", e.getMessage());
        try {
          FileUtils.cleanDirectory(dir);
          names = inMem() ? b.create() : b.createPersistedTo(namesF);
        } catch (IOException ex) {
          throw new RuntimeException(ex);
        }
      } else {
        throw new RuntimeException("Fatal exception when creating a new in memory nidx storage", e);
      }
    }
    // Anything past the open must not leak the map. Chronicle hands back an already open instance for
    // the same file, so a map left open by a failed start would be returned again on every later start -
    // even after an operator swapped in a corrected file - making a JVM restart the only way out.
    try {
      // Chronicle adopts the marshallers recorded in an existing file's header and ignores the value
      // class given to the builder above, so a legacy file (names mapped a bucket key to an int[] of
      // nidx ids before the single-tier refactor) opens without error and only fails once a value is
      // read. Check the adopted type up front to fail with something actionable.
      if (names.valueClass() != Integer.class) {
        throw new IllegalStateException(String.format(
          "Names index file at %s holds %s values and was written by an older version. "
            + "Rebuild the index file with the current code (nidx --file-only), swap it in and restart the server.",
          inMem() ? "memory" : dir.getAbsolutePath(), names.valueClass().getSimpleName()));
      }
      // recompute max nidx from a persisted map
      int max = 0;
      for (Integer v : names.values()) {
        if (v != null && v > max) max = v;
      }
      maxKey.set(max);
      started = true;
    } catch (RuntimeException e) {
      closeQuietly();
      throw e;
    }
    LOG.info("Names index chronicle store started: names={}/{} (entries/capacity)", names.size(), cfg.maxEntries);
  }

  @Override
  public void stop() {
    started = false;
    closeQuietly();
  }

  /**
   * Closes the chronicle map if open and clears the reference, so a subsequent start() reopens the file
   * from scratch rather than being handed back this instance.
   */
  private void closeQuietly() {
    if (names != null) {
      try {
        names.close();
      } catch (RuntimeException e) {
        LOG.warn("Failed to close names index chronicle map", e);
      }
      names = null;
    }
  }

  @Override
  public boolean hasStarted() {
    return names != null && started;
  }

  @Override
  public int get(String normalized) {
    assertOnline();
    Integer k = names.get(normalized);
    return k == null ? 0 : k;
  }

  @Override
  public boolean contains(String normalized) {
    assertOnline();
    return names.containsKey(normalized);
  }

  @Override
  public int maxKey() {
    return maxKey.get();
  }

  @Override
  public int count() {
    assertOnline();
    return names.size();
  }

  @Override
  public void clear() {
    assertOnline();
    names.clear();
    maxKey.set(0);
    this.created = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
  }

  /**
   * @param normalized make sure this is a pure ASCII key, no chars above 7 bits allowed !!!
   */
  @Override
  public void add(String normalized, int nidx) {
    assertOnline();
    Integer prev = names.put(normalized, nidx);
    if (prev != null && prev != nidx) {
      LOG.warn("Names index bucket >{}< already had key {} - overwriting with new key {}", normalized, prev, nidx);
    }
    if (nidx > maxKey.get()) {
      maxKey.set(nidx);
    }
  }

  @Override
  public Iterable<Map.Entry<String, Integer>> entries() {
    assertOnline();
    return names.entrySet();
  }

  @Override
  public void compact() {
    // single normalized->nidx map: nothing to compact.
  }

  @Override
  public LocalDateTime created() {
    return LocalDateTime.ofEpochSecond(created, 0, ZoneOffset.UTC);
  }
}
