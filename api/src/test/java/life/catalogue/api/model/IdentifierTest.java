package life.catalogue.api.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

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

  @Test
  public void local() {
    String id = "234567890";
    var id1 = Identifier.parse(id);
    var id2 = new Identifier(Identifier.Scope.LOCAL, id);
    assertEquals(id2, id1);
    assertEquals("local:"+id, id1.toString());
  }
}