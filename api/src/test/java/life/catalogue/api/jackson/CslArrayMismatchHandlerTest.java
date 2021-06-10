package life.catalogue.api.jackson;

import life.catalogue.api.model.CslData;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.junit.Test;

import com.fasterxml.jackson.core.type.TypeReference;

import de.undercouch.citeproc.csl.CSLType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CslArrayMismatchHandlerTest {
  /**
   * Avoid failing when string properties are given as arrays
   */
  @Test
  public void jacksonDeserde() throws IOException {
    InputStream in = ClassLoader.getSystemResourceAsStream("reference.json");
    TypeReference<List<CslData>> cslType = new TypeReference<List<CslData>>(){};
    List<CslData> refs = ApiModule.MAPPER.readValue(in, cslType);
    
    CslData r = refs.get(0);
    assertEquals("The Global Genome Biodiversity Network (GGBN) Data Standard specification", r.getTitle());
    assertEquals("GGBN Standard", r.getTitleShort());
    assertEquals("10.1093/database/baw125", r.getDOI());
    assertEquals("http://dx.doi.org/10.1093/database/baw125", r.getURL());
    assertEquals("1758-0463", r.getISSN());
    assertEquals(CSLType.ARTICLE_JOURNAL, r.getType());
  
    r = refs.get(1);
    // should not fail
    assertNull(r.getType());
  }
}