package life.catalogue.common.collection;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CountMapTest {

  @Test
  public void testCounter(){
    CountMap<Rank> cnt = new CountMap<>();

    assertNull(cnt.get(Rank.FAMILY));
    cnt.inc(Rank.FAMILY);
    assertEquals(1, (int) cnt.get(Rank.FAMILY));
    cnt.inc(Rank.FAMILY, 20);
    assertEquals(21, (int) cnt.get(Rank.FAMILY));
    assertNull(cnt.get(Rank.SUPERFAMILY));
    cnt.inc(Rank.FAMILY, -21);
    assertEquals(0, (int) cnt.get(Rank.FAMILY));

    cnt.dec(Rank.FAMILY, 6);
    assertEquals(-6, (int) cnt.get(Rank.FAMILY));
    cnt.dec(Rank.FAMILY);
    assertEquals(-7, (int) cnt.get(Rank.FAMILY));

    cnt.multiply(3);
    assertEquals(-21, (int) cnt.get(Rank.FAMILY));

    CountMap<Rank> cnt2 = new CountMap<>();
    assertNull(cnt2.get(Rank.FAMILY));
    assertNull(cnt2.get(Rank.GENUS));

    cnt2.inc(Rank.GENUS);
    assertNull(cnt2.get(Rank.FAMILY));
    assertEquals(1, (int) cnt2.get(Rank.GENUS));

    cnt2.inc(cnt);
    assertEquals(-21, (int) cnt2.get(Rank.FAMILY));
    assertEquals(1, (int) cnt2.get(Rank.GENUS));
  }

}