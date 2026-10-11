package life.catalogue.common.tax;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NomCode;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

public class AuthorAtomsTest {

  @Test
  public void encode() {
    var a = Authorship.authors("Denis", "Schiffermüller");
    assertEquals(List.of("Denis", "Schiffermüller"), AuthorAtoms.encode(a, NomCode.ZOOLOGICAL));

    a.setAnonymous(true);
    assertEquals(List.of("[Denis]", "[Schiffermüller]"), AuthorAtoms.encode(a, NomCode.ZOOLOGICAL));

    a = new Authorship();
    a.setAnonymous(true);
    assertEquals(List.of("Anon."), AuthorAtoms.encode(a, NomCode.ZOOLOGICAL));
    assertEquals(List.of("Anon."), AuthorAtoms.encode(a, null));
    assertEquals(List.of("anon."), AuthorAtoms.encode(a, NomCode.BOTANICAL));
  }

  @Test
  public void decode() {
    assertDecoded(null, false);
    assertDecoded(List.of(), false);
    assertDecoded(List.of("Denis", "Schiffermüller"), false, "Denis", "Schiffermüller");
    assertDecoded(List.of("[Denis]", "[Schiffermüller]"), true, "Denis", "Schiffermüller");
    assertDecoded(List.of("[ Lacepède ]"), true, "Lacepède");
    // only a fully bracketed team is attributed
    assertDecoded(List.of("[Tourn.]", "L."), false, "[Tourn.]", "L.");
    for (var anon : List.of("Anon.", "anon.", "anon", "Anonymous", "anonymus")) {
      assertDecoded(List.of(anon), true);
    }
    // an anonymous team member stays a string
    assertDecoded(List.of("Anonymous", "Krefft"), false, "Anonymous", "Krefft");
    assertDecoded(List.of("Anonymi"), false, "Anonymi");
  }

  @Test
  public void roundtrip() {
    for (var code : new NomCode[]{null, NomCode.ZOOLOGICAL, NomCode.BOTANICAL}) {
      for (var a : List.of(new Authorship(), Authorship.authors("Lacepède"), Authorship.authors("Denis", "Schiffermüller"))) {
        for (boolean anon : new boolean[]{false, true}) {
          a.setAnonymous(anon);
          var a2 = new Authorship();
          AuthorAtoms.decode(AuthorAtoms.encode(a, code), a2);
          assertEquals(a, a2);
        }
      }
    }
  }

  @Test
  public void years() {
    var a = Authorship.yearAuthors("1916", "Strand");
    assertEquals("1916", AuthorAtoms.encodeYear(a));
    a.setBracketedYear(true);
    assertEquals("[1916]", AuthorAtoms.encodeYear(a));
    assertNull(AuthorAtoms.encodeYear(new Authorship()));

    assertDecodedYear(null, false, null);
    assertDecodedYear("1916", false, "1916");
    assertDecodedYear("[1916]", true, "1916");
    assertDecodedYear("[ 1916 ]", true, "1916");
    assertDecodedYear("[]", false, "[]");

    for (String year : new String[]{null, "1916"}) {
      for (boolean bracketed : new boolean[]{false, true}) {
        a = new Authorship();
        a.setYear(year);
        a.setBracketedYear(bracketed && year != null);
        var a2 = new Authorship();
        AuthorAtoms.decodeYear(AuthorAtoms.encodeYear(a), a2);
        assertEquals(a, a2);
      }
    }
  }

  private static void assertDecodedYear(String year, boolean bracketed, String expected) {
    var a = new Authorship();
    AuthorAtoms.decodeYear(year, a);
    assertEquals(bracketed, a.isBracketedYear());
    assertEquals(expected, a.getYear());
  }

  private static void assertDecoded(List<String> authors, boolean anonymous, String... expected) {
    var a = new Authorship();
    AuthorAtoms.decode(authors, a);
    assertEquals(anonymous, a.isAnonymous());
    if (authors == null) {
      assertNull(a.getAuthors());
    } else {
      assertEquals(List.of(expected), a.getAuthors());
    }
  }
}
