package life.catalogue.es;

import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.es.json.EsModule;

import org.gbif.nameparser.api.Rank;

import java.util.List;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;

public class EsModuleTest {

  @Test
  public void sectorModeSerializedOnUsage() throws JsonProcessingException {
    ObjectMapper mapper = EsModule.contentMapper();
    Name name = new Name();
    name.setRank(Rank.SPECIES);
    name.setScientificName("Abies alba");
    Taxon taxon = new Taxon(name);
    taxon.setStatus(TaxonomicStatus.ACCEPTED);
    taxon.setSectorMode(Sector.Mode.MERGE);

    String json = mapper.writeValueAsString(new NameUsageWrapper(taxon));
    JsonNode root = mapper.readTree(json);

    // sectorMode must appear on usage (not @JsonIgnore in EsModule context)
    assertFalse("sectorMode should be present on usage", root.path("usage").path("sectorMode").isMissingNode());
    assertEquals("MERGE", root.path("usage").path("sectorMode").asText());
  }

  @Test
  public void sectorModeSerializedOnBareName() throws JsonProcessingException {
    ObjectMapper mapper = EsModule.contentMapper();
    Name name = new Name();
    name.setRank(Rank.SPECIES);
    name.setScientificName("Abies alba");
    name.setSectorMode(Sector.Mode.ATTACH);
    BareName bareName = new BareName(name);

    String json = mapper.writeValueAsString(new NameUsageWrapper(bareName));
    JsonNode root = mapper.readTree(json);

    // For bare names, sectorMode lives on the name
    assertFalse("sectorMode should be present on usage.name", root.path("usage").path("name").path("sectorMode").isMissingNode());
    assertEquals("ATTACH", root.path("usage").path("name").path("sectorMode").asText());
  }

  @Test
  public void sectorModeDeserializedFromUsage() throws JsonProcessingException {
    ObjectMapper mapper = EsModule.contentMapper();
    Name name = new Name();
    name.setRank(Rank.SPECIES);
    name.setScientificName("Abies alba");
    Taxon taxon = new Taxon(name);
    taxon.setStatus(TaxonomicStatus.ACCEPTED);
    taxon.setSectorMode(Sector.Mode.MERGE);

    // round-trip: serialize then deserialize
    String json = mapper.writeValueAsString(new NameUsageWrapper(taxon));
    NameUsageWrapper result = mapper.readValue(json, NameUsageWrapper.class);

    NameUsageBase usage = (NameUsageBase) result.getUsage();
    assertEquals("sectorMode must survive round-trip", Sector.Mode.MERGE, usage.getSectorMode());
  }

  /**
   * SimpleName labels are derived and have no setter, so they are never read back from the _source.
   * Keep them out of the ES documents - the classification would otherwise carry two label strings
   * per entry for every indexed usage.
   */
  @Test
  public void classificationLabelsNotSerialized() throws JsonProcessingException {
    ObjectMapper mapper = EsModule.contentMapper();
    Name name = new Name();
    name.setRank(Rank.SPECIES);
    name.setScientificName("Abies alba");
    name.setAuthorship("Mill.");
    Taxon taxon = new Taxon(name);
    taxon.setStatus(TaxonomicStatus.ACCEPTED);

    NameUsageWrapper nuw = new NameUsageWrapper(taxon);
    nuw.setClassification(List.of(
      new SimpleName("g1", "Abies", "Mill.", Rank.GENUS),
      new SimpleName("s1", "Abies alba", "Mill.", Rank.SPECIES)
    ));

    JsonNode root = mapper.readTree(mapper.writeValueAsString(nuw));
    JsonNode classification = root.path("classification");
    assertEquals(2, classification.size());
    for (JsonNode sn : classification) {
      assertTrue("no label on classification entries, got: " + sn, sn.path("label").isMissingNode());
      assertTrue("no labelHtml on classification entries, got: " + sn, sn.path("labelHtml").isMissingNode());
      assertFalse("the name itself must stay", sn.path("name").isMissingNode());
    }
    // the usage itself keeps its plain label, see NameUsageMixIn
    assertFalse(root.path("usage").path("label").isMissingNode());
  }

  @Test
  public void rankSerializedAsInt() throws JsonProcessingException {
    ObjectMapper mapper = EsModule.contentMapper();
    Name name = new Name();
    name.setRank(Rank.SPECIES);
    name.setScientificName("Abies alba");
    Taxon taxon = new Taxon(name);
    taxon.setStatus(TaxonomicStatus.ACCEPTED);
    String json = mapper.writeValueAsString(new NameUsageWrapper(taxon));
    System.out.println(json);
    System.out.println("SPECIES ordinal: " + Rank.SPECIES.ordinal());
    // Rank.SPECIES should be serialized as its ordinal integer, not as the string "SPECIES"
    assertTrue("Rank must be an integer in JSON, got: " + json, json.contains("\"rank\":" + Rank.SPECIES.ordinal()));
  }

}
