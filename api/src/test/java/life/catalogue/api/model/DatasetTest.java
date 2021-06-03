package life.catalogue.api.model;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.jackson.SerdeTestBase;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.License;
import org.junit.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
    d.setTitle("gfdscdscw");
    d.setDescription("gefzw fuewh gczew fw hfueh j ijdfeiw jfie eö.. few . few .");
    d.setOrigin(DatasetOrigin.EXTERNAL);
    d.setType(DatasetType.TAXONOMIC);
    d.setUrl(URI.create("www.gbif.org"));
    d.setLogo(URI.create("www.gbif.org"));
    d.setLicense(License.CC0);
    d.setGeographicScope("North Africa");
    d.setDistributor(new ArrayList<>(List.of(
      new Agent("dist"),
      new Agent("dist2")
    )));
    d.setContact(Agent.parse("foo"));
    d.setCreator(new ArrayList<>(List.of(
      new Agent("crea1"),
      new Agent("crea2"),
      new Agent("crea3")
    )));
    d.setEditor(new ArrayList<>(List.of(
      new Agent("editi"),
      new Agent("edito"),
      new Agent("edita")
    )));
    d.setNotes("cuzdsghazugbe67wqt6c g cuzdsghazugbe67wqt6c g  nhjs");
    return d;
  }

  @Override
  public Dataset genTestValue() throws Exception {
    return generateTestDataset();
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

    d.applyPatch(patch);
    assertEquals(copy, d);

    patch.setVersion("my version");
    copy.setVersion("my version");
    d.applyPatch(patch);
    assertEquals(copy, d);

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
}