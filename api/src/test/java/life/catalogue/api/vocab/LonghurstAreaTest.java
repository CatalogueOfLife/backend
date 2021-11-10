package life.catalogue.api.vocab;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class LonghurstAreaTest {

  @Test
  public void getGlobalId() {
    var a = new LonghurstArea("FKLD", "Coastal - SW Atlantic Shelves Province", 'B', 5);
    assertEquals("longhurst:FKLD", a.getGlobalId());
  }

  @Test
  public void build() {
    Set<String> ids = new HashSet<>();
    for (var a : LonghurstArea.build()) {
      ids.add(a.getId());
      assertNotNull(a.getId());
      assertNotNull(a.getName());
    }
    assertEquals(54, ids.size());
    assertEquals(ids.size(), LonghurstArea.AREAS.size());
  }
}