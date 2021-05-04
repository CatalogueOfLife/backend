package life.catalogue.common.concurrent;

import org.gbif.nameparser.api.Rank;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class UsageCounterTest {
  UsageCounter c;

  @Before
  public void init() {
    c = new UsageCounter();
  }

  @Test
  public void clear() {
    assertEquals(0, c.size());
    c.getTaxCounter().incrementAndGet();
    c.getSynCounter().incrementAndGet();
    assertEquals(2, c.size());
  }

  @Test
  public void set() {
    clear();

    UsageCounter c2 = new UsageCounter();
    c2.getTaxCounter().set(100);
    c2.getSynCounter().set(89);
    c2.getBareCounter().set(17);
    c2.putRankCount(Rank.SPECIES, 67);
    c2.putRankCount(Rank.GENUS, 21);

    c.set(c2);
    assertEquals(100, c.getTaxCounter().get());
    assertEquals(89, c.getSynCounter().get());
    assertEquals(17, c.getBareCounter().get());
    assertEquals(206, c.size());
    assertEquals(2, c.getRankCounter().size());
    assertEquals(67, c.getRankCounter().get(Rank.SPECIES).get());
    assertEquals(21, c.getRankCounter().get(Rank.GENUS).get());
  }

  @Test
  public void inc() {
  }

  @Test
  public void testInc() {
  }

  @Test
  public void testInc1() {
  }

  @Test
  public void testInc2() {
  }

  @Test
  public void getAll() {
  }

  @Test
  public void isEmpty() {
  }
}