package life.catalogue.matching;

import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.common.io.TempFile;

import org.gbif.nameparser.api.Rank;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assume.assumeTrue;

import static org.junit.Assert.*;

public class UsageMatcherFileStoreTest extends UsageMatcherStoreTestBase {
  private final List<TempFile> dirs = new ArrayList<>();

  private File newDir() throws IOException {
    var tf = TempFile.directory();
    dirs.add(tf);
    return tf.file;
  }

  @Override
  UsageSink createSink(int datasetKey) throws IOException {
    return new UsageMatcherFileStoreBuilder(datasetKey, newDir());
  }

  @Override
  UsageMatcherStore seal(UsageSink sink) throws IOException {
    return ((UsageMatcherFileStoreBuilder) sink).seal();
  }

  @After
  public void cleanup() {
    dirs.forEach(TempFile::close);
    dirs.clear();
  }

  @Test
  public void reopenPreservesData() throws IOException {
    var dir = newDir();
    var sn = snc("abc", "xyz", "Aus bus", "Smith", Rank.SPECIES, 42, 99);
    try (var builder = new UsageMatcherFileStoreBuilder(77, dir)) {
      builder.add(sn);
      try (var store = builder.seal()) {
        assertEquals(1, store.size());
      }
    }
    assertTrue(UsageMatcherFileStore.isStore(dir));
    // reopen without any DB derived params - data must still be present
    try (var store = UsageMatcherFileStore.open(77, dir)) {
      assertEquals(1, store.size());
      assertEquals(1, store.canonicalSize());
      assertEquals(sn, store.get("abc"));
      assertEquals(List.of("abc"), store.simpleNamesByCanonicalId(42).stream().map(s -> s.getId()).toList());
    }
    // the temporary build files are gone, only the three store files and nothing else remain
    var names = new java.util.TreeSet<>(List.of(dir.list()));
    assertEquals(java.util.Set.of("usages.bin", "canonical.bin", "groups.bin"), names);
  }

  /** The tax group column is the one mutable part of a sealed store - IdProvider fills it after loading. */
  @Test
  public void taxGroupSurvivesReopen() throws IOException {
    var dir = newDir();
    try (var builder = new UsageMatcherFileStoreBuilder(78, dir)) {
      builder.add(snc("a", null, "Aus", null, Rank.GENUS, 1, 1));
      builder.add(snc("b", "a", "Aus bus", null, Rank.SPECIES, 2, 2));
      try (var store = builder.seal()) {
        assertNull(store.get("a").getGroup());
        store.update("a", TaxGroup.Plants);
        assertEquals(TaxGroup.Plants, store.get("a").getGroup());
        assertNull(store.get("b").getGroup());
      }
    }
    try (var store = UsageMatcherFileStore.open(78, dir)) {
      assertEquals(TaxGroup.Plants, store.get("a").getGroup());
      assertNull(store.get("b").getGroup());
    }
    // and it can be cleared again - by an owner that opened the store for writing
    try (var store = UsageMatcherFileStore.openWritable(78, dir)) {
      store.update("a", null);
      assertNull(store.get("a").getGroup());
    }
  }

  @Test
  public void sealedStoreRejectsWrites() throws IOException {
    try (var builder = new UsageMatcherFileStoreBuilder(80, newDir())) {
      builder.add(snc("a", null, "Aus", null, Rank.GENUS, 1, 1));
      try (var store = builder.seal()) {
        assertThrows(UnsupportedOperationException.class, () -> store.add(snc("b", null, "Bus", null, Rank.GENUS, 2, 2)));
        assertThrows(UnsupportedOperationException.class, () -> store.updateParentId("a", "b"));
      }
      assertThrows(IllegalStateException.class, () -> builder.add(snc("c", null, "Cus", null, Rank.GENUS, 3, 3)));
      assertThrows(IllegalStateException.class, builder::seal);
    }
  }

