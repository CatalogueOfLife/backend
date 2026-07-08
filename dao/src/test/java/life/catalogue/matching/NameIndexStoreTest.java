package life.catalogue.matching;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.IndexName;
import life.catalogue.common.io.TempFile;
import life.catalogue.matching.nidx.NameIndexStore;
import life.catalogue.matching.nidx.NamesIndexConfig;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

abstract class NameIndexStoreTest {
  AtomicInteger keyGen = new AtomicInteger();
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
  public void size() throws Exception {
    assertEquals(0, db.count());

    addNameList("a", 1);
    assertEquals(1, db.count());

    addNameList("b", 2); // 2,3
    assertEquals(3, db.count());

    addNameList("c", 3); // 4,5,6
    assertEquals(6, db.count());

    addNameList("a", 3); // 7,8,9
    assertEquals(9, db.count());

    // add the same id, this should not increase the size
    addName("a", 1);
    assertEquals(9, db.count());

    // now shutdown and reopen
    db.stop();
    db = create();
    db.start();

    assertEquals(9, db.count());

  }

  @Test
  public void get() throws Exception {
    addName("b", 10, 10); // the canonical itself
    addName("b", 12, 10);
    addName("b", 13, 10);

    assertNotNullProps(db.get(10));
    assertNotNullProps(db.get(12));
    assertNotNullProps(db.get(13));
  }

  @Test
  public void byCanonical() throws Exception {
    addNameList("a", 4);

    addName("b", 10, 10); // the canonical itself
    addName("b", 12, 10);
    addName("b", 13, 10);
    assertEquals(7, db.count());

    // single-tier: the store no longer groups qualified names under a canonical key -
    // byCanonical always returns an empty collection now, regardless of the key given
    assertTrue(db.byCanonical(1).isEmpty());
    assertTrue(db.byCanonical(10).isEmpty());
  }

  @Test
  public void delete() throws Exception {
    addNameList("a", 4);

    addName("b", 10, 10); // the canonical itself
    addName("b", 12, 10);
    addName("b", 13, 10);
    assertEquals(7, db.count());

    // single-tier: no canonical->children grouping is maintained anymore
    assertTrue(db.byCanonical(10).isEmpty());

    db.delete(12, u -> "b");
    assertEquals(6, db.count());
    assertNull(db.get(12));
    assertNotNullProps(db.get(10));
    assertNotNullProps(db.get(13));

    // single-tier: deleting the canonical no longer cascades to remove other entries -
    // there are no qualified children left to cascade to
    db.delete(10, u -> "b");
    assertEquals(5, db.count());
    assertNull(db.get(10));
    assertNotNullProps(db.get(13));
  }

  @Test
  public void compact() throws Exception {
    addNameList("a", 4);

    addName("b", 10, 10); // the canonical itself
    addName("b", 12, 10);
    addName("b", 13, 10);
    addName("b", 12, 10);
    assertEquals(7, db.count());

    // single-tier: compact() is now a no-op since the canonical->children multimap it used to
    // dedupe is gone - just verify it runs without error and leaves the store unchanged
    db.compact();
    assertEquals(7, db.count());
    assertTrue(db.byCanonical(10).isEmpty());
  }

  private void addName(String key, int id) {
    addName(key, id, id);
  }

  private void addName(String key, int id, Integer canonicalID) {
    IndexName n = new IndexName(TestEntityGenerator.newName());
    n.setKey(id);
    db.add(key, n);
  }

  private void addNameList(String key, int size) {
    for (int idx = 0; idx<size; idx++) {
      IndexName n = new IndexName(TestEntityGenerator.newName());
      n.setKey(keyGen.incrementAndGet());
      db.add(key, n);
    }
  }

  private void assertNotNullProps(Iterable<IndexName> ns){
    for (var n : ns) {
      assertNotNullProps(n);
    }
  }

  private void assertNotNullProps(IndexName n){
    assertNotNull(n.getKey());
    assertNotNull(n.getScientificName());
    // single-tier: rank is always the canonical UNRANKED constant
    assertNotNull(n.getRank());
  }

  @Test
  public void close() {
  }
}
