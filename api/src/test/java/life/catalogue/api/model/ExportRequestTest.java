package life.catalogue.api.model;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.vocab.DataFormat;

import org.gbif.nameparser.api.Rank;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ExportRequestTest {

  @Test
  void isTreeRequest() {
    var req = new ExportRequest();
    assertFalse(req.isTreeRequest());
    req.setMinRank(Rank.FAMILY);
    assertFalse(req.isTreeRequest());
    req.setTaxGroups(false);
    assertFalse(req.isTreeRequest());
    req.setTaxGroups(true);
    assertTrue(req.isTreeRequest());
  }

  /**
   * force binds from the JSON body of POST /dataset/{key}/export, but is never serialized back.
   */
  @Test
  void forceJson() throws Exception {
    var req = ApiModule.MAPPER.readValue(
      "{\"root\":{\"id\":\"B6L67\"},\"format\":\"COLDP\",\"extended\":true,\"force\":true}", ExportRequest.class);
    assertTrue(req.isForce());
    assertTrue(req.isExtended());
    assertEquals(DataFormat.COLDP, req.getFormat());
    assertEquals("B6L67", req.getTaxonID());

    // absent force stays false
    var none = ApiModule.MAPPER.readValue("{\"format\":\"COLDP\"}", ExportRequest.class);
    assertFalse(none.isForce());

    // write only, it is a workflow flag and no part of the export identity
    assertFalse(ApiModule.MAPPER.writeValueAsString(req).contains("force"));
  }
}
