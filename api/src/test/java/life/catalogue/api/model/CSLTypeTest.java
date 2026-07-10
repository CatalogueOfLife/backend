package life.catalogue.api.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pure COL-side checks for the COL-owned CSLType enum. The 1:1 mirror against citeproc is
 * verified in the reference module (CslTypeConverterTest), which has citeproc on the classpath. */
public class CSLTypeTest {

  @Test
  public void fromStringRoundTrip() {
    assertEquals(CSLType.ARTICLE_JOURNAL, CSLType.fromString("article-journal"));
    assertEquals(CSLType.DATASET, CSLType.fromString("dataset"));
    assertNull(CSLType.fromString("not-a-type"));
  }
}
