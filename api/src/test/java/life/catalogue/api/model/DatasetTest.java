package life.catalogue.api.model;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.jackson.SerdeTestBase;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.License;
import life.catalogue.common.csl.CslUtil;
import life.catalogue.common.date.FuzzyDate;

import org.junit.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 *
 */
public class DatasetTest extends SerdeTestBase<Dataset> {
  
  public DatasetTest() {
    super(Dataset.class);
  }

  public static Dataset generateTestDataset() {
    Dataset d = new Dataset();
    d.setKey(12345);
    d.setSourceKey(12345);
    d.setDoi(DOI.test("123456789"));
    d.setIssued(FuzzyDate.of("1999-09-21"));
    d.setIdentifier(Map.of(
      "gbif", UUID.randomUUID().toString(),
      "col", "1001"
    ));
    d.setTitle("gfdscdscw");
    d.setDescription("gefzw fuewh gczew fw hfueh j ijdfeiw jfie eö.. few . few .");
    d.setOrigin(DatasetOrigin.EXTERNAL);
    d.setType(DatasetType.TAXONOMIC);
    d.setUrl(URI.create("www.gbif.org"));
    d.setLogo(URI.create("www.gbif.org"));
    d.setLicense(License.CC0);
    d.setGeographicScope("North Africa");
    d.setContact(Agent.contact("Helpdesk", "info@mailinator.com"));
    d.setCreator(new ArrayList<>(List.of(
      Agent.person("Roy", "Black"),
      Agent.person("Gerhard", "Höllerich"),
      Agent.organisation("The Honky Tonks")
    )));
    d.setEditor(new ArrayList<>(List.of(
      Agent.person("William N", "Eschmeyer"),
      Agent.person("Rudolf", "Van der Laan")
    )));
    d.setContributor(new ArrayList<>(List.of(
      Agent.person("Connie", "Francis"),
      Agent.person("Herbert", "Feuerstein")
    )));
    d.setSource(List.of(
      CitationTest.create(),
      CitationTest.create()
    ));
    d.setNotes("cuzdsghazugbe67wqt6c g cuzdsghazugbe67wqt6c g  nhjs");
    return d;
  }

  @Override
  public Dataset genTestValue() throws Exception {
    return generateTestDataset();
  }

  @Override
  protected void assertSerialisation(String json) {
    // dataset issued uses ISO style dates
    assertTrue(json.contains("\"1999-09-21\""));
    // CSL citation arrays
    assertTrue(json.contains("[[2024,11]]"));
  }

  @Test
  public void patch() throws Exception {
    Dataset d = genTestValue();

    Dataset patch = new Dataset();
    patch.setTitle("Grundig");
    patch.setAlias("grr");
    d.applyPatch(patch);

    assertEquals("Grundig", d.getTitle());
    assertEquals("grr", d.getAlias());
  }

  @Test
  public void applyPatch() {
    Dataset d = TestEntityGenerator.newDataset("Hallo Spencer");
    Dataset copy = new Dataset(d);
    Dataset patch = new Dataset();

    assertEquals(copy, d);

    // empty patch
    d.applyPatch(patch);
    assertEquals(copy, d);

    // single patch prop
    patch.setVersion("my version");
    copy.setVersion("my version");
    d.applyPatch(patch);
    assertEquals(copy, d);

    // key is ignored
    patch.setKey(345678);
    d.applyPatch(patch);
    assertEquals(copy, d);

    // other non metadata infos that should not be patched
    patch.setOrigin(DatasetOrigin.RELEASED);
    patch.setType(DatasetType.ARTICLE);
    patch.setSourceKey(1234);
    patch.setAttempt(13);
    d.applyPatch(patch);
    assertEquals(copy, d);
  }

  @Test
  public void testEmptyString() throws Exception {
    String json = ApiModule.MAPPER.writeValueAsString(genTestValue());
    json = json.replaceAll("www\\.gbif\\.org", "");
    json = json.replaceAll("cc0", "");
    
    Dataset d = ApiModule.MAPPER.readValue(json, Dataset.class);
    assertNull(d.getUrl());
    assertNull(d.getLogo());
    assertNull(d.getLicense());
  }

  @Test
  public void testColCitation() throws Exception {

  }

  @Test
  public void testSourceCitation() throws Exception {
    Dataset d = new Dataset();
    d.setKey(1000);
    d.setOrigin(DatasetOrigin.EXTERNAL);
    d.setTitle("Catalogue of the Alucitoidea of the World");
    d.setCreator(Agent.parse(List.of("Hobern, Donald", "Gielis, C.")));
    d.setEditor(Agent.parse(List.of("Hobern, Donald")));
    d.setVersion("1.0.21.199 (18 Jul 2021)");
    d.setIssued(FuzzyDate.of(2021,7,18));
    d.setUrl(URI.create("https://alucitoidea.hobern.net"));
    d.setDoi(DOI.col("e456fgvzb"));
    d.setContainerTitle("Catalogue of Life Checklist");
    d.setContainerCreator(Agent.parse(List.of("Banki, Olaf", "Roskov, Yuri")));

    System.out.println(CslUtil.buildCitation(d.toCSL()));
  }

  @Test
  public void toCSL() throws Exception {
    Dataset d = new Dataset();
    d.setKey(1000);
    d.setOrigin(DatasetOrigin.EXTERNAL);
    d.setTitle("Catalogue of the Alucitoidea of the World");
    d.setCreator(Agent.parse(List.of("Hobern, Donald", "Gielis, C.")));
    d.setEditor(Agent.parse(List.of("Hobern, Donald", "Hobern, Markus")));
    d.setVersion("1.0.21.199 (18 Jul 2021)");
    d.setIssued(FuzzyDate.of(2021,7,18));
    d.setUrl(URI.create("https://alucitoidea.hobern.net"));
    d.setDoi(DOI.col("e456fgvzb"));
    d.setContainerTitle("Catalogue of Life Checklist");
    d.setContainerCreator(Agent.parse(List.of("Banki, Olaf", "Roskov, Yuri")));

    var csl = d.toCSL();
    assertEquals(3, csl.getAuthor().length);
    assertNull(csl.getEditor());
    assertEquals("Catalogue of Life Checklist", csl.getContainerTitle());
  }

  @Test
  public void toCitation() throws Exception {
    Dataset d = new Dataset();
    d.setKey(1000);
    d.setOrigin(DatasetOrigin.EXTERNAL);
    d.setTitle("Catalogue of the Alucitoidea of the World");
    d.setCreator(Agent.parse(List.of("Hobern, Donald", "Gielis, C.")));
    d.setEditor(Agent.parse(List.of("Hobern, Donald", "Hobern, Markus")));
    d.setVersion("1.0.21.199 (18 Jul 2021)");
    d.setIssued(FuzzyDate.of(2021,7,18));
    d.setUrl(URI.create("https://alucitoidea.hobern.net"));
    d.setDoi(DOI.col("e456fgvzb"));
    d.setContainerTitle("Catalogue of Life Checklist");
    d.setContainerCreator(Agent.parse(List.of("Banki, Olaf", "Roskov, Yuri")));

    var csl = d.toCitation();
    assertEquals(3, csl.getAuthor().size());
    assertNull(csl.getEditor());
    assertEquals("Catalogue of Life Checklist", csl.getContainerTitle());
  }
}