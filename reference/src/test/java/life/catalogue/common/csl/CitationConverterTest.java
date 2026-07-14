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

  @Test
  public void citationHook() {
    Citation c = CitationTest.create();
    assertEquals(CslUtil.buildCitation(CitationConverter.toCSL(c)), c.getCitationText());
    assertEquals(CslUtil.buildCitationHtml(CitationConverter.toCSL(c)), c.getCitation());
  }
}
