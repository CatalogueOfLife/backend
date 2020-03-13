package life.catalogue.api.vocab;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class FrequencyTest {
  
  @Test
  public void getDays() {
    for (Frequency f : Frequency.values()) {
      assertNotNull(f.getDays());
    }
  }
  
  @Test
  public void fromDays() {
    assertEquals(Frequency.WEEKLY, Frequency.fromDays(null));
    assertEquals(Frequency.WEEKLY, Frequency.fromDays(3456789));
    for (Frequency f : Frequency.values()) {
      assertEquals(f, Frequency.fromDays(f.getDays()));
    }
  }
}