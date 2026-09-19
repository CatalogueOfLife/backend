package life.catalogue.common.tax.authormap;

import life.catalogue.common.tax.authormap.AuthorMapSurnameFix.Decision;
import life.catalogue.common.tax.authormap.AuthorMapSurnameFix.Kind;
import life.catalogue.common.tax.authormap.IpniAuthorLookup.IpniAuthor;

import java.util.*;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Real authormap rows and the answers IPNI gave for their standard forms.
 */
public class AuthorMapSurnameFixTest {

  @Test
  public void extendsCollapsedSurname() {
    assertChanged(Kind.EXTENDED, "A A Fischer von Waldheim",
      row("A A F von Waldheim", "A.A.Fisch.Waldh.", "Alexandr Alexandrovich Fischer von Waldheim"),
      ipni("A.A.Fisch.Waldh.", "Alexandr Alexandrovich (Alexander Alexandrowitz)", "Fischer von Waldheim"));
    // only IPNI knows this one, the standard form abbreviates the collapsed word itself
    assertChanged(Kind.EXTENDED, "A M F J Palisot de Beauvois",
      row("A M F J P de Beauvois", "P.Beauv.", "Ambroise Marie François Joseph Palisot de Beauvois"),
      ipni("P.Beauv.", "Ambroise Marie François Joseph", "Palisot de Beauvois"));
    // no particle
    assertChanged(Kind.EXTENDED, "H Ruiz López",
      row("H R López", "Ruiz", "Hipólito Ruiz López"),
      ipni("Ruiz", "Hipólito", "Ruiz López"));
    assertChanged(Kind.EXTENDED, "M Sessé y Lacasta",
      row("M S y Lacasta", "Sessé", "Martín Sessé y Lacasta"),
      ipni("Sessé", "Martín", "Sessé y Lacasta"));
    // a capitalised particle became an initial too
    assertChanged(Kind.EXTENDED, "G De Notaris",
      row("G D Notaris", "De Not.", "Giuseppe De Notaris"),
      ipni("De Not.", "Giuseppe(Josephus)", "De Notaris"));
  }

  @Test
  public void neverShortens() {
    // IPNI holds only the first part here, the canonical keeps the rest
    assertChanged(Kind.EXTENDED, "A Kerner von Marilaun",
      row("A K von Marilaun", "A.Kern.", "Anton Kerner von Marilaun"),
      ipni("A.Kern.", "Anton Joseph", "Kerner"));
    // and only the last part here
    assertUnchanged(row("A H C de Coincy", "Coincy", "Auguste Henri Cornut de Coincy"),
      ipni("Coincy", "Auguste Henri Cornut de", "Coincy"));
  }

  @Test
  public void givenNamesStay() {
    assertUnchanged(row("A D Danilov", "A.D.Danilov", "Alexander Danilovich Danilov"),
      ipni("A.D.Danilov", "Alexander Danilovich", "Danilov"));
    assertUnchanged(row("H L Li", "H.L.Li", "Hui Lin Li"),
      ipni("H.L.Li", "Hui Lin", "Li"));
    assertUnchanged(row("A G M W A Robyns", "A.Robyns", "André Georges Marie Walter Albert Robyns"),
      ipni("A.Robyns", "André Georges Marie Walter Albert", "Robyns"));
    // a given name spelled like the surname is no start of it
    assertUnchanged(row("G A W Arnott", "Arn.", "George Arnott Walker Arnott"),
      ipni("Arn.", "George Arnott Walker", "Arnott"));
  }

  @Test
  public void dropsSuffix() {
    assertChanged(Kind.SUFFIX, "B L Turner",
      row("B L T Sr.", "B.L.Turner", "Billie Lee Turner Sr."),
      ipni("B.L.Turner", "Billie Lee", "Turner"));
    // the surname precedes the suffix even without IPNI
    assertChanged(Kind.SUFFIX, "A M Harvill",
      row("A M H Jr", "Harvill", "Alton McCaleb Harvill, Jr"), null);
    assertChanged(Kind.SUFFIX, "J E Bowman",
      row("J E B the Elder", "Bowman", "John Eddowes Bowman the Elder"),
      ipni("Bowman", "John Eddowes", "Bowman"));
  }

  @Test
  public void fatherAndSon() {
    List<AuthorEntry> rows = List.of(
      row("J K Jr.", "J.Kickx f.", "Jean Kickx, Jr."),
      row("J K Sr.", "J.Kickx", "Jean Kickx, Sr."),
      row("J K Morton", "J.K.Morton", "John Kenneth Morton"));
    List<Decision> decisions = List.of(
      AuthorMapSurnameFix.decide(rows.get(0), ipni("J.Kickx f.", "Jean", "Kickx")),
      AuthorMapSurnameFix.decide(rows.get(1), ipni("J.Kickx", "Jean", "Kickx")),
      AuthorMapSurnameFix.decide(rows.get(2), ipni("J.K.Morton", "John Kenneth", "Morton")));
    List<String> report = new ArrayList<>();
    assertEquals(List.of("J Kickx f.", "J Kickx", "J K Morton"), AuthorMapSurnameFix.resolveCollisions(rows, decisions, report));
    assertEquals(1, report.size());
  }

