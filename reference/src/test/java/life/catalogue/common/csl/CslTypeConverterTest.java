package life.catalogue.common.csl;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Guards that the COL-owned CSLType mirrors citeproc's enum 1:1 in name and JSON id.
 * Lives in the reference module because it needs citeproc on the classpath.
 */
public class CslTypeConverterTest {

  @Test
  public void mirrorsCiteproc() {
    for (de.undercouch.citeproc.csl.CSLType cp : de.undercouch.citeproc.csl.CSLType.values()) {
      var col = CslTypeConverter.fromCiteproc(cp);
      assertNotNull("COL CSLType is missing citeproc value " + cp.name(), col);
      assertEquals("JSON id mismatch for " + cp.name(), cp.toString(), col.toString());
    }
    // and every COL value maps back to a citeproc value
    for (var col : life.catalogue.api.model.CSLType.values()) {
      assertNotNull("citeproc has no value for COL " + col.name(), CslTypeConverter.toCiteproc(col));
    }
  }
}
