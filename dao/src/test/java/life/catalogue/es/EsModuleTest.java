package life.catalogue.es;

import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.vocab.InfoGroup;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.es.json.EsModule;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.EnumSet;
import java.util.List;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

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

  /**
   * The canonical name is only written if it differs from the scientific name, #1676
   */
  @Test
  public void canonicalNameOnlyIfDifferent() throws JsonProcessingException {
    ObjectMapper mapper = EsModule.contentMapper();
    Name name = new Name();
    name.setGenus("Aleochara");
    name.setSpecificEpithet("composita");
    name.setRank(Rank.SPECIES);
    name.setCode(NomCode.ZOOLOGICAL);
    name.rebuildScientificName();
    Taxon taxon = new Taxon(name);
    taxon.setStatus(TaxonomicStatus.ACCEPTED);

    JsonNode root = mapper.readTree(mapper.writeValueAsString(new NameUsageWrapper(taxon)));
    assertTrue(root.path("usage").path("name").path("canonicalName").isMissingNode());

    name.setInfragenericEpithet("Coprochara");
    name.rebuildScientificName();
    String json = mapper.writeValueAsString(new NameUsageWrapper(taxon));
    root = mapper.readTree(json);
    assertEquals("Aleochara composita", root.path("usage").path("name").path("canonicalName").asText());

    // and it is read back without trouble
    NameUsageWrapper result = mapper.readValue(json, NameUsageWrapper.class);
    assertEquals("Aleochara (Coprochara) composita", result.getUsage().getName().getScientificName());
  }

  /**
   * taxon-ws reads the very index the backend writes, but is released on its own schedule.
   * A document written by a newer backend - a new property, issue or enum value - must still be readable
   * by an older reader, with the values it does not know dropped. See gbif/gbif-web#2080.
   */
  @Test
  public void tolerantReader() throws JsonProcessingException {
    ObjectMapper mapper = EsModule.contentMapper();
    Name name = new Name();
    name.setRank(Rank.SPECIES);
    name.setScientificName("Abies alba");
    name.setCode(NomCode.BOTANICAL);
    Taxon taxon = new Taxon(name);
    taxon.setStatus(TaxonomicStatus.ACCEPTED);
    NameUsageWrapper nuw = new NameUsageWrapper(taxon);
    nuw.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.MISSING_AUTHORSHIP));
    nuw.setSecondarySourceGroups(EnumSet.of(InfoGroup.AUTHORSHIP));
    nuw.setGroup(TaxGroup.Plants);

    ObjectNode root = (ObjectNode) mapper.readTree(mapper.writeValueAsString(nuw));
    root.put("someFutureProperty", "x");
    root.withArray("issues").add("SOME_FUTURE_ISSUE");
    root.withArray("secondarySourceGroups").add("SOME_FUTURE_GROUP");
    root.put("group", "SomeFutureGroup");
    ObjectNode n = (ObjectNode) root.path("usage").path("name");
    n.put("code", "SOME_FUTURE_CODE");
    n.put("someFutureNameProperty", 1);

    NameUsageWrapper result = mapper.readValue(mapper.writeValueAsString(root), NameUsageWrapper.class);
    assertEquals(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.MISSING_AUTHORSHIP), result.getIssues());
    assertEquals(EnumSet.of(InfoGroup.AUTHORSHIP), result.getSecondarySourceGroups());
    assertNull(result.getGroup());
    Name rn = result.getUsage().getName();
    assertEquals("Abies alba", rn.getScientificName());
    assertNull(rn.getCode());
    assertEquals(Rank.SPECIES, rn.getRank());
    assertEquals(TaxonomicStatus.ACCEPTED, result.getUsage().getStatus());
  }

}
