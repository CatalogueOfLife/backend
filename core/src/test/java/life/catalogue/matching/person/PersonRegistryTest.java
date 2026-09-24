package life.catalogue.matching.person;

import life.catalogue.api.vocab.TaxGroup;

import org.gbif.nameparser.api.NomCode;

import java.util.List;
import java.util.Set;

import org.junit.Test;

import static org.junit.Assert.*;

public class PersonRegistryTest {

  static Person person(String id, String wikidata, String family, String given, String suffix) {
    return new Person(id, wikidata, null, null, List.of(), family, given, suffix, null, null, null, null, Set.of(),
      Provenance.WIKIDATA);
  }

  static final Person SOWERBY1 = person("wd:Q1", "Q1", "Sowerby", "George Brettingham", "I");
  static final Person SOWERBY2 = person("wd:Q2", "Q2", "Sowerby", "George Brettingham", "II");
  static final Person SWARTZ = person("wd:Q3", "Q3", "Swartz", "Olof", null);

  static PersonRegistry registry() {
    return new PersonRegistry(new PersonFiles.Content(
      List.of(SOWERBY1, SOWERBY2, SWARTZ),
      List.of(
        new PersonName("wd:Q1", "G.B.Sowerby I", NameKind.CITATION, FormCode.ZOO, Provenance.WIKIDATA),
        new PersonName("wd:Q2", "G.B.Sowerby II", NameKind.CITATION, FormCode.ZOO, Provenance.WIKIDATA),
        new PersonName("wd:Q3", "Sw.", NameKind.STANDARD, FormCode.BOT, Provenance.IPNI),
        new PersonName("wd:Q3", "Olof Swartz", NameKind.FULL, FormCode.ANY, Provenance.WIKIDATA)
      ),
      List.of(new PersonRelation("wd:Q2", RelationType.PARENT, "wd:Q1", Provenance.WIKIDATA))
    ));
  }

  @Test
  public void citationsResolveByTheirNormalizedKey() {
    var reg = registry();
    assertEquals(Set.of(SOWERBY2), reg.candidates("G. B. Sowerby II", NomCode.ZOOLOGICAL));
    // a surname-first citation derives nothing: its key keeps the comma
    assertEquals(Set.of(), reg.candidates("Swartz, O.", NomCode.BOTANICAL));
    assertEquals(Set.of(SWARTZ), reg.candidates("Olof Swartz", NomCode.ZOOLOGICAL));
  }

  /** a botanical standard form is none in zoology */
  @Test
  public void codeRestrictsForms() {
    var reg = registry();
    assertEquals(Set.of(SWARTZ), reg.candidates("Sw.", NomCode.BOTANICAL));
    assertEquals(Set.of(SWARTZ), reg.candidates("Sw.", null));
    assertEquals(Set.of(), reg.candidates("Sw.", NomCode.ZOOLOGICAL));
  }

  /** initials of the given names with family name and suffix, and the bare family name, are derived when loading */
  @Test
  public void derivedForms() {
    var reg = registry();
    assertEquals(Set.of(SOWERBY1, SOWERBY2), reg.candidates("Sowerby", NomCode.ZOOLOGICAL));
    assertEquals(Set.of(SOWERBY2), reg.candidates("G.B. Sowerby II", NomCode.BOTANICAL));
    assertEquals(Set.of(SWARTZ), reg.candidates("O. Swartz", NomCode.BOTANICAL));
    assertEquals(Set.of(), reg.candidates("Linnaeus", null));
  }

