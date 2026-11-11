package life.catalogue.matching;

import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.matching.authorship.AuthorComparator;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

public class MisapplicationNoteTest {
  final AuthorComparator authComp = new AuthorComparator(AuthorshipNormalizer.INSTANCE);

  static MisapplicationNote p(String note) {
    return MisapplicationNote.parse(note, null);
  }

  static void assertParts(String note, boolean auct, String sensu, String... non) {
    var n = p(note);
    assertEquals(note, auct, n.isAuct());
    assertEquals(note, sensu, n.getSensu());
    assertEquals(note, List.of(non), n.getNon());
  }

  Equality cmp(String a, String b) {
    var eq = p(a).compare(p(b), authComp);
    assertEquals("symmetric", eq, p(b).compare(p(a), authComp));
    return eq;
  }

  @Test
  public void parse() {
    assertParts("sensu F. W. Hutton, 1873", false, "F. W. Hutton, 1873");
    assertParts("auct. non (DC.) Bunge", true, null, "(DC.) Bunge");
    assertParts("sensu Leloup, 1981 not Gould, 1846", false, "Leloup, 1981", "Gould, 1846");
    assertParts("sensu A.Adams, 1852, not Lamarck, 1822", false, "A.Adams, 1852", "Lamarck, 1822");
    assertParts("s. auct. non (Fr) Rea", true, null, "(Fr) Rea");
    assertParts("sensu auctt. nec (Werneburg, 1864)", true, null, "(Werneburg, 1864)");
    assertParts("sensu auct.", true, null);
    assertParts("auct.", true, null);
    assertParts("ss. auct. p.p.", true, null);
    assertParts("auctorum non Heyne, 1899", true, null, "Heyne, 1899");
    assertParts("non Smith, 1893", false, null, "Smith, 1893");
    assertParts("ss. Romagnesi non Kauffman", false, "Romagnesi", "Kauffman");
    assertParts("Kuroda MS sensu Matsukuma, Okutani & Habe, 1991", false, "Matsukuma, Okutani & Habe, 1991");
    // the sense after the exclusion, several exclusions, a bracket around the note
    assertParts("nec Hampson sensu Gaede, 1917", false, "Gaede, 1917", "Hampson");
    assertParts("non Leoret nec Martin-Donios", false, null, "Leoret", "Martin-Donios");
    assertParts("auct. (not R.A.Philippi, 1844)", true, null, "R.A.Philippi, 1844");
    // an upper case S. is an initial
    assertParts("non J. S. Smith", false, null, "J. S. Smith");

    // no note of a misapplication
    assertTrue(p(null).isEmpty());
    assertTrue(p(" ").isEmpty());
    assertTrue(p("sp. 1").isEmpty());
    assertTrue(p("s.l.").isEmpty());
    assertTrue(p("sensu lato").isEmpty());
    assertTrue(p("s. str.").isEmpty());
  }

  @Test
  public void sameSenseCitedDifferently() {
    assertEquals(Equality.EQUAL, cmp("sensu Hutton, 1873", "sensu F. W. Hutton, 1873"));
    assertEquals(Equality.EQUAL, cmp("sensu Allan, 1956", "sensu J. K. Allan, 1956"));
    assertEquals(Equality.EQUAL, cmp("sensu A.Adams, 1852, not Lamarck, 1822", "sensu A. Adams, 1852"));
    assertEquals(Equality.EQUAL, cmp("auct. non Gould, 1850", "auct. non A. A. Gould, 1850"));
    assertEquals(Equality.EQUAL, cmp("sensu Schuurmans Stekhoven & De Coninck, 1933",
      "sensu Schuurmans Stekhoven & De Coninck, 1931"));
    assertEquals(Equality.EQUAL, cmp("sensu auct.", "auct."));
    assertEquals(Equality.EQUAL, cmp("sensu Gaede, 1917, nec Hampson", "nec Hampson sensu Gaede, 1917"));
    assertEquals(Equality.EQUAL, cmp("non Leoret nec Martin-Donios", "nec Martin-Donios non Leoret"));
    // the name it is not on one side only tells nothing, nor does a shorter list of them
    assertEquals(Equality.EQUAL, cmp("auct.", "auct. non Wight & Arn."));
    assertEquals(Equality.EQUAL, cmp("non Leoret", "nec Martin-Donios non Leoret"));
  }

  @Test
  public void differentSense() {
    assertEquals(Equality.DIFFERENT, cmp("sensu Burton, 1959", "sensu Tsurnamal, 1969"));
    assertEquals(Equality.DIFFERENT, cmp("sensu Coe, 1943", "sensu Riser, 1993"));
    assertEquals(Equality.DIFFERENT, cmp("sensu Smith", "sensu Jones"));
    // authors in general against a named one
    assertEquals(Equality.DIFFERENT, cmp("auct.", "sensu Smith"));
    // the same sense, but not the same names it excludes
    assertEquals(Equality.DIFFERENT, cmp("auct. non Wight & Arn.", "auct. non L."));
    assertEquals(Equality.DIFFERENT, cmp("nec Bychowsky, 1936; nec Birgi & Lambert, 1987",
      "nec Bychowsky, 1936; nec Mizelle, 1937"));
  }

  @Test
  public void nothingInCommon() {
    assertEquals(Equality.UNKNOWN, cmp("sensu Smith", "non Jones"));
    assertEquals(Equality.UNKNOWN, cmp("sp. 1", "sp. 2"));
    assertEquals(Equality.UNKNOWN, cmp(null, "sensu Smith"));
  }
}
