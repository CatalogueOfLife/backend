package life.catalogue.command;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import static life.catalogue.command.UpdMetricCmd.SectorAttempt;
import static org.junit.Assert.assertEquals;

public class UpdMetricCmdTest {

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