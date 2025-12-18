package life.catalogue.api.model;

import org.junit.Test;

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
  public void http() {
    String url = "http://depo.msu.ru/ipt/resource?r=astrakhan";
    Identifier id = Identifier.parse(url);
    assertEquals(Identifier.Scope.URL.prefix(), id.getScope());
    assertEquals(url, id.getId());

    var id2 = Identifier.parse(id.toString());
    assertEquals(id2, id);

    Identifier id3 = new Identifier(Identifier.Scope.URL, url);
    assertEquals(url, id3.getId());

    url = "https://depo.msu.ru/ipt/resource?r=astrakhan";
    id = Identifier.parse(url);
    assertEquals(Identifier.Scope.URL.prefix(), id.getScope());
    assertEquals(url, id.getId());
    assertNotEquals(id2, id);

  }

  @Test
  public void other() {
    String raw = "urn:lsid:ipni.org:names:320035-2";
    Identifier id = Identifier.parse(raw);
    assertEquals(Identifier.Scope.LSID.prefix(), id.getScope());
    assertEquals(raw, id.getId());

    raw = "urn:ISBN:3-8273-7019-1";
    id = Identifier.parse(raw);
    assertEquals(Identifier.Scope.URN.prefix(), id.getScope());
    assertEquals(raw, id.getId());

    raw = "URN:MPEG:MPEG7:SCHEMA:2001";
    id = Identifier.parse(raw);
    assertEquals(Identifier.Scope.URN.prefix(), id.getScope());
    assertEquals(raw, id.getId());
  }

  @Test
  public void dois() {
    DOI doi = new DOI("10.48580/234567");
    Identifier id = Identifier.parse(doi.getDoiName());
    assertEquals(doi.getDoiString(), id.toString());
    assertEquals(Identifier.Scope.DOI.prefix(), id.getScope());
    assertTrue(id.isDOI());

    var id2 = new Identifier(doi);
    assertEquals(id2, id);

    id = Identifier.parse(doi.getDoiString());
    assertEquals(doi.getDoiString(), id.toString());
    assertEquals(Identifier.Scope.DOI.prefix(), id.getScope());
    assertTrue(id.isDOI());

    id = Identifier.parse(doi.getUrl().toString());
    assertEquals(doi.getDoiString(), id.toString());
    assertEquals(Identifier.Scope.DOI.prefix(), id.getScope());
    assertTrue(id.isDOI());

    id = Identifier.parse("http://doi.org/10.48580/234567");
    assertEquals(doi.getDoiString(), id.toString());
    assertEquals(Identifier.Scope.DOI.prefix(), id.getScope());
    assertTrue(id.isDOI());

    id = Identifier.parse("https://dx.doi.org/10.48580/234567");
    assertEquals(doi.getDoiString(), id.toString());
    assertEquals(Identifier.Scope.DOI.prefix(), id.getScope());
    assertTrue(id.isDOI());

    id = Identifier.parse("DOI:10.48580/234567");
    assertEquals(doi.getDoiString(), id.toString());
    assertEquals(Identifier.Scope.DOI.prefix(), id.getScope());
    assertTrue(id.isDOI());
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