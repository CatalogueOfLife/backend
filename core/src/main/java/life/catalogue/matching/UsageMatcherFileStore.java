package life.catalogue.matching;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.NameUsage;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.matching.mmap.MmapIO;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A sealed, memory mapped usage store: built once by {@link UsageMatcherFileStoreBuilder}, then read many
 * times without any locking or copy-out.
 *
 * <p>Three files make up a store, all little endian (see {@link MmapIO}):
 * <pre>
 * usages.bin     header | long[n+1] record offsets | int[tableSize] id hash | records blob
 *                record = [int idLen][id utf8 bytes][fory serialized body]
 * canonical.bin  header | int[nCanon] canonical ids, ascending | int[nCanon+1] offsets
 *                       | int[tableSize] canonical id hash | int[nRefs] usage slots
 * groups.bin     header | byte[n], TaxGroup ordinal + 1, 0 = null
 * </pre>
 *
 * <p>The usage id is stored outside the serialized body so a hash collision is resolved by comparing raw
 * mapped bytes - no allocation and no deserialization. The canonical index stores 4 byte record slots
 * rather than id strings, so a candidate is resolved by a direct offset instead of a second hash lookup.
 * Neither index has a per canonical fan out limit: a bucket is a plain run of ints.
 *
 * <p>Everything except the group column is immutable. {@link #add(SimpleNameCached)} and
 * {@link #updateParentId(String, String)} therefore throw; a change to the data means a rebuild.
 * {@code groups.bin} is mapped read-write because {@link #analyze(TaxGroupAnalyzer)} assigns a
 * {@link TaxGroup} to every usage after the store was sealed.
 */
public class UsageMatcherFileStore implements UsageMatcherStore {
  private static final Logger LOG = LoggerFactory.getLogger(UsageMatcherFileStore.class);

  static final String USAGES_FILE = "usages.bin";
  static final String CANONICAL_FILE = "canonical.bin";
  static final String GROUPS_FILE = "groups.bin";

  static final int MAGIC_USAGES = 0x434C5553; // CLUS
  static final int MAGIC_CANONICAL = 0x434C4341; // CLCA
  static final int MAGIC_GROUPS = 0x434C4752; // CLGR
  /** Bumped whenever the layout changes; an older file is rejected and the store rebuilt. */
  static final int FORMAT_VERSION = 1;

  static final int USAGES_HEADER = 32;
  static final int CANONICAL_HEADER = 32;
  static final int GROUPS_HEADER = 16;

  private static final TaxGroup[] GROUPS = TaxGroup.values();

  private final int datasetKey;
  private final File dir;
  private final Arena arena;
  private final MemorySegment usages;
  private final MemorySegment canonical;
  private final MemorySegment groups;

  private final int n;
  private final int live;
  private final int uMask;
  private final long uOffsets;
  private final long uHash;
  private final long uRecords;

  private final int nCanon;
  private final int nRefs;
  private final int cMask;
  private final long cIds;
  private final long cOffsets;
  private final long cHash;
  private final long cRefs;

  /**
   * Cheap check whether dir holds a store written by the current code, without mapping anything.
   * False for a missing directory, a directory holding an older format or the files of the previous
   * chronicle based store.
   */
  public static boolean isStore(File dir) {
    File f = new File(dir, USAGES_FILE);
    if (!f.isFile() || f.length() < USAGES_HEADER) return false;
    try (var in = new DataInputStream(new FileInputStream(f))) {
      byte[] b = new byte[8];
      in.readFully(b);
      int magic = le(b, 0);
      int version = le(b, 4);
      return magic == MAGIC_USAGES && version == FORMAT_VERSION;
    } catch (IOException e) {
      return false;
    }
  }

  private static int le(byte[] b, int off) {
    return (b[off] & 0xff) | (b[off + 1] & 0xff) << 8 | (b[off + 2] & 0xff) << 16 | (b[off + 3] & 0xff) << 24;
  }

  /**
   * Opens a sealed store from disk.
   * @throws IOException if the directory does not hold a complete store of the current format version
   */
  public static UsageMatcherFileStore open(int datasetKey, File dir) throws IOException {
    return new UsageMatcherFileStore(datasetKey, dir);
  }

  private UsageMatcherFileStore(int datasetKey, File dir) throws IOException {
    this.datasetKey = datasetKey;
    this.dir = dir;
    for (var fn : new String[]{USAGES_FILE, CANONICAL_FILE, GROUPS_FILE}) {
      if (!new File(dir, fn).isFile()) {
        throw new IOException("Missing matcher store file " + fn + " in " + dir);
      }
    }
    var a = Arena.ofShared();
    try {
      this.usages = MmapIO.map(new File(dir, USAGES_FILE), a, false);
      this.canonical = MmapIO.map(new File(dir, CANONICAL_FILE), a, false);
      this.groups = MmapIO.map(new File(dir, GROUPS_FILE), a, true);

      checkHeader(usages, MAGIC_USAGES, USAGES_HEADER, USAGES_FILE);
      checkHeader(canonical, MAGIC_CANONICAL, CANONICAL_HEADER, CANONICAL_FILE);
      checkHeader(groups, MAGIC_GROUPS, GROUPS_HEADER, GROUPS_FILE);

      this.n = usages.get(MmapIO.INT, 8);
      int tableSize = usages.get(MmapIO.INT, 12);
      this.live = usages.get(MmapIO.INT, 16);
      this.uMask = tableSize - 1;
      this.uOffsets = USAGES_HEADER;
      this.uHash = uOffsets + 8L * (n + 1);
      this.uRecords = uHash + 4L * tableSize;

      this.nCanon = canonical.get(MmapIO.INT, 8);
      int cTableSize = canonical.get(MmapIO.INT, 12);
      this.nRefs = canonical.get(MmapIO.INT, 16);
      this.cMask = cTableSize - 1;
      this.cIds = CANONICAL_HEADER;
      this.cOffsets = cIds + 4L * nCanon;
      this.cHash = cOffsets + 4L * (nCanon + 1);
      this.cRefs = cHash + 4L * cTableSize;

      if (groups.byteSize() < GROUPS_HEADER + (long) n) {
        throw new IOException("Truncated " + GROUPS_FILE + " in " + dir);
      }
    } catch (RuntimeException | IOException e) {
      a.close();
      throw e;
    }
    this.arena = a;
  }

  private static void checkHeader(MemorySegment seg, int magic, int headerSize, String name) throws IOException {
    if (seg.byteSize() < headerSize) {
      throw new IOException("Truncated matcher store file " + name);
    }
    int m = seg.get(MmapIO.INT, 0);
    int v = seg.get(MmapIO.INT, 4);
    if (m != magic || v != FORMAT_VERSION) {
      throw new IOException(String.format(
        "%s was written by a different version (magic=%08x, version=%d). Rebuild the matcher store.", name, m, v));
    }
  }

  @Override
  public int datasetKey() {
    return datasetKey;
  }

  public File directory() {
    return dir;
  }

  @Override
  public int size() {
    return live;
  }

  @Override
  public int canonicalSize() {
    return nCanon;
  }

  /** Absolute position of a record, resolving the negation that marks a shadowed slot. */
  private long offset(int slot) {
    long v = usages.get(MmapIO.LONG, uOffsets + 8L * slot);
    return uRecords + (v < 0 ? -v - 1 : v);
  }

  private boolean shadowed(int slot) {
    return usages.get(MmapIO.LONG, uOffsets + 8L * slot) < 0;
  }

  private boolean keyEquals(int slot, byte[] key) {
    long start = offset(slot);
    int idLen = usages.get(MmapIO.INT, start);
    if (idLen != key.length) return false;
    return MemorySegment.mismatch(usages, start + 4, start + 4 + idLen,
      MemorySegment.ofArray(key), 0, key.length) == -1;
  }

  /** @return the record slot for a usage id or -1 if unknown */
  private int slot(String usageID) {
    if (usageID == null || n == 0) return -1;
    byte[] key = usageID.getBytes(StandardCharsets.UTF_8);
    int idx = MmapIO.hash(key, key.length) & uMask;
    while (true) {
      int v = usages.get(MmapIO.INT, uHash + 4L * idx);
      if (v == 0) return -1;
      int slot = v - 1;
      if (keyEquals(slot, key)) return slot;
      idx = (idx + 1) & uMask;
    }
  }

  private SimpleNameCached read(int slot) {
    long start = offset(slot);
    long end = offset(slot + 1);
    int idLen = usages.get(MmapIO.INT, start);
    long bodyPos = start + 4 + idLen;
    int bodyLen = (int) (end - bodyPos);
    byte[] body = new byte[bodyLen];
    MemorySegment.copy(usages, MmapIO.BYTE, bodyPos, body, 0, bodyLen);
    var sn = UsageMatcherFactory.FURY.deserializeJavaObject(body, SimpleNameCached.class);
    sn.setGroup(group(slot));
    return sn;
  }

  private TaxGroup group(int slot) {
    int g = groups.get(MmapIO.BYTE, GROUPS_HEADER + (long) slot) & 0xff;
    return g == 0 ? null : GROUPS[g - 1];
  }

  @Override
  public SimpleNameCached get(String usageID) throws NotFoundException {
    int slot = slot(usageID);
    if (slot < 0) {
      throw NotFoundException.notFound(NameUsage.class, DSID.of(datasetKey, usageID));
    }
    return read(slot);
  }

  @Override
  public void update(String usageID, TaxGroup group) {
    int slot = slot(usageID);
    if (slot < 0) {
      throw NotFoundException.notFound(NameUsage.class, DSID.of(datasetKey, usageID));
    }
    groups.set(MmapIO.BYTE, GROUPS_HEADER + (long) slot, (byte) (group == null ? 0 : group.ordinal() + 1));
  }

  @Override
  public List<SimpleNameCached> simpleNamesByCanonicalId(int canonId) {
    int ci = canonIndex(canonId);
    if (ci < 0) return List.of();
    int from = canonical.get(MmapIO.INT, cOffsets + 4L * ci);
    int to = canonical.get(MmapIO.INT, cOffsets + 4L * (ci + 1));
    var list = new ArrayList<SimpleNameCached>(to - from);
    for (int i = from; i < to; i++) {
      list.add(read(canonical.get(MmapIO.INT, cRefs + 4L * i)));
    }
    return list;
  }

  private int canonIndex(int canonId) {
    if (nCanon == 0) return -1;
    int idx = MmapIO.hash(canonId) & cMask;
    while (true) {
      int v = canonical.get(MmapIO.INT, cHash + 4L * idx);
      if (v == 0) return -1;
      int ci = v - 1;
      if (canonical.get(MmapIO.INT, cIds + 4L * ci) == canonId) return ci;
      idx = (idx + 1) & cMask;
    }
  }

  @Override
  public Iterable<Integer> allCanonicalIds() {
    return () -> new Iterator<>() {
      int i = 0;

      @Override
      public boolean hasNext() {
        return i < nCanon;
      }

      @Override
      public Integer next() {
        if (i >= nCanon) throw new NoSuchElementException();
        return canonical.get(MmapIO.INT, cIds + 4L * i++);
      }
    };
  }

  @Override
  public Iterable<SimpleNameCached> all() {
    return () -> new Iterator<>() {
      int slot = advance(0);

      private int advance(int from) {
        int s = from;
        while (s < n && shadowed(s)) s++;
        return s;
      }

      @Override
      public boolean hasNext() {
        return slot < n;
      }

      @Override
      public SimpleNameCached next() {
        if (slot >= n) throw new NoSuchElementException();
        var sn = read(slot);
        slot = advance(slot + 1);
        return sn;
      }
    };
  }

  @Override
  public void add(SimpleNameCached sn) {
    throw new UnsupportedOperationException("The file store is sealed, rebuild it to change its data");
  }

  @Override
  public void updateParentId(String usageID, String parentId) {
    throw new UnsupportedOperationException("The file store is sealed, rebuild it to change its data");
  }

  @Override
  public void close() {
    try {
      groups.force();
    } catch (RuntimeException e) {
      LOG.warn("Failed to flush the tax group column of matcher store {}", dir, e);
    }
    arena.close();
  }
}