  /** add() is documented to behave like a map put, so a repeated id keeps the last record. */
  @Test
  public void duplicateIdsKeepTheLast() throws IOException {
    try (var builder = new UsageMatcherFileStoreBuilder(81, newDir())) {
      builder.add(snc("a", null, "Aus", "Smith", Rank.GENUS, 1, 1));
      builder.add(snc("b", null, "Bus", "Smith", Rank.GENUS, 2, 2));
      builder.add(snc("a", null, "Aus rewritten", "Miller", Rank.GENUS, 3, 3));
      try (var store = builder.seal()) {
        assertEquals(2, store.size());
        assertEquals("Aus rewritten", store.get("a").getName());
        assertEquals("Bus", store.get("b").getName());
        assertEquals(2, stream(store.all()).count());
        // the shadowed record is gone from the canonical index too
        assertTrue(store.simpleNamesByCanonicalId(1).isEmpty());
        assertEquals(List.of("a"), store.simpleNamesByCanonicalId(3).stream().map(s -> s.getId()).toList());
        assertEquals(2, store.canonicalSize());
      }
    }
  }

  /** Ids are compared as raw utf8 bytes, so non ascii and long ids must round trip unharmed. */
  @Test
  public void awkwardIds() throws IOException {
    var ids = List.of("urn:lsid:marinespecies.org:taxname:100000", "Ähre-Ü", "", "x".repeat(3000), "0");
    try (var builder = new UsageMatcherFileStoreBuilder(82, newDir())) {
      int i = 0;
      for (var id : ids) {
        builder.add(snc(id, null, "Aus bus", "Smith", Rank.SPECIES, ++i, i));
      }
      try (var store = builder.seal()) {
        assertEquals(ids.size(), store.size());
        for (var id : ids) {
          assertEquals(id, store.get(id).getId());
        }
        assertNotFound("nope", store);
      }
    }
  }

  /**
   * Random ids against a reference map: the static hash table has to resolve every collision and probe
   * wrap correctly, and each record has to be sliced out of the blob at exactly the right offset.
   */
  @Test
  public void randomRoundTrip() throws IOException {
    var rnd = new java.util.Random(42);
    var expected = new java.util.HashMap<String, String>();
    try (var builder = new UsageMatcherFileStoreBuilder(84, newDir())) {
      for (int i = 0; i < 5000; i++) {
        String id = Long.toHexString(rnd.nextLong()) + "-" + i;
        String name = "Aus " + rnd.nextInt(1000);
        expected.put(id, name);
        builder.add(snc(id, null, name, "Smith, " + (1800 + rnd.nextInt(200)), Rank.SPECIES, 1 + i % 700, i));
      }
      try (var store = builder.seal()) {
        assertEquals(expected.size(), store.size());
        for (var e : expected.entrySet()) {
          assertEquals(e.getValue(), store.get(e.getKey()).getName());
        }
        assertEquals(700, store.canonicalSize());
        int candidates = 0;
        for (var canonId : store.allCanonicalIds()) {
          candidates += store.simpleNamesByCanonicalId(canonId).size();
        }
        assertEquals("every usage is a candidate under exactly one canonical", expected.size(), candidates);
        assertNotFound("not-in-there", store);
      }
    }
  }

  /**
   * The order of the usages within a canonical bucket is the order they were added in. Not cosmetic:
   * IdProvider.issueIDs walks the bucket in this order and which usage reuses which released id follows
   * from it, so a reordering silently reshuffles stable ids across a release.
   */
  @Test
  public void canonicalOrderIsInsertionOrder() throws IOException {
    try (var builder = new UsageMatcherFileStoreBuilder(86, newDir())) {
      // interleaved, so the two buckets are not contiguous runs in the input
      for (int i = 0; i < 50; i++) {
        builder.add(snc("a" + i, null, "Aus bus", "Smith", Rank.SPECIES, 1, 1));
        builder.add(snc("b" + i, null, "Bus aus", "Miller", Rank.SPECIES, 2, 2));
      }
      try (var store = builder.seal()) {
        var expectedA = new java.util.ArrayList<String>();
        var expectedB = new java.util.ArrayList<String>();
        for (int i = 0; i < 50; i++) {
          expectedA.add("a" + i);
          expectedB.add("b" + i);
        }
        assertEquals(expectedA, store.simpleNamesByCanonicalId(1).stream().map(x -> x.getId()).toList());
        assertEquals(expectedB, store.simpleNamesByCanonicalId(2).stream().map(x -> x.getId()).toList());
      }
    }
  }

