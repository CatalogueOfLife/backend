package life.catalogue.resources.legacy;

import life.catalogue.common.io.Resources;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class IdMapTest {

  File dbFile;
  String resource;
  IdMap map;

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    final File file = new File("/tmp/idmap.bin");
    final String resource = "idmap-test.tsv";
    Object[] param1 = new Object[]{null, null};
    Object[] param2 = new Object[]{null, resource};
    Object[] param3 = new Object[]{file, null};
    Object[] param4 = new Object[]{file, resource};
    return List.of(param1, param2, param3, param4);
  }

  public IdMapTest(File dbFile, String resource) {
    this.dbFile = dbFile;
    this.resource = resource;
  }

  @Before
  public void init() throws Exception {
    if (resource != null) {
      map = IdMap.fromResource(dbFile, resource);
    } else {
      map = IdMap.empty(dbFile);
    }
    map.start();
  }

  @After
  public void cleanup() throws Exception {
    map.stop();
    if (dbFile != null && dbFile.exists()) {
      dbFile.delete();
    }
  }

  @Test
  public void reload() throws IOException {
    map.clear();
    assertEquals(0, map.size());
    map.reload(Resources.stream("idmap-test.tsv"));

    assertEquals(10, map.size());
    assertEquals("HEX", map.lookup("200f52638f6d098b26df65d3b26f0f09"));
    assertEquals("8K9W", map.lookup("4251802078a0d844bda6c62ae5a145aa"));
  }

  @Test
  public void hasStarted() throws IOException {
    assertTrue(map.hasStarted());

    map.stop();
    assertFalse(map.hasStarted());

    map.start();
    assertTrue(map.hasStarted());

    map.stop();
    assertFalse(map.hasStarted());

    map.start();
    assertTrue(map.hasStarted());
  }
}