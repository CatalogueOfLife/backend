package org.col.api.jackson;

import java.io.IOException;

import org.col.api.model.DatasetID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
}