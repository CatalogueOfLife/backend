package life.catalogue.api.vocab;

import java.net.URI;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GazetteerTest {

  @Test
  public void of() {
    for (Gazetteer g : Gazetteer.values()) {
      String prefix = g.prefix();
      assertEquals(g, Gazetteer.of(prefix));
    }
  }

  @Test
  public void testLink() throws Exception {
    Assert.assertEquals(URI.create("https://www.fao.org/fishery/en/area/27"), Gazetteer.FAO.getAreaLink("27"));
    Assert.assertEquals(URI.create("https://www.fao.org/fishery/en/area/27"), Gazetteer.FAO.getAreaLink("27.14"));
    Assert.assertEquals(URI.create("https://www.fao.org/fishery/en/area/29"), Gazetteer.FAO.getAreaLink("29.14.b.1"));
  }

}