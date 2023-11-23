package life.catalogue.db.type2;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import static org.junit.Assert.*;

public class HstoreRankCountTypeHandlerTest {


  @Test
  public void testSerde() {
    var h = new HstoreRankCountTypeHandler();
    assertEquals(Rank.SUBSECTION_BOTANY, h.toKey("SUBSECTION_BOTANY"));
    assertEquals(Rank.SUBSECTION_ZOOLOGY, h.toKey("SUBSECTION_ZOOLOGY"));
    assertEquals(Rank.SPECIES, h.toKey("SPECIES"));
  }
}