  @Test
  public void unresolvedCollisionKeepsSuffix() {
    List<AuthorEntry> rows = List.of(
      row("J L FRS", "Lightf.", "John Lightfoot FRS"),
      row("J Lightfoot", "J.Lightf.", "John Lightfoot"));
    List<Decision> decisions = List.of(
      AuthorMapSurnameFix.decide(rows.get(0), ipni("Lightf.", "John", "Lightfoot")),
      AuthorMapSurnameFix.decide(rows.get(1), null));
    List<String> report = new ArrayList<>();
    assertEquals(List.of("J L FRS", "J Lightfoot"), AuthorMapSurnameFix.resolveCollisions(rows, decisions, report));
    assertEquals(1, report.size());
  }

  @Test
  public void duplicateRowsAreNoCollision() {
    AuthorEntry ruiz = row("H R López", "Ruiz", "Hipólito Ruiz López");
    List<AuthorEntry> rows = List.of(ruiz, ruiz);
    Decision d = AuthorMapSurnameFix.decide(ruiz, ipni("Ruiz", "Hipólito", "Ruiz López"));
    List<String> report = new ArrayList<>();
    assertEquals(List.of("H Ruiz López", "H Ruiz López"), AuthorMapSurnameFix.resolveCollisions(rows, List.of(d, d), report));
    assertTrue(report.isEmpty());
  }

  @Test
  public void reviewOnly() {
    // IPNI names a surname the row does not know
    assertEquals(Kind.REVIEW, AuthorMapSurnameFix.decide(row("A B Foo", "A.B.Foo", "Anna Berta Foo"), ipni("A.B.Foo", "Anna Berta", "Bar")).kind());
    // or one that covers only part of the collapsed words, the rest being a title
    assertEquals(Kind.REVIEW, AuthorMapSurnameFix.decide(row("J W R von Rawicz", "Warsz.", "Józef Warszewicz Ritter von Rawicz"),
      ipni("Warsz.", "Josef Ritter von Rawicz", "Warszewicz")).kind());
    // no full name to spell initials out from, e.g. the hand made Bory row
    Decision bory = AuthorMapSurnameFix.decide(row("J B G M Bory de Saint-Vincent", "Bory", "Jean Baptiste Bory de Saint-Vincent"),
      ipni("Bory", "Jean Baptiste Geneviève Marcellin", "Bory"));
    assertEquals(Kind.REVIEW, bory.kind());
    assertEquals("J B G M Bory de Saint-Vincent", bory.canonical());
    assertEquals(Kind.NOT_FOUND, AuthorMapSurnameFix.decide(row("A B Foo", "A.B.Foo", "Anna Berta Foo"), null).kind());
  }

  @Test
  public void skipsNonInitialCanonicals() {
    assertEquals(Kind.SKIP, AuthorMapSurnameFix.decide(row("Carl Linnaeus", "Carl Linnaeus"), null).kind());
    assertEquals(Kind.UNCHANGED, AuthorMapSurnameFix.decide(row("C Linnaeus", "L.", "Carl Linnaeus"), null).kind());
    assertFalse(AuthorMapSurnameFix.needsLookup(AuthorMapSurnameFix.parse(row("C Linnaeus", "L.", "Carl Linnaeus"))));
    assertTrue(AuthorMapSurnameFix.needsLookup(AuthorMapSurnameFix.parse(row("H R López", "Ruiz", "Hipólito Ruiz López"))));
  }

  @Test
  public void inventedInitials() {
    // Braam is a nickname
    assertTrue(AuthorMapSurnameFix.moreInitialsThanForenames("A E B van Wyk", ipni("A.E.van Wyk", "Abraham Erasmus", "van Wyk")));
    assertFalse(AuthorMapSurnameFix.moreInitialsThanForenames("A H C de Coincy", ipni("Coincy", "Auguste Henri Cornut de", "Coincy")));
    assertFalse(AuthorMapSurnameFix.moreInitialsThanForenames("M Vahl", ipni("M.Vahl", "Martin (II)", "Vahl")));
    // dotted and parenthesised forenames count
    assertFalse(AuthorMapSurnameFix.moreInitialsThanForenames("A C Tangavelou", ipni("Tangav.", "A.C.", "Tangavelou")));
    assertFalse(AuthorMapSurnameFix.moreInitialsThanForenames("C D White", ipni("C.D.White", "(Charles) David", "White")));
    // a title is no forename
    assertTrue(AuthorMapSurnameFix.moreInitialsThanForenames("B P P de Lapeyrouse", ipni("Lapeyr.", "Philippe Picot de", "Lapeyrouse")));
  }

  private static void assertChanged(Kind kind, String expected, AuthorEntry row, IpniAuthor ipni) {
    Decision d = AuthorMapSurnameFix.decide(row, ipni);
    assertEquals(kind, d.kind());
    assertEquals(expected, d.canonical());
  }

  private static void assertUnchanged(AuthorEntry row, IpniAuthor ipni) {
    Decision d = AuthorMapSurnameFix.decide(row, ipni);
    assertEquals(Kind.UNCHANGED, d.kind());
    assertEquals(row.canonical(), d.canonical());
  }

  private static AuthorEntry row(String canonical, String... aliases) {
    return new AuthorEntry(canonical, AuthorCode.BOT, List.of(aliases));
  }

  private static IpniAuthor ipni(String std, String forename, String surname) {
    return new IpniAuthor(std, forename, surname);
  }
}
