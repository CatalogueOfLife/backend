package life.catalogue.common.csl;

import com.fasterxml.jackson.core.type.TypeReference;
import de.undercouch.citeproc.csl.CSLItemData;
import de.undercouch.citeproc.csl.CSLItemDataBuilder;
import de.undercouch.citeproc.csl.CSLType;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.CslData;
import life.catalogue.common.io.Resources;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class CslUtilTest {

  @Test
  public void makeBibliography() {
    for (int i = 0; i < 10; i++) {
      System.out.println(CslUtil.buildCitation(TestEntityGenerator.newReference()));
    }
  }
  
  @Test
  @Ignore("citeproc bug: https://github.com/michel-kraemer/citeproc-java/issues/101")
  public void buildCitation() throws IOException {
    InputStream in = Resources.stream("references/test.json");
    TypeReference<List<CslData>> cslType = new TypeReference<List<CslData>>(){};
    List<CslData> refs = ApiModule.MAPPER.readValue(in, cslType);

    // APA defines 6 authors before et al. <citation et-al-min="6"
    assertEquals("Droege, G., Barker, K., Seberg, O., Coddington, J., Benson, E., Berendsohn, W. G., et al. (2016). The Global Genome Biodiversity Network (GGBN) Data Standard specification. Database, 2016, baw125. https://doi.org/10.1093/database/baw125", CslUtil.buildCitation(refs.get(0)));
  }
  
  @Test
  public void performance() {
    CSLItemDataBuilder builder = new CSLItemDataBuilder()
        .type(CSLType.WEBPAGE)
        .abstrct("bcgenwgz ew hcehnuew")
        .title("my Title")
        .accessed(1999)
        .author("Markus", "Döring")
        .DOI("10.1093/database/baw125")
        .URL("gbif.org")
        .ISSN("1758-0463")
        .originalTitle("my orig tittel");
    
    CSLItemData csl = builder.build();
    assertEquals("Döring, M. my Title. https://doi.org/10.1093/database/baw125", CslUtil.buildCitation(csl));

    System.out.println("Start time measuring");
    StopWatch watch = StopWatch.createStarted();
    final int times = 100;
    for (int x=1; x<=times; x++){
      builder.title("my Title "+x);
      csl = builder.accessed(1900+x).build();
      assertEquals("Döring, M. my Title "+x+". https://doi.org/10.1093/database/baw125", CslUtil.buildCitation(csl));
    }
    watch.stop();
    System.out.println(watch);
    var performance = watch.getTime() / times;
    System.out.println(performance + "ms/citation");
  }

}