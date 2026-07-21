package life.catalogue.matching;

import life.catalogue.common.io.TempFile;
import life.catalogue.matching.nidx.NameIndexChronicleStore;
import life.catalogue.matching.nidx.NameIndexStore;
import life.catalogue.matching.nidx.NamesIndexConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.map.ChronicleMapBuilder;

import org.junit.Test;

import static org.junit.Assert.*;

public class NameIndexChronicleStoreTest extends NameIndexStoreTest {

  @Override
  NameIndexStore create() throws IOException {
    return new NameIndexChronicleStore(cfg);
  }

  /**
   * The persistent chronicle store must survive a stop/reopen against the same directory.
   */
  @Test
  public void persistenceAcrossRestart() throws Exception {
    db.add("abies alb", 1);
    db.add("picea", 5);
    db.add("larus", 9);
    assertEquals(3, db.count());

    db.stop();
    db = create();
    db.start();

    assertEquals(3, db.count());
    assertEquals(9, db.maxKey());
    assertEquals(1, db.get("abies alb"));
    assertEquals(9, db.get("larus"));
  }

  /**
   * Writes a pre-c75da33c5 names file, which mapped a bucket key to an int[] of nidx ids.
   */
  private static void writeLegacyFile(File namesF) {
    try (ChronicleMap<String, int[]> legacy = ChronicleMapBuilder.of(String.class, int[].class)
           .name("names")
           .entries(1000)
           .averageKey("Abies alba")
           .averageValue(new int[]{3456, 2345, 657})
           .createPersistedTo(namesF)) {
      legacy.put("abies alb", new int[]{1});
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Writes a current-format names file holding a single entry.
   */
  private static void writeCurrentFile(File namesF, String key, int nidx) {
    try (ChronicleMap<String, Integer> m = ChronicleMapBuilder.of(String.class, Integer.class)
           .name("names")
           .entries(1000)
           .averageKey("Abies alba")
           .createPersistedTo(namesF)) {
      m.put(key, nidx);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Chronicle adopts the marshallers recorded in an existing file's header and ignores the value class
   * handed to the builder, so a legacy int[] file opens without error and only blows up when a value is
   * first read. Starting on one must fail with an actionable message, not a bare ClassCastException.
   */
  @Test
  public void legacyFileFailsFast() throws Exception {
    try (TempFile d = TempFile.directory()) {
      var c = new NamesIndexConfig();
      c.file = d.file;
      writeLegacyFile(new File(d.file, "names"));

      var store = new NameIndexChronicleStore(c);
      try {
        store.start();
        fail("expected a legacy format failure");
      } catch (IllegalStateException e) {
        assertTrue("message must name the file: " + e.getMessage(),
          e.getMessage().contains(d.file.getAbsolutePath()));
        assertTrue("message must tell the operator to rebuild: " + e.getMessage(),
          e.getMessage().toLowerCase().contains("rebuild"));
      }
      assertFalse(store.hasStarted());
    }
  }

  /**
   * Regression: a start() that fails after opening the chronicle map must not leak it. Chronicle hands
   * back an already-open instance for the same file, so a leaked map means every later start() re-reads
   * the stale mapping - swapping in a corrected file could then only be recovered by a JVM restart.
   */
  @Test
  public void failedStartDoesNotLeakOpenMap() throws Exception {
    try (TempFile d = TempFile.directory()) {
      var c = new NamesIndexConfig();
      c.file = d.file;
      File namesF = new File(d.file, "names");
      writeLegacyFile(namesF);

      var store = new NameIndexChronicleStore(c);
      try {
        store.start();
        fail("expected a legacy format failure");
      } catch (IllegalStateException e) {
        // expected
      }

      // the operator swaps in a correctly built file and starts the component again
      try (TempFile good = TempFile.directory()) {
        File goodF = new File(good.file, "names");
        writeCurrentFile(goodF, "picea", 7);
        Files.copy(goodF.toPath(), namesF.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }

      store.start();
      assertTrue(store.hasStarted());
      assertEquals(7, store.get("picea"));
      assertEquals(7, store.maxKey());
      store.stop();
    }
  }
}