  /** Usages without a names index match are stored, but are not candidates for anything. */
  @Test
  public void usagesWithoutCanonical() throws IOException {
    try (var builder = new UsageMatcherFileStoreBuilder(85, newDir())) {
      var unmatched = snc("a", null, "Aus?", null, Rank.UNRANKED, 1, 1);
      unmatched.setCanonicalId(null);
      unmatched.setNamesIndexId(null);
      builder.add(unmatched);
      builder.add(snc("b", null, "Bus", "Smith", Rank.GENUS, 2, 2));
      try (var store = builder.seal()) {
        assertEquals(2, store.size());
        assertEquals(1, store.canonicalSize());
        assertNull(store.get("a").getCanonicalId());
        assertEquals("Aus?", store.get("a").getName());
        assertEquals(2, stream(store.all()).count());
        assertEquals(java.util.List.of("b"), store.simpleNamesByCanonicalId(2).stream().map(x -> x.getId()).toList());
      }
    }
  }

  /** A directory holding files of the previous chronicle based store is not mistaken for a store. */
  @Test
  public void foreignDirectoryIsNoStore() throws IOException {
    var dir = newDir();
    assertFalse(UsageMatcherFileStore.isStore(dir));
    for (var fn : new String[]{"usages", "canonical"}) {
      try (var out = new FileOutputStream(new File(dir, fn))) {
        out.write(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9});
      }
    }
    assertFalse(UsageMatcherFileStore.isStore(dir));
    assertThrows(IOException.class, () -> UsageMatcherFileStore.open(83, dir));
  }

  /** Builds a small but complete store of 20 usages across 7 canonicals. */
  private File buildSmallStore() throws IOException {
    var dir = newDir();
    try (var builder = new UsageMatcherFileStoreBuilder(91, dir)) {
      for (int i = 0; i < 20; i++) {
        builder.add(snc("u" + i, i == 0 ? null : "u0", "Aus bus" + i, "Smith", Rank.SPECIES, 100 + i % 7, 500 + i));
      }
      builder.seal().close();
    }
    return dir;
  }

  /**
   * A truncated file must be recognised as corrupt while opening rather than blowing up with an
   * IndexOutOfBoundsException on some later match request.
   */
  @Test
  public void truncatedFilesAreRejected() throws IOException {
    for (var fn : new String[]{UsageMatcherFileStore.USAGES_FILE, UsageMatcherFileStore.CANONICAL_FILE, UsageMatcherFileStore.GROUPS_FILE}) {
      var dir = buildSmallStore();
      var f = new File(dir, fn);
      long full = f.length();
      assertTrue(fn + " is only " + full + " bytes", full > 32); // groups.bin is the smallest at 16 + n
      // chop off the last 8 bytes - header, magic and version all still intact
      try (var ch = java.nio.channels.FileChannel.open(f.toPath(), java.nio.file.StandardOpenOption.WRITE)) {
        ch.truncate(full - 8);
      }
      // isStore must agree, or reconcile would skip the broken store and it would stay unavailable forever
      assertFalse("truncated " + fn + " must not pass isStore", UsageMatcherFileStore.isStore(dir));
      assertThrows("truncated " + fn + " must not open", IOException.class, () -> UsageMatcherFileStore.open(91, dir));
    }
  }

  /** Extra trailing bytes mean the file is not the one the header describes either. */
  @Test
  public void trailingGarbageIsRejected() throws IOException {
    for (var fn : new String[]{UsageMatcherFileStore.USAGES_FILE, UsageMatcherFileStore.CANONICAL_FILE, UsageMatcherFileStore.GROUPS_FILE}) {
      var dir = buildSmallStore();
      try (var out = new FileOutputStream(new File(dir, fn), true)) {
        out.write(new byte[16]);
      }
      assertFalse("padded " + fn + " must not pass isStore", UsageMatcherFileStore.isStore(dir));
      assertThrows("padded " + fn + " must not open", IOException.class, () -> UsageMatcherFileStore.open(91, dir));
    }
  }

  /**
   * A bogus header count must be caught up front. A hash table smaller than the key count would make the
   * linear probe of a missing key loop forever, which no amount of bounds checking would catch.
   */
  @Test
  public void corruptHeaderCountsAreRejected() throws IOException {
    // usage count, usage table size, live count, canonical count, canonical table size, canonical ref count
    int[][] cases = {
      {0, 8, 21}, {0, 12, 8}, {0, 12, 15}, {0, 16, 21}, {0, 8, -1},
      {1, 8, 99}, {1, 12, 4}, {1, 16, 3}
    };
    for (int[] c : cases) {
      var dir = buildSmallStore();
      var f = new File(dir, c[0] == 0 ? UsageMatcherFileStore.USAGES_FILE : UsageMatcherFileStore.CANONICAL_FILE);
      writeLeInt(f, c[1], c[2]);
      String msg = "header " + c[0] + "@" + c[1] + "=" + c[2];
      assertFalse(msg + " must not pass isStore", UsageMatcherFileStore.isStore(dir));
      assertThrows(msg + " must not open", IOException.class, () -> UsageMatcherFileStore.open(91, dir));
    }
  }

  /** The group column must describe the same number of usages as the record file. */
  @Test
  public void mismatchedGroupColumnIsRejected() throws IOException {
    var dir = buildSmallStore();
    writeLeInt(new File(dir, UsageMatcherFileStore.GROUPS_FILE), 8, 19);
    assertFalse(UsageMatcherFileStore.isStore(dir));
    assertThrows(IOException.class, () -> UsageMatcherFileStore.open(91, dir));
  }

  /**
   * A store reopened from disk is entirely read only, so a stray group write fails loudly rather than
   * racing another process through the shared mapping. Only the builder hands out a writable store.
   */
  @Test
  public void reopenedStoreIsReadOnly() throws IOException {
    var dir = buildSmallStore();
    try (var store = UsageMatcherFileStore.open(91, dir)) {
      assertThrows(IllegalStateException.class, () -> store.update("u3", TaxGroup.Plants));
      assertThrows(IllegalStateException.class, () -> store.analyze(new TaxGroupAnalyzer()));
      assertEquals("groups must be unchanged", null, store.get("u3").getGroup());
    }
    // ... while the store the builder hands back may still be analyzed
    try (var store = UsageMatcherFileStore.openWritable(91, dir)) {
      store.update("u3", TaxGroup.Plants);
      assertEquals(TaxGroup.Plants, store.get("u3").getGroup());
    }
    try (var store = UsageMatcherFileStore.open(91, dir)) {
      assertEquals("the write must have reached disk", TaxGroup.Plants, store.get("u3").getGroup());
    }
  }

  /** A read only store must open even when the directory itself cannot be written to. */
  @Test
  public void opensOnAReadOnlyDirectory() throws IOException {
    var dir = buildSmallStore();
    var files = new File[]{new File(dir, UsageMatcherFileStore.USAGES_FILE),
      new File(dir, UsageMatcherFileStore.CANONICAL_FILE), new File(dir, UsageMatcherFileStore.GROUPS_FILE)};
    try {
      for (var f : files) {
        assumeTrue("cannot drop write permission", f.setWritable(false, false));
      }
      try (var store = UsageMatcherFileStore.open(91, dir)) {
        assertEquals(20, store.size());
        assertEquals("Aus bus7", store.get("u7").getName());
      }
    } finally {
      // restore no matter how we leave, or the temp dir cannot be cleaned up
      for (var f : files) f.setWritable(true, false);
    }
  }

  /** An intact store still opens - the validation must not be over eager. */
  @Test
  public void validStoreStillOpens() throws IOException {
    var dir = buildSmallStore();
    assertTrue(UsageMatcherFileStore.isStore(dir));
    try (var store = UsageMatcherFileStore.open(91, dir)) {
      assertEquals(20, store.size());
      assertEquals(7, store.canonicalSize());
      assertEquals("Aus bus13", store.get("u13").getName());
    }
  }

  private static void writeLeInt(File f, long pos, int value) throws IOException {
    try (var ch = java.nio.channels.FileChannel.open(f.toPath(), java.nio.file.StandardOpenOption.WRITE)) {
      var bb = java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(value).flip();
      ch.write(bb, pos);
    }
  }
}
