package life.catalogue.matching;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.IndexName;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mapdb.DBMaker;

import static org.junit.Assert.*;

public class NameIndexMapDBStoreTest {
  AtomicInteger keyGen = new AtomicInteger();
  File dbf;
  DBMaker.Maker maker;
  NameIndexMapDBStore db;

  @Before
  public void init() throws Exception {
    dbf = File.createTempFile("colNidxStore",".db");
    dbf.delete();
    maker = DBMaker.fileDB(dbf).fileMmapEnableIfSupported();
    db = new NameIndexMapDBStore(maker, dbf);
    db.start();
  }

  @After
  public void cleanup() throws Exception {
    db.stop();
    dbf.delete();
  }

  @Test
  public void size() throws Exception {
    try {
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
      db = new NameIndexMapDBStore(maker, dbf);
      db.start();

      assertEquals(9, db.count());

    } finally {
      dbf.delete();
    }
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

    assertNull(db.byCanonical(1));
    var res = db.byCanonical(10);
    assertEquals(2, res.size());
    assertNotNullProps(res);
  }

  private void addName(String key, int id) {
    addName(key, id, null);
  }

  private void addName(String key, int id, Integer canonicalID) {
    IndexName n = new IndexName(TestEntityGenerator.newName());
    n.setKey(id);
    n.setCanonicalId(canonicalID);
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
    assertNotNull(n.getCanonicalId());
    assertNotNull(n.getScientificName());
    assertNotNull(n.getRank());
  }

  @Test
  public void close() {
  }
}