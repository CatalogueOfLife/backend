package life.catalogue.common.csl;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.*;
import life.catalogue.common.collection.CollectionUtils;
import life.catalogue.common.io.Resources;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.time.StopWatch;
import org.junit.Test;

import com.fasterxml.jackson.core.type.TypeReference;

import de.undercouch.citeproc.csl.*;

import static org.junit.Assert.*;

public class CslUtilTest {

  @Test
  public void parseVolumeIssuePage() {
    assertEquals(Optional.of(new CslUtil.VolumeIssuePage(null, 1,2,3)), CslUtil.parseVolumeIssuePage("1(2):3"));
    assertEquals(Optional.of(new CslUtil.VolumeIssuePage(null, 1,2,3)), CslUtil.parseVolumeIssuePage("1 (2): 3"));
    assertEquals(Optional.of(new CslUtil.VolumeIssuePage(null, 1,2,3)), CslUtil.parseVolumeIssuePage("1 (2): p.3"));
    assertEquals(Optional.of(new CslUtil.VolumeIssuePage(null, 1,2,3)), CslUtil.parseVolumeIssuePage("1 (2): pp 3"));
    assertEquals(Optional.of(new CslUtil.VolumeIssuePage(null, 1,2,3)), CslUtil.parseVolumeIssuePage("1 (2): page 3"));

    assertEquals(Optional.of(new CslUtil.VolumeIssuePage(null,13,null,137)), CslUtil.parseVolumeIssuePage("13 : p 137"));
    assertEquals(Optional.of(new CslUtil.VolumeIssuePage("My great Journal.",13,null,137)), CslUtil.parseVolumeIssuePage("My great Journal. 13 : p 137"));
    assertEquals(Optional.empty(), CslUtil.parseVolumeIssuePage("My great Journal. (1848)"));
    assertEquals(Optional.of(new CslUtil.VolumeIssuePage("anything allowed here!+#\"§$%", 1,2,3)), CslUtil.parseVolumeIssuePage("anything allowed here!+#\"§$% 1 (2): p.3"));

    assertEquals(Optional.empty(), CslUtil.parseVolumeIssuePage("9781486300051"));
    assertEquals(Optional.empty(), CslUtil.parseVolumeIssuePage("1(48)9781486300051"));
  }

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

    d.setKey(999);
    assertTrue(CslUtil.toBibTexString(d.toCSL()).startsWith("@misc{999,"));

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

    // no csl data but the id should not return anything
    CslData data = new CslData();
    data.setId("1234");
    assertNull(CslUtil.buildCitation(data));

    // literal author example
    CslName ed = new CslName("Greuter,W. et al.");
    data.setEditor(new CslName[]{ed});
    data.setContainerTitle("Med-Checklist Vol.4 (published)");
    data.setIssued(new CslDate(1989));
    assertEquals("Greuter,W. et al. (1989). Med-Checklist Vol.4 (Published).", CslUtil.buildCitation(data));

    // volume issue pages
    data = new CslData();
    data.setAuthor(new CslName[]{new CslName("Werner", "Greuter")});
    data.setContainerTitle("J. Linn. Soc., Bot.");
    data.setIssued(new CslDate(1911));
    data.setVolume("10");
    data.setPage("141-156");
    assertEquals("Greuter, W. (1911). J. Linn. Soc., Bot., 10, 141–156.", CslUtil.buildCitation(data));

    data.setIssue("41");
    assertEquals("Greuter, W. (1911). J. Linn. Soc., Bot., 10(41), 141–156.", CslUtil.buildCitation(data));
    assertEquals("<div class=\"csl-entry\">Greuter, W. (1911). <span style=\"font-style: italic\">J. Linn. Soc., Bot.</span>, <span style=\"font-style: italic\">10</span>(41), 141&ndash;156.</div>", CslUtil.buildCitationHtml(data));
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

  static CSLName person(String family, String given) {
        return new CSLNameBuilder()
        .given(given)
        .family(family)
        .isInstitution(false)
        .build();
  }

  static CSLName[] persons(String... parts) {
    List<CSLName> names = new ArrayList<>();
    var iter = Arrays.stream(parts).iterator();
    while (iter.hasNext()) {
      names.add(person(iter.next(), iter.next()));
    }
    return names.toArray(new CSLName[0]);
  }

  static CSLDate date(int year, int month, int day) {
    return new CSLDateBuilder()
      .dateParts(new int[]{year, month, day})
      .build();
  }

  static CSLName[] colFirst(){
    return persons("Bánki", "Olaf", "Roskov", "Yuri", "Vandepitte", "Leen", "DeWalt", "R. E.", "Remsen", "David", "Schalk", "Peter", "Orrell", "Thomas", "Miller", "Joe");
  }

  static CSLName[] colSources(){
    return persons("Aalbu", "R.", "Adlard", "R.", "Adriaenssens", "E.", "Aedo", "C.", "Aescht", "E.", "Akkari", "N.", "Alonso-Zarazaga", "M. A.", "Alvarez", "B.", "Alvarez", "F.", "Anderson", "G.", "Angel", "M", "Döring", "Markus", "Stjernegaard Jeppesen", "Thomas", "Ower", "Geoff");
  }

  static CSLName[] colAll(){
    return CollectionUtils.concat(colFirst(), colSources());
  }

  @Test
  public void colSource() {
    CSLItemDataBuilder builder = new CSLItemDataBuilder()
      .type(CSLType.DATASET)
      .title("Catalogue of Life Checklist")
      .publisher("Catalogue of Life")
      .publisherPlace("Leiden, NL")
      .issued(date(2021,7,29))
      .version("2021-07-29")
      .author(colAll())
      .ISBN("2405-8858")
      .DOI("10.1093/database/baw125");

    System.out.println("\nMonthly July 2021");
    System.out.println(CslUtil.buildCitation(builder.build()));

    System.out.println("\nAnnual 2021");
    builder
      .version("Annual Checklist 2021");
    System.out.println(CslUtil.buildCitation(builder.build()));


    // SOURCE
    System.out.println("\nSource as chapter");
    builder
      .type(CSLType.CHAPTER)
      .title("3i World Auchenorrhyncha Database")
      .containerTitle("Catalogue of Life Checklist")
      .containerAuthor(colAll())
      //.author("D. A.", "Dmitriev")
      .author(persons("Dmitriev","D. A.", "McKamey", "S.", "Sanborn", "A.", "Takiya", "D. M.", "Zahniser","J."))
      .version("Jun 2021");
    System.out.println(CslUtil.buildCitation(builder.build()));
  }
}