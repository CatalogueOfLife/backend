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
 *
 * <h2>Who may write</h2>
 * {@code groups.bin} is the one mutable part, because {@link #analyze(TaxGroupAnalyzer)} assigns a
 * {@link TaxGroup} to every usage after the store was sealed. It is mapped read-write <em>only</em> for a
 * store opened through {@link #openWritable(int, File)}, which in practice means the one
 * {@link UsageMatcherFileStoreBuilder#seal()} hands back to whoever just built it into a directory nobody
 * else knows about yet. Every other opener - a server reopening a store, the matching server - goes through
 * {@link #open(int, File)} and gets a wholly read-only store, so:
 * <ul>
 *   <li>a store directory can live on a read-only mount,</li>
 *   <li>a stray {@link #update(String, TaxGroup)} fails loudly instead of racing.</li>
 * </ul>
 *
 * <p>This matters because the mapping is {@code MAP_SHARED}: two writers - threads, or processes sharing a
 * {@code storageDir} - would overwrite each other's bytes with no ordering or visibility guarantee. Reading
 * is unrestricted; any number of processes may map the same sealed store, and the two immutable files never
 * change under them. Building, on the other hand, is single-writer by design and is <em>not</em> coordinated
 * across processes - see {@link UsageMatcherFactory} for what that means for a shared storage directory.
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
  private final boolean writableGroups;

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
   * The regions of the three files, derived from their headers and validated against the actual file sizes.
   * Every offset a lookup uses comes from here, so a truncated or otherwise corrupt store is rejected while
   * opening rather than blowing up with an {@link IndexOutOfBoundsException} on some later match request.
   */
  private record Layout(int n, int live, int uMask, long uOffsets, long uHash, long uRecords, long blobLen,
                        int nCanon, int nRefs, int cMask, long cIds, long cOffsets, long cHash, long cRefs) {

    /**
     * @param uHdr {n, tableSize, live} and the record blob length
     * @param cHdr {nCanon, tableSize, nRefs}
     * @param gn   the usage count recorded in the group column
     */
    static Layout of(int[] uHdr, long blobLen, long uLen, int[] cHdr, long cLen, int gn, long gLen) throws IOException {
      int n = uHdr[0], uTable = uHdr[1], live = uHdr[2];
      checkCount(n, "usage count", USAGES_FILE);
      checkTable(uTable, n, USAGES_FILE);
      if (live < 0 || live > n) {
        throw corrupt(USAGES_FILE, "live count " + live + " outside 0.." + n);
      }
      long uOffsets = USAGES_HEADER;
      long uHash = uOffsets + 8L * (n + 1);
      long uRecords = uHash + 4L * uTable;
      checkSize(uLen, uRecords, blobLen, "record blob", USAGES_FILE);

      int nCanon = cHdr[0], cTable = cHdr[1], nRefs = cHdr[2];
      checkCount(nCanon, "canonical count", CANONICAL_FILE);
      checkCount(nRefs, "canonical ref count", CANONICAL_FILE);
      checkTable(cTable, nCanon, CANONICAL_FILE);
      if (nRefs < nCanon) {
        throw corrupt(CANONICAL_FILE, nRefs + " refs cannot cover " + nCanon + " canonical ids");
      }
      long cIds = CANONICAL_HEADER;
      long cOffsets = cIds + 4L * nCanon;
      long cHash = cOffsets + 4L * (nCanon + 1);
      long cRefs = cHash + 4L * cTable;
      checkSize(cLen, cRefs, 4L * nRefs, "canonical refs", CANONICAL_FILE);

      if (gn != n) {
        throw corrupt(GROUPS_FILE, "holds " + gn + " groups but " + USAGES_FILE + " holds " + n + " usages");
      }
      checkSize(gLen, GROUPS_HEADER, n, "group column", GROUPS_FILE);

      return new Layout(n, live, uTable - 1, uOffsets, uHash, uRecords, blobLen,
        nCanon, nRefs, cTable - 1, cIds, cOffsets, cHash, cRefs);
    }
  }

  /**
   * Checks whether dir holds a complete and structurally sound store written by the current code, reading
   * only the three file headers - nothing is mapped and no record is touched. False for a missing directory,
   * an older format, the files of the previous chronicle based store, and for a store whose headers do not
   * describe the bytes actually on disk.
   *
   * <p>{@code UsageMatcherFactory.needsRebuild} leans on this: a store that cannot be opened has to be
   * reported as needing a rebuild here, or it would be skipped by reconcile and stay unavailable forever.
   */
  public static boolean isStore(File dir) {
    try {
      int[] uHdr = new int[4]; // n, tableSize, live, reserved
      long[] blobLen = new long[1];
      long uLen = readHeader(dir, USAGES_FILE, MAGIC_USAGES, USAGES_HEADER, uHdr, blobLen);
      int[] cHdr = new int[3]; // nCanon, tableSize, nRefs
      long cLen = readHeader(dir, CANONICAL_FILE, MAGIC_CANONICAL, CANONICAL_HEADER, cHdr, null);
      int[] gHdr = new int[1]; // n
      long gLen = readHeader(dir, GROUPS_FILE, MAGIC_GROUPS, GROUPS_HEADER, gHdr, null);
      Layout.of(uHdr, blobLen[0], uLen, cHdr, cLen, gHdr[0], gLen);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Reads magic, version, the following {@code out.length} ints and, when {@code trailing} is given, one
   * more long, all in a single pass over the file header.
   * @return the file length
   */
  private static long readHeader(File dir, String name, int magic, int headerSize, int[] out, long[] trailing)
    throws IOException {
    File f = new File(dir, name);
    if (!f.isFile() || f.length() < headerSize) {
      throw new IOException("Missing or truncated matcher store file " + name + " in " + dir);
    }
    try (var in = new DataInputStream(new FileInputStream(f))) {
      if ((int) readLe(in, 4) != magic || (int) readLe(in, 4) != FORMAT_VERSION) {
        throw new IOException(name + " in " + dir + " was written by a different version");
      }
      for (int i = 0; i < out.length; i++) {
        out[i] = (int) readLe(in, 4);
      }
      if (trailing != null) {
        trailing[0] = readLe(in, 8);
      }
    }
    return f.length();
  }

  /** Reads a little endian value of {@code len} bytes, sign extended when it is an int. */
  private static long readLe(DataInputStream in, int len) throws IOException {
    byte[] b = new byte[len];
    in.readFully(b);
    long v = 0;
    for (int i = len - 1; i >= 0; i--) {
      v = (v << 8) | (b[i] & 0xffL);
    }
    return len == 4 ? (int) v : v;
  }

  /**
   * Opens a sealed store from disk, entirely read only - {@link #update(String, TaxGroup)} throws.
   * This is what a server reopening a store does, so the store directory may sit on a read only mount.
   * @throws IOException if the directory does not hold a complete store of the current format version
   */
  public static UsageMatcherFileStore open(int datasetKey, File dir) throws IOException {
    return new UsageMatcherFileStore(datasetKey, dir, false);
  }

  /**
   * Opens a sealed store whose group column may be written by {@link #analyze(TaxGroupAnalyzer)}.
   *
   * <p>The caller must be the store's exclusive owner: {@code groups.bin} is mapped {@code MAP_SHARED},
   * so concurrent writers - threads or processes - would silently overwrite each other's bytes with no
   * ordering guarantee. In practice only {@link UsageMatcherFileStoreBuilder#seal()} hands out a writable
   * store, to whoever just built it into a private directory. See the class javadoc.
   */
  public static UsageMatcherFileStore openWritable(int datasetKey, File dir) throws IOException {
    return new UsageMatcherFileStore(datasetKey, dir, true);
  }

  private UsageMatcherFileStore(int datasetKey, File dir, boolean writableGroups) throws IOException {
    this.datasetKey = datasetKey;
    this.dir = dir;
    this.writableGroups = writableGroups;
    for (var fn : new String[]{USAGES_FILE, CANONICAL_FILE, GROUPS_FILE}) {
      if (!new File(dir, fn).isFile()) {
        throw new IOException("Missing matcher store file " + fn + " in " + dir);
      }
    }
    var a = Arena.ofShared();
    try {
      this.usages = MmapIO.map(new File(dir, USAGES_FILE), a, false);
      this.canonical = MmapIO.map(new File(dir, CANONICAL_FILE), a, false);
      this.groups = MmapIO.map(new File(dir, GROUPS_FILE), a, writableGroups);

      checkHeader(usages, MAGIC_USAGES, USAGES_HEADER, USAGES_FILE);
      checkHeader(canonical, MAGIC_CANONICAL, CANONICAL_HEADER, CANONICAL_FILE);
      checkHeader(groups, MAGIC_GROUPS, GROUPS_HEADER, GROUPS_FILE);

      var l = Layout.of(
        new int[]{usages.get(MmapIO.INT, 8), usages.get(MmapIO.INT, 12), usages.get(MmapIO.INT, 16)},
        usages.get(MmapIO.LONG, 24), usages.byteSize(),
        new int[]{canonical.get(MmapIO.INT, 8), canonical.get(MmapIO.INT, 12), canonical.get(MmapIO.INT, 16)},
        canonical.byteSize(), groups.get(MmapIO.INT, 8), groups.byteSize());
      this.n = l.n();
      this.live = l.live();
      this.uMask = l.uMask();
      this.uOffsets = l.uOffsets();
      this.uHash = l.uHash();
      this.uRecords = l.uRecords();
      this.nCanon = l.nCanon();
      this.nRefs = l.nRefs();
      this.cMask = l.cMask();
      this.cIds = l.cIds();
      this.cOffsets = l.cOffsets();
      this.cHash = l.cHash();
      this.cRefs = l.cRefs();

      // the record blob is only reachable through the offsets, so pin down their two boundary values here.
      // Everything in between is bounds checked by the MemorySegment on access.
      if (n > 0) {
        long first = usages.get(MmapIO.LONG, uOffsets);
        long last = usages.get(MmapIO.LONG, uOffsets + 8L * n);
        if (first != 0 && first != -1) { // -1 is slot 0 negated, ie shadowed by a duplicate id
          throw corrupt(USAGES_FILE, "first record offset is " + first + ", expected 0");
        }
        if (last != l.blobLen()) {
          throw corrupt(USAGES_FILE, "last record offset is " + last + ", expected the blob length " + l.blobLen());
        }
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

  private static IOException corrupt(String name, String detail) {
    return new IOException(String.format("Corrupt matcher store file %s: %s. Rebuild the matcher store.", name, detail));
  }

  private static void checkCount(int count, String what, String name) throws IOException {
    if (count < 0) {
      throw corrupt(name, "negative " + what + " " + count);
    }
  }

  /**
   * An open addressed table is only usable if it is a power of two (the mask assumes it) and has at least one
   * free slot for every possible key - otherwise the linear probe in {@link #slot(String)} / {@link #canonIndex(int)}
   * never terminates on a miss, which is a far nastier failure than an out of bounds read.
   */
  private static void checkTable(int tableSize, int keys, String name) throws IOException {
    if (tableSize < 16 || Integer.bitCount(tableSize) != 1) {
      throw corrupt(name, "hash table size " + tableSize + " is not a power of two >= 16");
    }
    if (tableSize <= keys) {
      throw corrupt(name, "hash table of " + tableSize + " cannot hold " + keys + " keys");
    }
  }

  /** Verifies that a region starting at {@code from} with {@code len} bytes is exactly what the file holds. */
  private static void checkSize(long fileLen, long from, long len, String what, String name) throws IOException {
    if (len < 0 || from < 0 || from + len != fileLen) {
      throw corrupt(name, String.format("%s ends at %d but the file is %d bytes", what, from + len, fileLen));
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
    if (!writableGroups) {
      throw new IllegalStateException("The matcher store at " + dir + " is open read only. "
        + "Tax groups can only be assigned by whoever built the store, see UsageMatcherFileStore.openWritable");
    }
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
    if (writableGroups) { // a read only mapping has nothing to flush and force() would throw on it
      try {
        groups.force();
      } catch (RuntimeException e) {
        LOG.warn("Failed to flush the tax group column of matcher store {}", dir, e);
      }
    }
    arena.close();
  }
}
