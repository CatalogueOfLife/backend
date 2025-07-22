package life.catalogue.api.model;

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
}