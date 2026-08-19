package life.catalogue.matching;

import life.catalogue.common.io.TempFile;
import life.catalogue.matching.nidx.NameIndexStore;
import life.catalogue.matching.nidx.NamesIndexConfig;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests the slim {@code normalized-String -> nidx-int} store interface.
 */
abstract class NameIndexStoreTest {
  NamesIndexConfig cfg;
  TempFile dir;
  NameIndexStore db;

  @Before
  public void init() throws Exception {
    cfg = new NamesIndexConfig();
    dir = TempFile.directory();
    cfg.file = dir.file;
    db = create();
    assertFalse(db.hasStarted());
    db.start();
    assertTrue(db.hasStarted());
  }

  @After
  public void cleanup() throws Exception {
    db.stop();
    assertFalse(db.hasStarted());
    dir.close();
  }

  abstract NameIndexStore create() throws IOException;

  @Test
  public void created() throws Exception {
    System.out.println(db.created());
    assertTrue(LocalDateTime.now().isAfter(db.created()));
    assertTrue(LocalDateTime.now().minus(1, ChronoUnit.MINUTES).isBefore(db.created()));
  }

  @Test
  public void addGetContains() throws Exception {
    assertEquals(0, db.count());
    assertEquals(0, db.get("nope"));
    assertFalse(db.contains("nope"));
    assertEquals(0, db.maxKey());

    db.add("abies alb", 1);
    assertEquals(1, db.count());
    assertEquals(1, db.get("abies alb"));
    assertTrue(db.contains("abies alb"));
    assertEquals(1, db.maxKey());
    assertEquals(0, db.get("nope"));
    assertFalse(db.contains("nope"));

    db.add("picea", 5);
    assertEquals(2, db.count());
    assertEquals(5, db.get("picea"));
    assertEquals(5, db.maxKey());

    // adding the same key again does not increase the size
    db.add("abies alb", 1);
    assertEquals(2, db.count());
    assertEquals(5, db.maxKey());
  }

  @Test
  public void entries() throws Exception {
    db.add("abies alb", 1);
    db.add("picea", 5);
    Map<String, Integer> found = new HashMap<>();
    for (var e : db.entries()) {
      found.put(e.getKey(), e.getValue());
    }
    assertEquals(2, found.size());
    assertEquals(Integer.valueOf(1), found.get("abies alb"));
    assertEquals(Integer.valueOf(5), found.get("picea"));
  }

  @Test
  public void clear() throws Exception {
    db.add("abies alb", 1);
    db.add("picea", 5);
    assertEquals(2, db.count());

    db.clear();
    assertEquals(0, db.count());
    assertEquals(0, db.maxKey());
    assertFalse(db.contains("abies alb"));
    assertEquals(0, db.get("abies alb"));
  }

  @Test
  public void compact() throws Exception {
    db.add("abies alb", 1);
    db.compact();
    assertEquals(1, db.count());
    assertEquals(1, db.get("abies alb"));
  }

  @Test
  public void close() {
  }
}
