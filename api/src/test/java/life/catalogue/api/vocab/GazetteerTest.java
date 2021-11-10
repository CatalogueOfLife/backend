package life.catalogue.api.vocab;

import org.junit.Test;

import static org.junit.Assert.*;

public class GazetteerTest {

  @Test
  public void of() {
    for (Gazetteer g : Gazetteer.values()) {
      String prefix = g.prefix();
      assertEquals(g, Gazetteer.of(prefix));
    }
  }


}