package life.catalogue.importer.corpus;

import life.catalogue.importer.corpus.TextChange.Level;

import org.junit.Test;

import static org.junit.Assert.*;

public class TextChangeTest {

  @Test
  public void level() {
    assertEquals(Level.SAME, TextChange.level(null, null));
    assertEquals(Level.SAME, TextChange.level("Mill.", "Mill."));
    assertEquals(Level.ADDED, TextChange.level(null, "Mill."));
    assertEquals(Level.REMOVED, TextChange.level("Mill.", null));
    assertEquals(Level.WHITESPACE, TextChange.level("P. D. Sell", "P.D.Sell"));
    assertEquals(Level.PUNCTUATION, TextChange.level("Rossi 1790", "Rossi, 1790"));
    assertEquals(Level.WHITESPACE, TextChange.level("Trautv.&Meyer", "Trautv. & Meyer"));
    assertEquals(Level.CASE, TextChange.level("SMITH", "Smith"));
    assertEquals(Level.DIACRITICS, TextChange.level("Méquignon", "Mequignon"));
    assertEquals(Level.TEXT, TextChange.level("Smith and Jones", "Smith & Jones"));
    assertFalse(Level.CASE.isSignificant());
    assertTrue(Level.ADDED.isSignificant());
    assertTrue(Level.TEXT.isSignificant());
  }

  @Test
  public void cause() {
    assertEquals("separator", TextChange.cause("Smith and Jones", "Smith & Jones"));
    assertEquals("separator", TextChange.cause("Smith und Jones", "Smith, Jones"));
    assertEquals("et-al", TextChange.cause("Smith et al., 1900", "Smith, 1900"));
    assertEquals("initials", TextChange.cause("G.B. Sowerby", "Sowerby"));
    assertEquals("year", TextChange.cause("Smith, 1900", "Smith"));
    assertEquals("year", TextChange.cause("Smith, 1900", "Smith, 1901"));
    assertEquals("rank-marker", TextChange.cause("Poa annua var. alba", "Poa annua alba"));
    assertEquals("in-citation", TextChange.cause("Smith in Jones, 1900", "Smith, 1900"));
    assertEquals("ex-author", TextChange.cause("Brouss. ex Willd.", "Willd."));
    assertEquals("note", TextChange.cause("Mill. sensu Smith", "Mill. Smith"));
    assertEquals("order", TextChange.cause("(Smith) Mill.", "(Mill.) Smith"));
    assertEquals("abbreviation", TextChange.cause("Mill.", "Miller"));
    assertEquals("tail-dropped", TextChange.cause("Abies alba foo bar", "Abies alba"));
    assertEquals("tail-added", TextChange.cause("Abies alba", "Abies alba foo bar"));
    assertEquals("head-dropped", TextChange.cause("foo Abies alba", "Abies alba"));
    assertEquals("words-dropped", TextChange.cause("Abies foo alba bar", "Abies alba"));
    assertEquals("other", TextChange.cause("Abies alba", "Picea abies"));
  }
}
