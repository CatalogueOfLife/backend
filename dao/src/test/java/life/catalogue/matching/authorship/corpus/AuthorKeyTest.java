package life.catalogue.matching.authorship.corpus;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class AuthorKeyTest {

  static ExportRow row(List<String> basEx, List<String> bas, String basYear,
                       List<String> combEx, List<String> comb, String combYear, String sanct) {
    return new ExportRow(1, 1, "x", "SPECIES", null, null, "Aus bus", "verbatim", comb, combEx, combYear, bas, basEx, basYear, sanct,
      List.of());
  }

  static AuthorKey comb(String... authors) {
    return AuthorKey.of(row(List.of(), List.of(), null, List.of(), List.of(authors), null, null));
  }

  @Test
  public void slotsInFixedOrder() {
    var key = AuthorKey.of(row(List.of("Sol."), List.of("L."), "1753", List.of("Hook."), List.of("Mill.", "Sm."), "1768", null));
    assertEquals("Sol.;L.;Hook.;Mill.|Sm.", key.exact());
    assertEquals("sol;l;hook;mill|sm", key.loose());
  }

  @Test
  public void yearAndSanctioningAuthorAreNoPartOfIt() {
    var a = AuthorKey.of(row(List.of(), List.of("L."), "1753", List.of(), List.of("Mill."), "1768", "Fr."));
    var b = AuthorKey.of(row(List.of(), List.of("L."), null, List.of(), List.of("Mill."), "1801", null));
    assertEquals(a, b);
  }

  /** a basionym author is another statement than the same author of the combination */
  @Test
  public void slotMatters() {
    var bas = AuthorKey.of(row(List.of(), List.of("L."), null, List.of(), List.of(), null, null));
    assertNotEquals(bas.loose(), comb("L.").loose());
  }

  @Test
  public void collapsesWhitespace() {
    assertEquals(";;;J. E. Gray", comb("  J.  E.   Gray ").exact());
  }

  /** punctuation, spacing and case alone make no pair worth looking at */
  @Test
  public void looseIgnoresPunctuationSpacingAndCase() {
    assertEquals(comb("J.E.Gray").loose(), comb("J. E. GRAY").loose());
    assertEquals(comb("L.").loose(), comb("L").loose());
    assertEquals(comb("Saint-Hilaire").loose(), comb("Saint Hilaire").loose());
  }

  /** folding diacritics is the job of the code that is measured, so they have to stay */
  @Test
  public void looseKeepsDiacritics() {
    assertEquals("müller", comb("Müller").loose().replace(";", ""));
    assertNotEquals(comb("Müller").loose(), comb("Muller").loose());
    assertNotEquals(comb("Müller").loose(), comb("Mueller").loose());
  }

  @Test
  public void dropsAuthorsWithoutAnyLetter() {
    assertEquals(comb("Voet").loose(), comb("Voet", "?").loose());
    assertTrue(comb("?").isEmpty());
    assertFalse(comb("Voet").isEmpty());
  }
}
