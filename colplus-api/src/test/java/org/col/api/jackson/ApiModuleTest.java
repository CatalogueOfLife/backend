package org.col.api.jackson;

import java.io.IOException;

import org.col.api.model.DatasetID;
import org.col.api.model.EditorialDecision;
import org.col.api.vocab.Lifezone;
import org.col.api.vocab.TaxonomicStatus;
import org.junit.Test;

import static org.junit.Assert.*;

public class ApiModuleTest {
  
  @Test
  public void testInit() throws IOException {
    assertNotNull( ApiModule.MAPPER );
  }
  
  @Test
  public void testJsonCreator() throws IOException {
    DatasetID did1 = new DatasetID(123, "peter");
    String json = ApiModule.MAPPER.writeValueAsString(did1);
    DatasetID did2 = ApiModule.MAPPER.readValue(json, DatasetID.class);
    assertEquals(did1, did2);
  }
  
  @Test
  public void testEnum() throws IOException {
    EditorialDecision ed = new EditorialDecision();
    ed.setDatasetKey(4321);
    ed.setStatus(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
    ed.getLifezones().add(Lifezone.MARINE);
    ed.getLifezones().add(Lifezone.FRESHWATER);
    ed.setMode(EditorialDecision.Mode.UPDATE);
    
    String json = ApiModule.MAPPER.writeValueAsString(ed);
    assertTrue(json.contains("provisionally accepted"));
    EditorialDecision ed2 = ApiModule.MAPPER.readValue(json, EditorialDecision.class);
    assertEquals(ed, ed2);
  }
}