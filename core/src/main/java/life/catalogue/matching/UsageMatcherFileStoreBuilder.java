package life.catalogue.matching;

import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.matching.mmap.MmapIO;

import java.io.File;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.BitSet;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.fastutil.Arrays;

/**
 * Builds a {@link UsageMatcherFileStore} from a stream of usages.
 *
 * <p>Every {@link #add(SimpleNameCached)} is a sequential append to three temporary files - there is no
 * read-modify-write anywhere, so the cost is linear in the number of usages no matter how many of them
 * share a canonical name. {@link #seal()} then assembles the final files in a single pass each and hands
 * back the read only store.
 *
 * <p>The builder is not thread safe; loads are single threaded.
 */
public class UsageMatcherFileStoreBuilder implements UsageSink, AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(UsageMatcherFileStoreBuilder.class);

  private final int datasetKey;
  private final File dir;
  private final File recordsTmp;
  private final File offsetsTmp;
  private final File canonTmp;
  private final File groupsTmp;

  private MmapIO.LeOut records;
  private MmapIO.LeOut offsets;
  private MmapIO.LeOut canon;
  private MmapIO.LeOut groups;

  private int n = 0;
  private int canonPairs = 0;
  private long pos = 0;
  private boolean sealed = false;
  // slots dropped because a later add repeated their id; null while there are none
  private BitSet shadowedSlots;
  private int shadowedCount = 0;

  public UsageMatcherFileStoreBuilder(int datasetKey, File dir) throws IOException {
    this.datasetKey = datasetKey;
    this.dir = dir;
    FileUtils.forceMkdir(dir);
    this.recordsTmp = new File(dir, "records.tmp");
    this.offsetsTmp = new File(dir, "offsets.tmp");
    this.canonTmp = new File(dir, "canon.tmp");
    this.groupsTmp = new File(dir, "groups.tmp");
    this.records = new MmapIO.LeOut(recordsTmp);
    this.offsets = new MmapIO.LeOut(offsetsTmp);
    this.canon = new MmapIO.LeOut(canonTmp);
    this.groups = new MmapIO.LeOut(groupsTmp);
  }

  @Override
  public int datasetKey() {
    return datasetKey;
  }

  public int size() {
    return n;
  }

  /**
   * Appends a usage. Ids should be unique - if the same id is added twice the later record wins, exactly
   * like a map put, and the shadowed record is skipped by the sealed store.
   */
  @Override
  public void add(SimpleNameCached sn) {
    if (sealed) throw new IllegalStateException("Store already sealed");
    try {
      byte[] id = sn.getId().getBytes(StandardCharsets.UTF_8);
      byte[] body = UsageMatcherFactory.FURY.serializeJavaObject(sn);
      offsets.writeLong(pos);
      records.writeInt(id.length);
      records.write(id);
      records.write(body);
      pos += 4L + id.length + body.length;
      var g = sn.getGroup();
      groups.writeByte(g == null ? 0 : g.ordinal() + 1);
      if (sn.getCanonicalId() != null) {
        canon.writeInt(sn.getCanonicalId());
        canon.writeInt(n);
        canonPairs++;
      }
      n++;
    } catch (IOException e) {
      throw new RuntimeException("Failed to add usage " + sn.getId() + " to the matcher store for dataset " + datasetKey, e);
    }
  }

  /**
   * Assembles the final files and opens the sealed store. The builder is unusable afterwards; closing it
   * only removes the temporary files.
   */
  public UsageMatcherFileStore seal() throws IOException {
    if (sealed) throw new IllegalStateException("Store already sealed");
    sealed = true;
    closeTemps();

    long[] offs = readOffsets();
    int[] table = buildUsageIndex(offs);
    writeUsages(offs, table);
    table = null;
    offs = null;

    writeCanonical();
    writeGroups();

    FileUtils.deleteQuietly(recordsTmp);
    FileUtils.deleteQuietly(offsetsTmp);
    FileUtils.deleteQuietly(canonTmp);
    FileUtils.deleteQuietly(groupsTmp);

    LOG.info("Sealed matcher store for dataset {} with {} usages in {}", datasetKey, n, dir);
    return UsageMatcherFileStore.open(datasetKey, dir);
  }

  private long[] readOffsets() throws IOException {
    long[] offs = new long[n + 1];
    try (var in = new MmapIO.LeIn(offsetsTmp)) {
      for (int i = 0; i < n; i++) {
        offs[i] = in.readLong();
      }
    }
    offs[n] = pos;
    return offs;
  }

  /**
   * Builds the open addressed id -> slot table from the record blob. A repeated id keeps the later record
   * and marks the earlier slot shadowed by negating its offset.
   */
  private int[] buildUsageIndex(long[] offs) throws IOException {
    int[] table = new int[MmapIO.tableSize(n)];
    int mask = table.length - 1;
    int shadowed = 0;
    if (n > 0) {
      try (Arena arena = Arena.ofConfined()) {
        MemorySegment seg = MmapIO.map(recordsTmp, arena, false);
        byte[] buf = new byte[64];
        for (int slot = 0; slot < n; slot++) {
          long start = offs[slot] < 0 ? -offs[slot] - 1 : offs[slot];
          int idLen = seg.get(MmapIO.INT, start);
          if (buf.length < idLen) buf = new byte[idLen];
          MemorySegment.copy(seg, MmapIO.BYTE, start + 4, buf, 0, idLen);
          int idx = MmapIO.hash(buf, idLen) & mask;
          while (true) {
            int v = table[idx];
            if (v == 0) {
              table[idx] = slot + 1;
              break;
            }
            int other = v - 1;
            if (keyEquals(seg, offs, other, seg, start + 4, idLen)) {
              // same id added twice: the later record wins, the earlier one is dropped from the store
              offs[other] = -offs[other] - 1;
              table[idx] = slot + 1;
              if (shadowedSlots == null) shadowedSlots = new BitSet(n);
              shadowedSlots.set(other);
              shadowed++;
              break;
            }
            idx = (idx + 1) & mask;
          }
        }
      }
    }
    if (shadowed > 0) {
      LOG.warn("Matcher store for dataset {} was given {} duplicate usage ids, keeping the last of each", datasetKey, shadowed);
    }
    this.shadowedCount = shadowed;
    return table;
  }

  private static boolean keyEquals(MemorySegment seg, long[] offs, int slot, MemorySegment key, long keyPos, int keyLen) {
    long start = offs[slot] < 0 ? -offs[slot] - 1 : offs[slot];
    int idLen = seg.get(MmapIO.INT, start);
    if (idLen != keyLen) return false;
    return MemorySegment.mismatch(seg, start + 4, start + 4 + idLen, key, keyPos, keyPos + keyLen) == -1;
  }

  private void writeUsages(long[] offs, int[] table) throws IOException {
    File f = new File(dir, UsageMatcherFileStore.USAGES_FILE);
    long headerEnd;
    try (var out = new MmapIO.LeOut(f)) {
      out.writeInt(UsageMatcherFileStore.MAGIC_USAGES);
      out.writeInt(UsageMatcherFileStore.FORMAT_VERSION);
      out.writeInt(n);
      out.writeInt(table.length);
      out.writeInt(n - shadowedCount);
      out.writeInt(0); // reserved
      out.writeLong(pos); // total length of the records blob
      for (long o : offs) {
        out.writeLong(o);
      }
      for (int t : table) {
        out.writeInt(t);
      }
      out.flush();
      headerEnd = out.written();
    }
    appendBlob(f, headerEnd, recordsTmp);
  }

  /** Appends the raw record blob at the given position, avoiding a copy through user space. */
  private static void appendBlob(File target, long position, File blob) throws IOException {
    try (FileChannel dst = FileChannel.open(target.toPath(), StandardOpenOption.WRITE);
         FileChannel src = FileChannel.open(blob.toPath(), StandardOpenOption.READ)) {
      long size = src.size();
      long done = 0;
      while (done < size) {
        long t = dst.transferFrom(src, position + done, size - done);
        if (t <= 0) break;
        done += t;
      }
      if (done != size) {
        throw new IOException("Only transferred " + done + " of " + size + " bytes into " + target);
      }
      dst.force(true);
    }
  }

  private void writeCanonical() throws IOException {
    final int[] ids = new int[canonPairs];
    final int[] slots = new int[canonPairs];
    int m = 0;
    try (var in = new MmapIO.LeIn(canonTmp)) {
      for (int i = 0; i < canonPairs; i++) {
        int cid = in.readInt();
        int slot = in.readInt();
        if (shadowedSlots != null && shadowedSlots.get(slot)) continue; // dropped duplicate
        ids[m] = cid;
        slots[m] = slot;
        m++;
      }
    }
    final int size = m;
    // sort by canonical id, then by slot: quicksort is not stable, and the order of the usages within a
    // canonical bucket is not cosmetic - IdProvider hands them to issueIDs in this order and which usage
    // reuses which released id depends on it. Ordering by slot keeps the insertion (dataset scan) order.
    Arrays.parallelQuickSort(0, size,
      (a, b) -> {
        int c = Integer.compare(ids[a], ids[b]);
        return c != 0 ? c : Integer.compare(slots[a], slots[b]);
      },
      (a, b) -> {
        int t = ids[a]; ids[a] = ids[b]; ids[b] = t;
        t = slots[a]; slots[a] = slots[b]; slots[b] = t;
      });

    int nCanon = 0;
    for (int i = 0; i < size; i++) {
      if (i == 0 || ids[i] != ids[i - 1]) nCanon++;
    }
    int tableSize = MmapIO.tableSize(nCanon);
    int mask = tableSize - 1;
    int[] distinct = new int[nCanon];
    int[] starts = new int[nCanon + 1];
    int[] table = new int[tableSize];
    int c = 0;
    for (int i = 0; i < size; i++) {
      if (i == 0 || ids[i] != ids[i - 1]) {
        distinct[c] = ids[i];
        starts[c] = i;
        int idx = MmapIO.hash(ids[i]) & mask;
        while (table[idx] != 0) {
          idx = (idx + 1) & mask;
        }
        table[idx] = c + 1;
        c++;
      }
    }
    starts[nCanon] = size;

    try (var out = new MmapIO.LeOut(new File(dir, UsageMatcherFileStore.CANONICAL_FILE))) {
      out.writeInt(UsageMatcherFileStore.MAGIC_CANONICAL);
      out.writeInt(UsageMatcherFileStore.FORMAT_VERSION);
      out.writeInt(nCanon);
      out.writeInt(tableSize);
      out.writeInt(size);
      out.writeInt(0); // reserved
      out.writeLong(0); // reserved
      for (int i = 0; i < nCanon; i++) out.writeInt(distinct[i]);
      for (int i = 0; i <= nCanon; i++) out.writeInt(starts[i]);
      for (int t : table) out.writeInt(t);
      for (int i = 0; i < size; i++) out.writeInt(slots[i]);
    }
  }

  private void writeGroups() throws IOException {
    try (var out = new MmapIO.LeOut(new File(dir, UsageMatcherFileStore.GROUPS_FILE));
         var in = new MmapIO.LeIn(groupsTmp)) {
      out.writeInt(UsageMatcherFileStore.MAGIC_GROUPS);
      out.writeInt(UsageMatcherFileStore.FORMAT_VERSION);
      out.writeInt(n);
      out.writeInt(0); // reserved
      byte[] buf = new byte[65536];
      int left = n;
      while (left > 0) {
        int len = Math.min(buf.length, left);
        in.readFully(buf, len);
        out.write(len == buf.length ? buf : java.util.Arrays.copyOf(buf, len));
        left -= len;
      }
    }
  }

  private void closeTemps() throws IOException {
    if (records != null) { records.close(); records = null; }
    if (offsets != null) { offsets.close(); offsets = null; }
    if (canon != null) { canon.close(); canon = null; }
    if (groups != null) { groups.close(); groups = null; }
  }

  @Override
  public void close() {
    try {
      closeTemps();
    } catch (IOException e) {
      LOG.warn("Failed to close temporary matcher store files in {}", dir, e);
    }
    FileUtils.deleteQuietly(recordsTmp);
    FileUtils.deleteQuietly(offsetsTmp);
    FileUtils.deleteQuietly(canonTmp);
    FileUtils.deleteQuietly(groupsTmp);
  }
}
