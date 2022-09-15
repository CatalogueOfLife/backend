package life.catalogue.api.model;

import org.junit.Test;

import static org.junit.Assert.*;

public class IdentifierTest {

  @Test
  public void roundtrip() {
    String tsn = "tsn:23545";
    Identifier id = new Identifier(tsn);
    var id2 = new Identifier(id.toString());
    assertEquals(id2, id);

    assertEquals(tsn, id.toString());
  }
}