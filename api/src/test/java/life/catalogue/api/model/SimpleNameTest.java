package life.catalogue.api.model;

import life.catalogue.api.jackson.ApiModule;

import org.gbif.nameparser.api.Rank;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;

public class SimpleNameTest {
  
  @Test
  public void compareTo() {
    List<SimpleName> names = new ArrayList<>();
    names.add(sn(Rank.SPECIES, "Abiès alba", "Mill."));
    names.add(sn(Rank.SPECIES, "Abiès alba", "Miller"));
    names.add(sn(Rank.SPECIES, "Pinus alba", null));
    names.add(sn(Rank.SPECIES, "Abieta alba", ""));
    names.add(sn(Rank.SUBGENUS, "Pomela", null));
    names.add(sn(Rank.GENUS, "Pomela", null));
    names.add(sn(Rank.GENUS, "Pomela", "Karl"));
    names.add(sn(null, "Pomela", null));
    names.add(sn(null, "", null));
    
    names.sort(SimpleName.NATURAL_ORDER);
    
    for (SimpleName sn : names) {
      System.out.println(sn);
    }
  }

  @Test
  public void to_String() {
    SimpleName sn = new SimpleName("atc", "Cichorieae", Rank.TRIBE);
    Assert.assertEquals("TRIBE Cichorieae [atc]", sn.toString());

    sn.setParent("Asteraceae");
    Assert.assertEquals("TRIBE Cichorieae [atc parent=Asteraceae]", sn.toString());
  }

  /**
   * Both labels must stay part of the serialized SimpleName.
   * They were lost for the entire API in ab3ed107f and broke the UI twice, see
   * https://github.com/CatalogueOfLife/checklistbank/issues/1643
   */
  @Test
  public void serializedLabels() throws Exception {
    SimpleName sn = new SimpleName("x8N", "Abies alba", "Mill.", Rank.SPECIES);
    JsonNode json = ApiModule.MAPPER.readTree(ApiModule.MAPPER.writeValueAsString(sn));

    Assert.assertEquals("Abies alba Mill.", json.get("label").asText());
    Assert.assertEquals("<i>Abies alba</i> Mill.", json.get("labelHtml").asText());
  }

  /**
   * The extinct flag itself is @JsonIgnore because the dagger is part of the label - so the label
   * has to carry it.
   */
  @Test
  public void serializedExtinctDagger() throws Exception {
    SimpleName sn = new SimpleName("x8N", "Abies alba", "Mill.", Rank.SPECIES);
    sn.setExtinct(true);
    JsonNode json = ApiModule.MAPPER.readTree(ApiModule.MAPPER.writeValueAsString(sn));

    Assert.assertEquals(NameUsageBase.EXTINCT_SYMBOL + "Abies alba Mill.", json.get("label").asText());
    Assert.assertFalse(json.has("extinct"));
  }

  /**
   * The parent is exposed as parentId only, deliberately - the duplicate parent property stays hidden.
   */
  @Test
  public void serializedParentId() throws Exception {
    SimpleName sn = new SimpleName("atc", "Cichorieae", Rank.TRIBE);
    sn.setParent("Asteraceae");
    JsonNode json = ApiModule.MAPPER.readTree(ApiModule.MAPPER.writeValueAsString(sn));

    Assert.assertEquals("Asteraceae", json.get("parentId").asText());
    Assert.assertFalse(json.has("parent"));
  }

  static SimpleName sn(Rank rank, String name, String author) {
    return new SimpleName(null, name, author, rank);
  }
}