package org.col.common.csl;

import de.undercouch.citeproc.csl.CSLItemData;
import de.undercouch.citeproc.csl.CSLItemDataBuilder;
import org.col.api.model.CslData;
import org.col.api.model.CslDate;
import org.col.api.vocab.CSLRefType;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

public class CslDataConverterTest {
  @Test
  @Ignore
  public void toCSLItemData() {
  }
  
  @Test
  public void toCSLType() {
    for (CSLRefType t : CSLRefType.values()) {
      assertNotNull(CslDataConverter.toCSLType(t));
    }
  }
  
  @Test
  public void toCSLDate() {
    assertNull(CslDataConverter.toCSLDate(null));
    CslDate d = new CslDate();
    assertNotNull(CslDataConverter.toCSLDate(d));
    d.setCirca(true);
    assertNotNull(CslDataConverter.toCSLDate(d));
    d.setSeason("spring");
    d.setRaw("my spring");
    assertNotNull(CslDataConverter.toCSLDate(d));
  }
  
  @Test
  public void toCslData() {
    assertNull(CslDataConverter.toCslData(null));
  
    CSLItemData csl = new CSLItemDataBuilder()
        .abstrct("bcgenwgz ew hcehnuew")
        .title("my Title")
        .accessed(1999)
        .author("Markus", "Döring")
        .DOI("10.1093/database/baw125")
        .URL("gbif.org")
        .ISSN("1758-0463")
        .originalTitle("my orig tittel")
        .build();
    CslData conv = CslDataConverter.toCslData(csl);
    assertNotNull(conv);
    assertEquals(csl.getTitle(), conv.getTitle());
    assertEquals(csl.getOriginalTitle(), conv.getOriginalTitle());
    
    //TODO: https://github.com/Sp2000/colplus-backend/issues/322
    assertEquals(csl.getDOI(), conv.getDOI());
    assertEquals(csl.getURL(), conv.getURL());
    assertEquals(csl.getISSN(), conv.getISSN());
  }
  
}