  /**
   * Wikidata often lists fewer given names than the label holds (G. B. Sowerby II has only "George"), and IPNI puts a
   * particle at the end of the forename. Initials come from the label too, and particles stay words.
   */
  @Test
  public void initialsOfTheLabelAndParticles() {
    var sowerby = new Person("wd:Q1223045", "Q1223045", null, null, List.of(), "Sowerby", "George", "II", null, null, null, null,
      Set.of(), Provenance.WIKIDATA);
    var candolle = new Person("ipni:1-1", null, "1-1", null, List.of(), "Candolle", "Augustin Pyramus de", null, null, null, null, null,
      Set.of(), Provenance.IPNI);
    var reg = new PersonRegistry(new PersonFiles.Content(List.of(sowerby, candolle),
      List.of(new PersonName("wd:Q1223045", "George Brettingham Sowerby II", NameKind.FULL, FormCode.ANY, Provenance.WIKIDATA),
        new PersonName("ipni:1-1", "DC.", NameKind.STANDARD, FormCode.BOT, Provenance.IPNI)),
      List.of()));
    assertEquals(Set.of(sowerby), reg.candidates("G.B. Sowerby II", NomCode.ZOOLOGICAL));
    assertEquals(Set.of(candolle), reg.candidates("A. P. de Candolle", NomCode.BOTANICAL));
    assertEquals(Set.of(candolle), reg.candidates("A.P.de Candolle", NomCode.BOTANICAL));
  }

  @Test
  public void activeBeforeBornIsAProblem() {
    var p = new Person("wd:Q1", "Q1", null, null, List.of(), null, null, null, 1900, null, 1844, null, Set.of(), Provenance.WIKIDATA);
    var reg = new PersonRegistry(new PersonFiles.Content(List.of(p),
      List.of(new PersonName("wd:Q1", "A. Doe", NameKind.FULL, FormCode.ANY, Provenance.WIKIDATA)), List.of()));
    assertEquals(List.of("wd:Q1 was active before it was born"), reg.problems());
  }

  /** a person without an id is reported, never a crash of whoever loads the files */
  @Test
  public void personWithoutIdIsAProblem() {
    var p = new Person(null, null, null, null, List.of(), "Doe", null, null, null, null, null, null, Set.of(), Provenance.WIKIDATA);
    var reg = new PersonRegistry(new PersonFiles.Content(List.of(SWARTZ, p),
      List.of(new PersonName("wd:Q3", "Sw.", NameKind.STANDARD, FormCode.BOT, Provenance.IPNI)), List.of()));
    assertEquals(List.of("a person without an id: Doe"), reg.problems());
    assertEquals(Set.of(SWARTZ), reg.candidates("Sw.", NomCode.BOTANICAL));
  }

  @Test
  public void relativesBothWays() {
    var reg = registry();
    assertEquals(Set.of(SOWERBY1), reg.relatives(SOWERBY2));
    assertEquals(Set.of(SOWERBY2), reg.relatives(SOWERBY1));
    assertEquals(Set.of(), reg.relatives(SWARTZ));
  }

  @Test
  public void anyIdResolves() {
    var moved = new Person("wd:Q9", "Q9", "4084-1", null, List.of("ipni:4084-1"), "Hooker", "Joseph Dalton", "f.", 1817, 1911,
      null, null, Set.of(TaxGroup.Angiosperms), Provenance.WIKIDATA);
    var reg = new PersonRegistry(new PersonFiles.Content(List.of(moved),
      List.of(new PersonName("ipni:4084-1", "Hook.f.", NameKind.STANDARD, FormCode.BOT, Provenance.CURATED)), List.of()));
    assertSame(moved, reg.get("ipni:4084-1"));
    assertSame(moved, reg.get("wd:Q9"));
    assertEquals(Set.of(moved), reg.candidates("Hook. f.", NomCode.BOTANICAL));
    assertEquals(List.of(), reg.problems());
    assertEquals(1, reg.size());
  }

