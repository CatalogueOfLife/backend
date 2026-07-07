package life.catalogue.api.vocab;

import org.junit.Test;

import static org.junit.Assert.*;

public class IdentifierScopeTest {

  @Test
  public void extractIdFromResolverUrl() {
    IdentifierScope inat = IdentifierScopes.byScope("inat");
    assertNotNull(inat);
    // full resolver URL -> plain local id
    assertEquals("42007", inat.extractId("https://www.inaturalist.org/taxa/42007"));
    // already a plain id -> kept unchanged
    assertEquals("42007", inat.extractId("42007"));
    // a non-matching URL is kept unchanged
    assertEquals("https://example.org/42007", inat.extractId("https://example.org/42007"));
  }

  @Test
  public void httpHttpsLeniency() {
    IdentifierScope inat = IdentifierScopes.byScope("inat");
    // resolver is https, but http is a common mistake and must still match
    assertEquals("42007", inat.extractId("http://www.inaturalist.org/taxa/42007"));
    assertEquals("42007", inat.extractId("https://www.inaturalist.org/taxa/42007"));
  }

  @Test
  public void regexConstrainsCapturedId() {
    IdentifierScope inat = IdentifierScopes.byScope("inat");
    // inat regex is ^[0-9]+$, so a non-numeric tail does not match the resolver and is kept as-is
    assertEquals("https://www.inaturalist.org/taxa/abc", inat.extractId("https://www.inaturalist.org/taxa/abc"));
  }

  @Test
  public void idPlaceholderInsideUrl() {
    // worms keeps {id} as a query parameter, not at the end
    IdentifierScope worms = IdentifierScopes.byScope("worms");
    assertNotNull(worms);
    assertEquals("212808", worms.extractId("https://www.marinespecies.org/aphia.php?p=taxdetails&id=212808"));
    assertEquals("212808", worms.extractId("212808"));
  }

  @Test
  public void noResolverKeepsRaw() {
    // 'local' has no resolver template, so the value is always kept unchanged
    IdentifierScope local = IdentifierScopes.byScope("local");
    assertNotNull(local);
    assertEquals("https://www.inaturalist.org/taxa/42007", local.extractId("https://www.inaturalist.org/taxa/42007"));
    assertEquals("anything", local.extractId("anything"));
  }

  @Test
  public void nullSafe() {
    assertNull(IdentifierScopes.byScope("inat").extractId(null));
  }
}
