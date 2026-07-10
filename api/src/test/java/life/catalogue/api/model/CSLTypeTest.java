package life.catalogue.api.model;

import de.undercouch.citeproc.csl.CSLType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Guards that the COL-owned CSLType mirrors citeproc's enum 1:1 in name and JSON id. */
public class CSLTypeTest {

  @Test
  public void mirrorsCiteproc() {
    for (CSLType cp : CSLType.values()) {
      life.catalogue.api.model.CSLType col =
        assertDoesNotThrow(() -> life.catalogue.api.model.CSLType.valueOf(cp.name()),
          "COL CSLType is missing citeproc value " + cp.name());
      assertEquals(cp.toString(), col.toString(),
        "JSON id mismatch for " + cp.name());
    }
    // and every COL value maps back to a citeproc value
    for (var col : life.catalogue.api.model.CSLType.values()) {
      assertDoesNotThrow(() -> CSLType.valueOf(col.name()),
        "citeproc has no value for COL " + col.name());
    }
  }

  @Test
  public void fromStringRoundTrip() {
    assertEquals(life.catalogue.api.model.CSLType.ARTICLE_JOURNAL,
      life.catalogue.api.model.CSLType.fromString("article-journal"));
    assertEquals(life.catalogue.api.model.CSLType.DATASET,
      life.catalogue.api.model.CSLType.fromString("dataset"));
    assertNull(life.catalogue.api.model.CSLType.fromString("not-a-type"));
  }
}