  @Test
  public void problems() {
    var reg = new PersonRegistry(new PersonFiles.Content(
      List.of(SWARTZ, person("wd:Q7", "Q8", "Doe", null, null), person("clb:1", "Q9", "Roe", null, null),
        new Person("wd:Q5", "Q5", null, null, List.of("wd:Q3"), "Poe", null, null, 1900, 1850, null, null, Set.of(), Provenance.WIKIDATA)),
      List.of(new PersonName("wd:Q3", "Sw.", NameKind.STANDARD, FormCode.BOT, Provenance.IPNI),
        new PersonName("wd:Q404", "Nobody", NameKind.FULL, FormCode.ANY, Provenance.CURATED)),
      List.of(new PersonRelation("wd:Q3", RelationType.SIBLING, "wd:Q404", Provenance.CURATED))
    ));
    var p = String.join("\n", reg.problems());
    assertTrue(p, p.contains("wd:Q7 should be wd:Q8"));
    assertTrue(p, p.contains("clb:1 is local but has authority ids"));
    assertTrue(p, p.contains("id wd:Q3 is held by wd:Q3 and wd:Q5"));
    assertTrue(p, p.contains("wd:Q5 was born after it died"));
    assertTrue(p, p.contains("unknown person wd:Q404"));
    assertTrue(p, p.contains("wd:Q7 has no name"));
  }

  /** relatives are cited by the family name and suffix alone: "Hooker f.", "Sowerby II" */
  @Test
  public void familyWithSuffix() {
    var hooker = new Person("wd:Q157501", "Q157501", "4084-1", null, List.of(), "Hooker", "Joseph Dalton", "f.", 1817, 1911, null,
      null, Set.of(), Provenance.WIKIDATA);
    var reg = new PersonRegistry(new PersonFiles.Content(List.of(hooker, SOWERBY2),
      List.of(new PersonName("wd:Q157501", "Hook.f.", NameKind.STANDARD, FormCode.BOT, Provenance.IPNI),
        new PersonName("wd:Q2", "G.B.Sowerby II", NameKind.CITATION, FormCode.ZOO, Provenance.WIKIDATA)),
      List.of()));
    assertEquals(Set.of(hooker), reg.candidates("Hooker f.", NomCode.BOTANICAL));
    assertEquals(Set.of(hooker), reg.candidates("Hooker fil.", NomCode.BOTANICAL));
    assertEquals(Set.of(SOWERBY2), reg.candidates("Sowerby II", NomCode.ZOOLOGICAL));
  }

  /** IPNI lists alternative forenames in brackets, and a variant ending with the family name gives initials too */
  @Test
  public void initialsOfVariantsWithoutBracketedAlternatives() {
    assertEquals("C. B. ", PersonRegistry.initials("Carl (Karl, Carel, Carolus) Bořivoj (Boriwog, Boriwag)"));
    var presl = new Person("wd:Q5", "Q5", null, null, List.of(), "Presl", "Carl (Karl, Carel, Carolus) Bořivoj (Boriwog, Boriwag)",
      null, 1794, 1852, null, null, Set.of(), Provenance.IPNI);
    var reg = new PersonRegistry(new PersonFiles.Content(List.of(presl),
      List.of(new PersonName("wd:Q5", "C.Presl", NameKind.STANDARD, FormCode.BOT, Provenance.IPNI),
        new PersonName("wd:Q5", "Karel Bořivoj Presl", NameKind.VARIANT, FormCode.ANY, Provenance.WIKIDATA)),
      List.of()));
    assertEquals(Set.of(presl), reg.candidates("K.B. Presl", NomCode.BOTANICAL));
    assertEquals(Set.of(presl), reg.candidates("C. B. Presl", NomCode.BOTANICAL));
  }

  /** the keys of a person's forms, derived ones included, as the fallback compares them */
  @Test
  public void keysOfAPerson() {
    var reg = registry();
    assertEquals(Set.of("sw", "olof swartz", "o swartz", "swartz"), reg.keys(SWARTZ, NomCode.BOTANICAL));
    assertEquals(Set.of("olof swartz", "o swartz", "swartz"), reg.keys(SWARTZ, NomCode.ZOOLOGICAL));
  }
}
