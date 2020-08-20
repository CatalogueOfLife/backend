package life.catalogue.admin;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static life.catalogue.admin.MetricsUpdater.SectorAttempt;
import static org.junit.Assert.assertEquals;

public class MetricsUpdaterTest {

  @Test
  public void testSectorAttempt() {
    Set<SectorAttempt> set = new HashSet<>();
    set.add(new SectorAttempt(1,2,3));
    set.add(new SectorAttempt(1,2,2));
    set.add(new SectorAttempt(1,1,1));
    set.add(new SectorAttempt(1,2,3));

    assertEquals(3, set.size());
  }
}