package life.catalogue.api.vocab;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class TdwgAreaTest {

  @Test
  public void build() {
    Set<String> ids = new HashSet<>();
    for (var a : TdwgArea.build()) {
      ids.add(a.getId());
      assertNotNull(a.getId());
      assertNotNull(a.getName());
      if (a.getLevel() == 1) {
        assertNull(a.getParent());
      } else {
        assertNotNull(a.getParent());
        var p = TdwgArea.of(a.getParent());
        assertEquals(a.getLevel()-1, p.getLevel());
        assertEquals(a.getParent(), p.getId());
      }
    }
    assertEquals(1039, ids.size());
    assertEquals(ids.size(), TdwgArea.AREAS.size());
  }
}