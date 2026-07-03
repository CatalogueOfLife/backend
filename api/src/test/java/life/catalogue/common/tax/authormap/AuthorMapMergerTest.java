package life.catalogue.common.tax.authormap;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AuthorMapMergerTest {
  @Test
  public void unionsSharedAliasAndPromotesToAny() {
    List<AuthorEntry> manual = List.of(
      new AuthorEntry("C Linnaeus", AuthorCode.BOT, List.of("L.", "Carl Linnaeus")));
    List<AuthorEntry> wikidata = List.of(
      // shares "Carl Linnaeus" -> same author; ZOO usage promotes group to ANY (published under both codes)
      new AuthorEntry("Linnaeus", AuthorCode.ZOO, List.of("Carl Linnaeus", "Linné")),
      new AuthorEntry("G Cuvier", AuthorCode.ZOO, List.of("Georges Cuvier")));

    List<AuthorEntry> merged = AuthorMapMerger.merge(List.of(manual, wikidata));

    AuthorEntry linn = merged.stream().filter(e -> e.canonical().equals("C Linnaeus")).findFirst().orElseThrow();
    assertEquals(AuthorCode.ANY, linn.code());               // BOT + ZOO -> ANY
    assertTrue(linn.aliases().contains("Linné"));            // alias unioned in from wikidata
    assertTrue(linn.aliases().contains("L."));               // manual alias kept

    AuthorEntry cuv = merged.stream().filter(e -> e.canonical().equals("G Cuvier")).findFirst().orElseThrow();
    assertEquals(AuthorCode.ZOO, cuv.code());                // only zoological, stays ZOO
  }

  @Test
  public void manualCanonicalAndCodeWin() {
    List<AuthorEntry> manual = List.of(new AuthorEntry("J F Gmelin", AuthorCode.ANY, List.of("Gmelin")));
    List<AuthorEntry> other  = List.of(new AuthorEntry("Johann Gmelin", AuthorCode.BOT, List.of("Gmelin", "J.F.Gmel.")));
    List<AuthorEntry> merged = AuthorMapMerger.merge(List.of(manual, other));
    AuthorEntry g = merged.get(0);
    assertEquals("J F Gmelin", g.canonical());   // manual canonical wins
    assertEquals(AuthorCode.ANY, g.code());      // manual locked the code
    assertTrue(g.aliases().contains("J.F.Gmel.")); // aliases still unioned
  }
}
