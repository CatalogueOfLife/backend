package life.catalogue.common.tax.authormap;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AuthorMapMergerTest {
  @Test
  public void unionsSharedFullNameAndPromotesToAny() {
    List<AuthorEntry> manual = List.of(
      new AuthorEntry("C Linnaeus", AuthorCode.BOT, List.of("L.", "Carl Linnaeus")));
    List<AuthorEntry> wikidata = List.of(
      new AuthorEntry("Linnaeus", AuthorCode.ZOO, List.of("Carl Linnaeus", "Linné")),
      new AuthorEntry("G Cuvier", AuthorCode.ZOO, List.of("Georges Cuvier")));

    List<AuthorEntry> merged = AuthorMapMerger.merge(List.of(manual, wikidata), 2);

    AuthorEntry linn = merged.stream().filter(e -> e.canonical().equals("C Linnaeus")).findFirst().orElseThrow();
    assertEquals(AuthorCode.ANY, linn.code());              // BOT + ZOO -> ANY, bridged on full name "Carl Linnaeus"
    assertTrue(linn.aliases().contains("Linné"));
    assertTrue(linn.aliases().contains("L."));

    AuthorEntry cuv = merged.stream().filter(e -> e.canonical().equals("G Cuvier")).findFirst().orElseThrow();
    assertEquals(AuthorCode.ZOO, cuv.code());
  }

  @Test
  public void manualCanonicalAndCodeWin() {
    List<AuthorEntry> manual = List.of(new AuthorEntry("J F Gmelin", AuthorCode.ANY, List.of("Gmelin", "Johann Friedrich Gmelin")));
    List<AuthorEntry> other  = List.of(new AuthorEntry("Johann Gmelin", AuthorCode.BOT, List.of("J.F.Gmel.", "Johann Friedrich Gmelin")));
    List<AuthorEntry> merged = AuthorMapMerger.merge(List.of(manual, other), 2);
    AuthorEntry g = merged.stream().filter(e -> e.canonical().equals("J F Gmelin")).findFirst().orElseThrow();
    assertEquals("J F Gmelin", g.canonical());   // manual canonical (earliest) wins
    assertEquals(AuthorCode.ANY, g.code());
    assertTrue(g.aliases().contains("J.F.Gmel."));   // aliases unioned via shared full name
  }

  @Test
  public void bridgesGroupsOnlyViaSharedFullName() {
    List<AuthorEntry> s0 = List.of(new AuthorEntry("A P de Candolle", AuthorCode.BOT, List.of("DC.", "Augustin Pyramus de Candolle")));
    List<AuthorEntry> s1 = List.of(new AuthorEntry("Candolle", AuthorCode.ZOO, List.of("Augustin Pyramus de Candolle", "A.P. de Candolle")));
    List<AuthorEntry> merged = AuthorMapMerger.merge(List.of(s0, s1), 2);
    assertEquals(1, merged.size());                 // unioned on the multi-token full name
    AuthorEntry g = merged.get(0);
    assertEquals("A P de Candolle", g.canonical());
    assertEquals(AuthorCode.ANY, g.code());
    assertTrue(g.aliases().containsAll(List.of("DC.", "Augustin Pyramus de Candolle", "A.P. de Candolle")));
  }

  @Test
  public void sharedSurnameDoesNotConflateAndAmbiguousKeyDropped() {
    // curated IPNI author: unique full name + bare surname
    List<AuthorEntry> curated = List.of(new AuthorEntry("A Smith", AuthorCode.BOT, List.of("Smith", "Andrew Smith")));
    // non-curated wikidata author sharing ONLY the bare surname
    List<AuthorEntry> wikidata = List.of(new AuthorEntry("Hobart Muir Smith", AuthorCode.ZOO, List.of("Smith", "Hobart Muir Smith")));
    List<AuthorEntry> merged = AuthorMapMerger.merge(List.of(curated, wikidata), 1); // only source 0 curated
    assertEquals(2, merged.size());                 // NOT conflated
    AuthorEntry andrew = merged.stream().filter(e -> e.canonical().equals("A Smith")).findFirst().orElseThrow();
    AuthorEntry hobart = merged.stream().filter(e -> e.canonical().equals("Hobart Muir Smith")).findFirst().orElseThrow();
    assertTrue(andrew.aliases().contains("Smith"));            // curated keeps the ambiguous surname
    assertFalse(hobart.aliases().stream().anyMatch(a -> a.equalsIgnoreCase("Smith"))); // stripped from non-curated
    assertTrue(hobart.aliases().contains("Hobart Muir Smith")); // unique full name kept
  }

  @Test
  public void ambiguousKeyKeptAtHighestPrecedenceCuratedHolder() {
    // two curated authors share a bare surname; manual (source 0) outranks existing (source 1)
    List<AuthorEntry> manual = List.of(new AuthorEntry("J F Gmelin", AuthorCode.ANY, List.of("Gmelin", "Johann Friedrich Gmelin")));
    List<AuthorEntry> existing = List.of(new AuthorEntry("S G Gmelin", AuthorCode.BOT, List.of("Gmelin", "Samuel Gottlieb Gmelin")));
    List<AuthorEntry> merged = AuthorMapMerger.merge(List.of(manual, existing), 2); // both curated
    AuthorEntry jf = merged.stream().filter(e -> e.canonical().equals("J F Gmelin")).findFirst().orElseThrow();
    AuthorEntry sg = merged.stream().filter(e -> e.canonical().equals("S G Gmelin")).findFirst().orElseThrow();
    assertTrue(jf.aliases().contains("Gmelin"));            // highest-precedence curated (manual) keeps it
    assertFalse(sg.aliases().stream().anyMatch(a -> a.equalsIgnoreCase("Gmelin"))); // stripped from lower-precedence curated
  }
}
