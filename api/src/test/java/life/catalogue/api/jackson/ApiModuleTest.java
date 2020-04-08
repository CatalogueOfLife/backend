package life.catalogue.api.jackson;

import java.io.IOException;
import java.util.Set;

import life.catalogue.api.model.DSIDValue;
import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.vocab.Lifezone;
import life.catalogue.api.vocab.TaxonomicStatus;
import org.junit.Test;

import static org.junit.Assert.*;

public class ApiModuleTest {
  
  @Test
  public void testInit() throws IOException {
    assertNotNull( ApiModule.MAPPER );
  }
  
  @Test
  public void testJsonCreator() throws IOException {
    DSIDValue did1 = new DSIDValue(123, "peter");
    String json = ApiModule.MAPPER.writeValueAsString(did1);
    DSIDValue did2 = ApiModule.MAPPER.readValue(json, DSIDValue.class);
    assertEquals(did1, did2);
  }
  
  @Test
  public void testEnum() throws IOException {
    EditorialDecision ed = new EditorialDecision();
    ed.setDatasetKey(4321);
    ed.setStatus(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
    ed.setLifezones(Set.of(Lifezone.MARINE, Lifezone.FRESHWATER));
    ed.setMode(EditorialDecision.Mode.UPDATE);
    
    String json = ApiModule.MAPPER.writeValueAsString(ed);
    assertTrue(json.contains("provisionally accepted"));
    EditorialDecision ed2 = ApiModule.MAPPER.readValue(json, EditorialDecision.class);
    assertEquals(ed, ed2);
  }
}