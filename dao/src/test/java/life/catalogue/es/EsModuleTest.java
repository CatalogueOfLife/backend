package life.catalogue.es;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.es.json.EsModule;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertTrue;

public class EsModuleTest {

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
