package life.catalogue.api.vocab.area;

import java.net.URI;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GazetteerTest {

  @Test
  public void of() {
    for (Gazetteer g : Gazetteer.values()) {
      String prefix = g.prefix();
      assertEquals(g, Gazetteer.of(prefix));
      assertNotNull(g.getPattern());
    }
  }

  @Test
  public void normalizeFao() {
    // correct ids stay as they are
    for (String id : List.of("27", "27.1", "37.4.1", "21.0.A", "21.3.P.n", "27.12.c", "27.12.a.4", "27.3.d.28.2", "47.A.0", "58.4.3.a")) {
      assertEquals(id, Gazetteer.FAO.normalize(id));
      assertTrue(id, Gazetteer.FAO.getRegex().matcher(id).matches());
    }
    assertEquals("21.0.A", Gazetteer.FAO.normalize("21.0.a"));
    assertEquals("21.3.P.n", Gazetteer.FAO.normalize("21.3.p.N"));
    assertEquals("27.12.c", Gazetteer.FAO.normalize(" 27.12.C "));
    assertEquals("47.A.0", Gazetteer.FAO.normalize("47.a.0"));
    assertEquals("58.4.3.a", Gazetteer.FAO.normalize("58.4.3.A"));
    // a trailing dot stays and fails the pattern
    assertEquals("27.", Gazetteer.FAO.normalize("27."));
  }

  @Test
  public void normalizeRealm() {
    assertEquals("Neotropic", Gazetteer.REALM.normalize("Neotropic"));
    assertEquals("Neotropic", Gazetteer.REALM.normalize("neotropic"));
    assertEquals("Neotropic", Gazetteer.REALM.normalize(" NEOTROPIC "));
    assertEquals("TemperateSouthAmerica", Gazetteer.REALM.normalize("TemperateSouthAmerica"));
    assertEquals("TemperateSouthAmerica", Gazetteer.REALM.normalize("temperateSouthAmerica"));
    assertTrue(Gazetteer.REALM.getRegex().matcher("TemperateSouthAmerica").matches());
  }

  @Test
  public void testLink() throws Exception {
    Assert.assertEquals(URI.create("https://www.fao.org/fishery/en/area/27"), Gazetteer.FAO.getAreaLink("27"));
    Assert.assertEquals(URI.create("https://www.fao.org/fishery/en/area/27"), Gazetteer.FAO.getAreaLink("27.14"));
    Assert.assertEquals(URI.create("https://www.fao.org/fishery/en/area/29"), Gazetteer.FAO.getAreaLink("29.14.b.1"));
  }

}