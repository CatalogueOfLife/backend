package life.catalogue.common.csl;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Agent;
import life.catalogue.api.model.Citation;
import life.catalogue.api.model.CitationFormatter;
import life.catalogue.api.model.CitationTest;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.area.Country;
import life.catalogue.common.date.FuzzyDate;

import java.net.URI;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Citation-output regression tests relocated from the api module's CitationTest / DatasetTest,
 * where the citeproc converters and formatter now live. The CslCitationFormatter is registered so
 * the model's getCitation()/getCitationText() hook resolves to real APA strings.
 */
public class CitationConverterTest {

  @BeforeClass
  public static void registerFormatter() {
    CitationFormatter.register(new CslCitationFormatter());
    TestEntityGenerator.CSL_CITATION_BUILDER = CslUtil::buildCitation;
  }

  @Test
  public void citationToCSL() {
    var csl = CitationConverter.toCSL(CitationTest.create());
    assertNotNull(csl);
  }

  @Test
  public void datasetToCSL() {
    Dataset d = new Dataset();
    d.setKey(1000);
    d.setOrigin(DatasetOrigin.EXTERNAL);
    d.setTitle("Catalogue of the Alucitoidea of the World");
    d.setCreator(Agent.parse(List.of("Hobern, Donald", "Gielis, C.")));
    d.setEditor(Agent.parse(List.of("Hobern, Donald", "Hobern, Markus")));
    d.setVersion("1.0.21.199 (18 Jul 2021)");
    d.setIssued(FuzzyDate.of(2021,7,18));
    d.setUrl(URI.create("https://alucitoidea.hobern.net"));
    d.setContainerTitle("Catalogue of Life Checklist");
    d.setContainerCreator(Agent.parse(List.of("Banki, Olaf", "Roskov, Yuri")));

    var csl = DatasetCitationConverter.toCSL(d);
    assertEquals(3, csl.getAuthor().length);
    assertNull(csl.getEditor());
    assertEquals("Catalogue of Life Checklist", csl.getContainerTitle());
  }

  @Test
  public void datasetSourceCitationViaHook() {
    Dataset d = new Dataset();
    d.setKey(1000);
    d.setOrigin(DatasetOrigin.EXTERNAL);
    d.setTitle("Catalogue of the Alucitoidea of the World");
    d.setCreator(Agent.parse(List.of("Hobern, Donald", "Gielis, C.")));
    d.setEditor(Agent.parse(List.of("Hobern, Donald")));
    d.setUrl(URI.create("https://alucitoidea.hobern.net"));
    d.setVersion("Annual Edition 2024");
    d.setIssued(FuzzyDate.of(2024,6,18));
    d.setPublisher(Agent.organisation("Catalogue of Life", null, "Amsterdam", null, Country.NETHERLANDS));
    d.setContainerTitle("Catalogue of Life");
    d.setContainerCreator(Agent.parse(List.of("Banki, Olaf", "Roskov, Yuri")));

    // hook wiring: model getters resolve through the registered CslCitationFormatter
    assertEquals(CslUtil.buildCitation(DatasetCitationConverter.toCSL(d)), d.getCitationText());
    assertNotNull(d.getCitation());
  }

  static Dataset source() {
    Dataset d = new Dataset();
    d.setKey(1000);
    d.setOrigin(DatasetOrigin.EXTERNAL);
    d.setTitle("World Database of Nematodes");
    d.setCreator(Agent.parse(List.of("Deprez, Tim")));
    d.setVersion("2024-01-01");
    d.setIssued(FuzzyDate.of(2024,1,1));
    d.setContainerTitle("Catalogue of Life");
    d.setContainerVersion("2026-08-26 XR");
    d.setContainerIssued(FuzzyDate.of(2026,8,26));
    d.setContainerPublisher(Agent.organisation("Catalogue of Life Foundation", null, "Amsterdam", null, Country.NETHERLANDS));
    return d;
  }

  static String cite(Dataset d) {
    return CslUtil.buildCitation(CitationConverter.toCSL(d.toCitation()));
  }

  /**
   * Exports drop the container creators from every source citation, see ArchiveExport.exportMetadata.
   * Everything else about the container has to survive that - it is what says which release a source DOI
   * belongs to.
   */
  @Test
  public void sourceCitationWithoutContainerCreators() {
    Dataset d = source();
    d.setContainerCreator(Agent.parse(List.of("Banki, Olaf", "Roskov, Yuri")));
    assertEquals("Deprez, T. (2026). World Database of Nematodes. In O. Banki & Y. Roskov, Catalogue of Life " +
                 "(2026-08-26 XR). Catalogue of Life Foundation, Amsterdam, Netherlands.", cite(d));

    // only the container creators go, the container itself stays
    d.setContainerCreator(null);
    assertEquals("Deprez, T. (2026). World Database of Nematodes. In Catalogue of Life " +
                 "(2026-08-26 XR). Catalogue of Life Foundation, Amsterdam, Netherlands.", cite(d));
  }

  /**
   * Why those container creators are waste rather than data: apa.csl declares et-al-min=21 and
   * et-al-use-first=19, so any list of 21 or more renders as exactly the same 19 names plus "et al.".
   * Storing 100 of them on every source of a release, as exports used to, can never show more than these.
   */
  @Test
  public void containerCreatorsBeyondTheEtAlCutoffAreNeverRendered() {
    Dataset d = source();
    assertEquals(cite(withContainerCreators(d, 21)), cite(withContainerCreators(d, 100)));
    // one below the cut-off APA still lists everybody, so 21 is the smallest equivalent list
    assertNotEquals(cite(withContainerCreators(d, 20)), cite(withContainerCreators(d, 21)));
  }

  private static Dataset withContainerCreators(Dataset d, int size) {
    var names = new java.util.ArrayList<String>(size);
    for (int i = 0; i < size; i++) {
      // the name parser wants plain letters, so number the agents alphabetically
      String n = "" + (char) ('a' + i / 26) + (char) ('a' + i % 26);
      names.add(String.format("Sur%s, Giv%s", n, n));
    }
    d.setContainerCreator(Agent.parse(names));
    return d;
  }

  @Test
  public void citationHook() {
    Citation c = CitationTest.create();
    assertEquals(CslUtil.buildCitation(CitationConverter.toCSL(c)), c.getCitationText());
    assertEquals(CslUtil.buildCitationHtml(CitationConverter.toCSL(c)), c.getCitation());
  }
}
