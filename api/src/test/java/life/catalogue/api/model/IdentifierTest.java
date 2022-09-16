package life.catalogue.api.model;

import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.*;

public class IdentifierTest {

  @Test
  public void roundtrip() {
    String tsn = "tsn:23545";
    Identifier id = Identifier.parse(tsn);
    var id2 = Identifier.parse(id.toString());
    assertEquals(id2, id);

    assertEquals(tsn, id.toString());
  }

  @Test
  public void dois() {
    DOI doi = new DOI("10.48580/234567");
    Identifier id = Identifier.parse(doi.getDoiName());
    assertEquals(doi.getDoiString(), id.toString());

    id = Identifier.parse(doi.getDoiString());
    assertEquals(doi.getDoiString(), id.toString());

    id = Identifier.parse(doi.getUrl().toString());
    assertEquals(doi.getDoiString(), id.toString());

    id = Identifier.parse("http://doi.org/10.48580/234567");
    assertEquals(doi.getDoiString(), id.toString());

    id = Identifier.parse("https://dx.doi.org/10.48580/234567");
    assertEquals(doi.getDoiString(), id.toString());
  }

  @Test(expected = IllegalArgumentException.class)
  public void iae() {
    Identifier.parse("234567890");
  }
}