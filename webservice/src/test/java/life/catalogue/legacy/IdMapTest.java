package life.catalogue.legacy;

import life.catalogue.common.io.Resources;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class IdMapTest {

  @Test
  public void reload() throws IOException {
    IdMap map = new IdMap(null, null);
    map.start();
    assertEquals(0, map.size());
    map.reload(Resources.stream("idmap-test.tsv"));

    assertEquals(4, map.size());
    assertEquals("HEX", map.lookup("200f52638f6d098b26df65d3b26f0f09"));
  }
}