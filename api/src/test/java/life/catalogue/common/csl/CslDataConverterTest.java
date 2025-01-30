package life.catalogue.common.csl;

import de.undercouch.citeproc.bibtex.BibTeXConverter;
import de.undercouch.citeproc.csl.CSLDate;
import de.undercouch.citeproc.csl.CSLType;

import life.catalogue.api.model.CslData;
import life.catalogue.api.model.CslDate;

import org.jbibtex.Key;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import de.undercouch.citeproc.csl.CSLItemData;
import de.undercouch.citeproc.csl.CSLItemDataBuilder;

import static org.junit.Assert.*;

public class CslDataConverterTest {
  @Test
  @Ignore
  public void toCSLItemData() {
  }

  @Test
  public void typeRoundtrip() {
    var bibConv = new BibTeXConverter();
    for (var type : CSLType.values()) {
      var bibType = CslDataConverter.toBibTexType(type);
      assertNotNull(bibType);
      var cslType = bibConv.toType(new Key(bibType));
      System.out.println(type + " -> " + bibType + " -> "+ cslType);
      //assertEquals(type, cslType);
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
    Assert.assertEquals(csl.getTitle(), conv.getTitle());
    Assert.assertEquals(csl.getOriginalTitle(), conv.getOriginalTitle());
    
    Assert.assertEquals(csl.getDOI(), conv.getDOI());
    Assert.assertEquals(csl.getURL(), conv.getURL());
    Assert.assertEquals(csl.getISSN(), conv.getISSN());
  }


  private CSLItemDataBuilder testCslBuilder() {
    return new CSLItemDataBuilder()
      .abstrct("bcgenwgz ew hcehnuew")
      .title("my Title")
      .accessed(1999)
      .issued(2011)
      .author("Markus", "Döring")
      .DOI("10.1093/database/baw125")
      .URL("gbif.org")
      .ISSN("1758-0463")
      .originalTitle("my orig tittel");
  }

  @Test
  public void toBibTex() {
    CSLItemData csl = testCslBuilder().build();
    var bib = CslDataConverter.toBibTex(csl);
    assertNotNull(bib);

    // test with unusual null date arrays
    testCSLDate( null );
    testCSLDate( new CSLDate() );
    testCSLDate( new CSLDate(new int[][]{}, null, null, null, null) );
  }

  private void testCSLDate(CSLDate d) {
    var csl = testCslBuilder()
      .issued(d)
      .build();
    assertNotNull(CslDataConverter.toBibTex(csl));
  }

}