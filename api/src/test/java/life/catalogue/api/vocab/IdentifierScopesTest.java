package life.catalogue.api.vocab;

import life.catalogue.api.model.Identifier;

import java.util.regex.Pattern;

import org.junit.Test;

import static org.junit.Assert.*;

public class IdentifierScopesTest {

  @Test
  public void registryLoads() {
    assertFalse("registry must not be empty", IdentifierScopes.all().isEmpty());
    for (IdentifierScope s : IdentifierScopes.all()) {
      assertNotNull("scope is required: " + s, s.getScope());
      assertEquals("scope must be lowercased: " + s.getScope(), s.getScope().toLowerCase(), s.getScope());
      assertNotNull("title is required for scope " + s.getScope(), s.getTitle());
      if (s.getResolverTemplate() != null) {
        assertTrue("resolverTemplate must contain the {id} placeholder: " + s,
          s.getResolverTemplate().contains("{id}"));
      }
      if (s.getRegex() != null) {
        // must be a valid Java regex
        Pattern.compile(s.getRegex());
        if (s.getExample() != null) {
          assertTrue("example '" + s.getExample() + "' must match regex for scope " + s.getScope(),
            Pattern.compile(s.getRegex()).matcher(s.getExample()).matches());
        }
      }
    }
  }

  @Test
  public void allEnumScopesArePresent() {
    for (Identifier.Scope enumScope : Identifier.Scope.values()) {
      IdentifierScope reg = IdentifierScopes.byScope(enumScope.prefix());
      assertNotNull("Identifier.Scope." + enumScope.name() + " ('" + enumScope.prefix()
        + "') is missing from the identifier scope registry", reg);
    }
  }

  @Test
  public void byScopeIsCaseInsensitive() {
    assertNotNull(IdentifierScopes.byScope("col"));
    assertNotNull(IdentifierScopes.byScope("COL"));
    assertNotNull(IdentifierScopes.byScope("Col"));
    assertSame(IdentifierScopes.byScope("col"), IdentifierScopes.byScope("COL"));
    assertNull(IdentifierScopes.byScope("not-a-real-scope"));
    assertNull(IdentifierScopes.byScope(null));
  }

  @Test
  public void byDatasetKeyResolvesCol() {
    IdentifierScope col = IdentifierScopes.byScope("col");
    assertNotNull(col);
    assertEquals(Integer.valueOf(3), col.getDatasetKey());
    assertSame(col, IdentifierScopes.byDatasetKey(3));
    assertNull(IdentifierScopes.byDatasetKey(987654321));
  }
}
