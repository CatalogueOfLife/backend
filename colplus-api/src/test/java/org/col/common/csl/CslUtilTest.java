package org.col.common.csl;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import de.undercouch.citeproc.csl.CSLItemData;
import de.undercouch.citeproc.csl.CSLItemDataBuilder;
import org.col.api.TestEntityGenerator;
import org.col.api.jackson.ApiModule;
import org.col.api.model.CslData;
import org.col.common.io.Resources;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CslUtilTest {

  @Test
  public void makeBibliography() {
    for (int i = 0; i < 10; i++) {
      System.out.println(CslUtil.buildCitation(TestEntityGenerator.newReference()));
    }
  }
  
  @Test
  public void buildCitation() throws IOException {
    InputStream in = Resources.stream("references/test.json");
    TypeReference<List<CslData>> cslType = new TypeReference<List<CslData>>(){};
    List<CslData> refs = ApiModule.MAPPER.readValue(in, cslType);
    
    assertEquals("Droege, G., Barker, K., Seberg, O., Coddington, J., Benson, E., Berendsohn, W. G., et al. (2016). The Global Genome Biodiversity Network (GGBN) Data Standard specification. Database, 2016, baw125. https://doi.org/10.1093/database/baw125", CslUtil.buildCitation(refs.get(0)));
  }
  
  @Test
  public void cslCitation() {
    CSLItemDataBuilder builder = new CSLItemDataBuilder()
        .abstrct("bcgenwgz ew hcehnuew")
        .title("my Title")
        .accessed(1999)
        .author("Markus", "Döring")
        .DOI("10.1093/database/baw125")
        .URL("gbif.org")
        .ISSN("1758-0463")
        .originalTitle("my orig tittel");
    
    CSLItemData csl = builder.build();
    assertEquals("Döring, M. (n.d.). my Title. https://doi.org/10.1093/database/baw125", CslUtil.buildCitation(CslDataConverter.toCslData(csl)));
    
    for (int x=1; x<10; x++){
      csl = builder.title("title-"+x).build();
      assertEquals("Döring, M. (n.d.). title-"+x+". https://doi.org/10.1093/database/baw125", CslUtil.buildCitation(CslDataConverter.toCslData(csl)));
    }
  }
}