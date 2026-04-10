package life.catalogue.es;

import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.es.json.EsModule;

import org.gbif.nameparser.api.Rank;

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
