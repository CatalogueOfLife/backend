package life.catalogue.api.model;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.jackson.SerdeTestBase;
import life.catalogue.api.vocab.Country;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.License;
import life.catalogue.common.csl.CslUtil;
import life.catalogue.common.date.FuzzyDate;

import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.*;

import org.junit.Test;

import static org.junit.Assert.*;


public class DatasetTest extends SerdeTestBase<Dataset> {
  private static Set<?> IGNORE = Set.of(License.class);

  public DatasetTest() {
    super(Dataset.class);
  }

  public static Dataset generateTestDataset() {
    Dataset d = new Dataset();
    d.setKey(12345);
    d.setSourceKey(12345);
    d.setDoi(DOI.test("123456789"));
    d.setIssued(FuzzyDate.of("1999-09-21"));
    d.setVersion("1999 v2");
    d.setIdentifier(Map.of(
      "gbif", UUID.randomUUID().toString(),
      "col", "1001"
    ));
    d.setUrlFormatter(Map.of(
      "name", "https://fishbase.mnhn.fr/summary/{ID}",
      "reference", "https://fishbase.mnhn.fr/references/FBRefSummary.php?ID={ID}"
    ));
    d.setConversion(new Dataset.UrlDescription("http://convert.me", "bli bla BLUB"));
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
      Agent.person("Roy", "Black", "roy@black.de", "0000-0003-4994-0653"),
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
  public void copyCOnstructor() throws Exception {
    Dataset d = TestEntityGenerator.newFullDataset(1234);
    var d2 = new Dataset(d);
    assertEquals(d, d2);

    var ud = new Dataset.UrlDescription(d.getConversion().getUrl(), d.getConversion().getDescription());
    d2.setConversion(ud);
    assertEquals(d, d2);

    ud = new Dataset.UrlDescription(URI.create("www.gbif.org"), d.getConversion().getDescription());
    d2.setConversion(ud);
    assertNotEquals(d, d2);
  }

  @Test
  public void version() throws Exception {
    Dataset d = new Dataset();
    assertNull(d.getVersion());
    assertNull(d.getIssued());

    d.setVersion("2345");
    assertEquals("2345", d.getVersion());
    assertNull(d.getIssued());

    d.setIssued(FuzzyDate.of(2025));
    assertEquals("2345", d.getVersion());
    assertEquals(FuzzyDate.of(2025), d.getIssued());

    // if there is no dedicated version use the issued date instead!
    d.setVersion(null);
    assertEquals("2025", d.getVersion());
    assertEquals(FuzzyDate.of(2025), d.getIssued());
  }

  @Test
  public void nullTypesComplete() throws Exception {
    for (var p : Dataset.PATCH_PROPS) {
      System.out.println(p.getName() + "  -> " + p.getPropertyType());

      if (!IGNORE.contains(p.getPropertyType())) {
        assertTrue(p.getName(), Dataset.NULL_TYPES.containsKey(p.getName()));
      }
    }
  }

  public static Dataset createNullPatchDataset(int key) {
    var d = new Dataset();
    d.setKey(key);
    try {
      for (PropertyDescriptor p : Dataset.PATCH_PROPS) {
        if (!IGNORE.contains(p.getPropertyType())) {
          p.getWriteMethod().invoke(d, Dataset.NULL_TYPES.get(p.getName()));
        }
      }
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
    return d;
  }

  @Test
  public void patch() throws Exception {
    Dataset orig = genTestValue();
    Dataset d = new Dataset(orig);

    Dataset patch = new Dataset();
    patch.setTitle("Grundig");
    patch.setAlias("grr");
    d.applyPatch(patch);

    assertEquals("Grundig", d.getTitle());
    assertEquals("grr", d.getAlias());

    d = new Dataset(orig);
    patch = createNullPatchDataset(-12);
    d.applyPatch(patch);

    assertNull(d.getTitle());
    assertNull(d.getAlias());
    assertNull(d.getDescription());
    assertNull(d.getCreator());
    assertNull(d.getPublisher());
    assertNull(d.getDoi());
    assertNull(d.getContributor());
    assertNull(d.getContact());
    assertNull(d.getIssued());
    assertNull(d.getIdentifier());
    assertNull(d.getKeyword());

    assertEquals(orig.getLicense(), d.getLicense());
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
    patch.setOrigin(DatasetOrigin.RELEASE);
    patch.setType(DatasetType.ARTICLE);
    patch.setSourceKey(1234);
    patch.setAttempt(13);
    d.applyPatch(patch);
    assertEquals(copy, d);

    // just making sure nothing bad happens when appling explicit nulls
    d = TestEntityGenerator.newDataset("Hallo Spencer");
    d.applyPatch(createNullPatchDataset(999));
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
    d.setUrl(URI.create("https://alucitoidea.hobern.net"));
    d.setDoi(DOI.col("e456fgvzb"));
    // these are taken from the container (=COL) for sources by the mapper
    d.setVersion("Annual Edition 2024");
    d.setIssued(FuzzyDate.of(2024,6,18));
    d.setPublisher(Agent.organisation("Catalogue of Life", null, "Amsterdam", null, Country.NETHERLANDS));
    d.setContainerTitle("Catalogue of Life");
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

  @Test
  public void merge() throws Exception {
    var res = Dataset.merge(
      Agent.parse(List.of("Hobern, Donald", "Hobern, Markus")),
      Agent.parse(List.of("Hobern, Donald", "Hobern, Markus"))
    );

    assertEquals(4, res.size());
  }

  @Test
  public void unique() throws Exception {
    var res = Dataset.unique(
      Agent.parse(List.of("Hobern, Donald", "Hobern, Markus", "Döring, Markus", "Döring, Melitta"))
    );
    assertEquals(3, res.size());

    List<Agent> agents = Agent.parse(List.of("Hobern, Donald", "Hobern, Markus", "Döring, Markus", "Döring, Melitta"));
    agents.add(null);
    agents.add(new Agent(null, null));
    res = Dataset.unique(agents);
    assertEquals(3, res.size());
  }
}