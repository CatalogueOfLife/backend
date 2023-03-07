package life.catalogue.common.collection;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CountMapTest {

  @Test
  public void testCounter(){
    CountEnumMap<Rank> cnt = new CountEnumMap<>(Rank.class);

    assertNull(cnt.get(Rank.FAMILY));
    cnt.inc(Rank.FAMILY);
    assertEquals(1, (int) cnt.get(Rank.FAMILY));
    cnt.inc(Rank.FAMILY, 20);
    assertEquals(21, (int) cnt.get(Rank.FAMILY));
    assertNull(cnt.get(Rank.SUPERFAMILY));
    cnt.inc(Rank.FAMILY, -21);
    assertEquals(0, (int) cnt.get(Rank.FAMILY));
  }
}