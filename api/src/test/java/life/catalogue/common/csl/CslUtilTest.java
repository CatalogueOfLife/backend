package life.catalogue.common.csl;

import com.fasterxml.jackson.core.type.TypeReference;
import de.undercouch.citeproc.csl.CSLItemData;
import de.undercouch.citeproc.csl.CSLItemDataBuilder;
import de.undercouch.citeproc.csl.CSLType;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.Agent;
import life.catalogue.api.model.Citation;
import life.catalogue.api.model.CslData;
import life.catalogue.api.model.Dataset;
import life.catalogue.common.io.Resources;
import life.catalogue.common.io.UTF8IoUtils;

import org.apache.commons.lang3.time.StopWatch;
import org.junit.Ignore;
import org.junit.Test;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class CslUtilTest {

  @Test
  public void makeBibliography() {
    for (int i = 0; i < 10; i++) {
      System.out.println(CslUtil.buildCitation(TestEntityGenerator.newReference()));
    }
  }

  @Test
  public void bibtex() throws Exception {
    var d = Dataset.read(Resources.stream("metadata/col.yaml"));
    System.out.println( CslUtil.toBibTexString(d.toCSL()) );

    System.out.println("\n" + CslUtil.buildCitation(d.toCSL()) );

    d.setCreator(List.of(d.getCreator().get(0), Agent.parse("et al.")));
    var csl = d.toCSL();
    System.out.println("\n" + CslUtil.buildCitationHtml(csl) );
  }


  @Test
  public void buildCitation() throws IOException {
    InputStream in = Resources.stream("references/test.json");
    TypeReference<List<CslData>> cslType = new TypeReference<List<CslData>>(){};
    List<CslData> refs = ApiModule.MAPPER.readValue(in, cslType);

    // APA defines 21 authors before et al. <bibliography et-al-min="21"
    assertEquals("Droege, G., Barker, K., Seberg, O., Coddington, J., Benson, E., Berendsohn, W. G., Bunk, B., Butler, C., Cawsey, E. M., Deck, J., Döring, M., Flemons, P., Gemeinholzer, B., Güntsch, A., Hollowell, T., Kelbert, P., Kostadinov, I., Kottmann, R., Lawlor, R. T., et al. (2016). The Global Genome Biodiversity Network (GGBN) Data Standard specification. Database, 2016, baw125. https://doi.org/10.1093/database/baw125", CslUtil.buildCitation(refs.get(0)));
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