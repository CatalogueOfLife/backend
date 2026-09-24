package life.catalogue.matching.person;

import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonName;
import life.catalogue.api.model.PersonRelation;
import life.catalogue.api.vocab.PersonFormCode;
import life.catalogue.api.vocab.PersonNameKind;
import life.catalogue.api.vocab.PersonRelationType;
import life.catalogue.api.vocab.PersonSource;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.common.tax.AuthorshipNormalizer;

import org.gbif.nameparser.api.NomCode;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import static org.junit.Assert.*;

public class MemoryPersonStoreTest {

  static Person person(String id, String wikidata, String family, String given, String suffix) {
    return new Person(id, wikidata, null, null, List.of(), family, given, suffix, null, null, null, null, Set.of(),
      PersonSource.WIKIDATA);
  }

  static final Person SOWERBY1 = person("wd:Q1", "Q1", "Sowerby", "George Brettingham", "I");
  static final Person SOWERBY2 = person("wd:Q2", "Q2", "Sowerby", "George Brettingham", "II");
  static final Person SWARTZ = person("wd:Q3", "Q3", "Swartz", "Olof", null);

  static MemoryPersonStore registry() {
    return new MemoryPersonStore(new PersonFiles.Content(
      List.of(SOWERBY1, SOWERBY2, SWARTZ),
      List.of(
        new PersonName("wd:Q1", "G.B.Sowerby I", PersonNameKind.CITATION, PersonFormCode.ZOO, PersonSource.WIKIDATA),
        new PersonName("wd:Q2", "G.B.Sowerby II", PersonNameKind.CITATION, PersonFormCode.ZOO, PersonSource.WIKIDATA),
        new PersonName("wd:Q3", "Sw.", PersonNameKind.STANDARD, PersonFormCode.BOT, PersonSource.IPNI),
        new PersonName("wd:Q3", "Olof Swartz", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.WIKIDATA)
      ),
      List.of(new PersonRelation("wd:Q2", PersonRelationType.PARENT, "wd:Q1", PersonSource.WIKIDATA))
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
      Set.of(), PersonSource.WIKIDATA);
    var candolle = new Person("ipni:1-1", null, "1-1", null, List.of(), "Candolle", "Augustin Pyramus de", null, null, null, null, null,
      Set.of(), PersonSource.IPNI);
    var reg = new MemoryPersonStore(new PersonFiles.Content(List.of(sowerby, candolle),
      List.of(new PersonName("wd:Q1223045", "George Brettingham Sowerby II", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.WIKIDATA),
        new PersonName("ipni:1-1", "DC.", PersonNameKind.STANDARD, PersonFormCode.BOT, PersonSource.IPNI)),
      List.of()));
    assertEquals(Set.of(sowerby), reg.candidates("G.B. Sowerby II", NomCode.ZOOLOGICAL));
    assertEquals(Set.of(candolle), reg.candidates("A. P. de Candolle", NomCode.BOTANICAL));
    assertEquals(Set.of(candolle), reg.candidates("A.P.de Candolle", NomCode.BOTANICAL));
  }

  @Test
  public void activeBeforeBornIsAProblem() {
    var p = new Person("wd:Q1", "Q1", null, null, List.of(), null, null, null, 1900, null, 1844, null, Set.of(), PersonSource.WIKIDATA);
    var reg = new MemoryPersonStore(new PersonFiles.Content(List.of(p),
      List.of(new PersonName("wd:Q1", "A. Doe", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.WIKIDATA)), List.of()));
    assertEquals(List.of("wd:Q1 was active before it was born"), reg.problems());
  }

  /** a person without an id is reported, never a crash of whoever loads the files */
  @Test
  public void personWithoutIdIsAProblem() {
    var p = new Person(null, null, null, null, List.of(), "Doe", null, null, null, null, null, null, Set.of(), PersonSource.WIKIDATA);
    var reg = new MemoryPersonStore(new PersonFiles.Content(List.of(SWARTZ, p),
      List.of(new PersonName("wd:Q3", "Sw.", PersonNameKind.STANDARD, PersonFormCode.BOT, PersonSource.IPNI)), List.of()));
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
      null, null, Set.of(TaxGroup.Angiosperms), PersonSource.WIKIDATA);
    var reg = new MemoryPersonStore(new PersonFiles.Content(List.of(moved),
      List.of(new PersonName("ipni:4084-1", "Hook.f.", PersonNameKind.STANDARD, PersonFormCode.BOT, PersonSource.CURATED)), List.of()));
    assertSame(moved, reg.get("ipni:4084-1"));
    assertSame(moved, reg.get("wd:Q9"));
    assertEquals(Set.of(moved), reg.candidates("Hook. f.", NomCode.BOTANICAL));
    assertEquals(List.of(), reg.problems());
    assertEquals(1, reg.size());
  }

  @Test
  public void problems() {
    var reg = new MemoryPersonStore(new PersonFiles.Content(
      List.of(SWARTZ, person("wd:Q7", "Q8", "Doe", null, null), person("clb:1", "Q9", "Roe", null, null),
        new Person("wd:Q5", "Q5", null, null, List.of("wd:Q3"), "Poe", null, null, 1900, 1850, null, null, Set.of(), PersonSource.WIKIDATA)),
      List.of(new PersonName("wd:Q3", "Sw.", PersonNameKind.STANDARD, PersonFormCode.BOT, PersonSource.IPNI),
        new PersonName("wd:Q404", "Nobody", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.CURATED)),
      List.of(new PersonRelation("wd:Q3", PersonRelationType.SIBLING, "wd:Q404", PersonSource.CURATED))
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
      null, Set.of(), PersonSource.WIKIDATA);
    var reg = new MemoryPersonStore(new PersonFiles.Content(List.of(hooker, SOWERBY2),
      List.of(new PersonName("wd:Q157501", "Hook.f.", PersonNameKind.STANDARD, PersonFormCode.BOT, PersonSource.IPNI),
        new PersonName("wd:Q2", "G.B.Sowerby II", PersonNameKind.CITATION, PersonFormCode.ZOO, PersonSource.WIKIDATA)),
      List.of()));
    assertEquals(Set.of(hooker), reg.candidates("Hooker f.", NomCode.BOTANICAL));
    assertEquals(Set.of(hooker), reg.candidates("Hooker fil.", NomCode.BOTANICAL));
    assertEquals(Set.of(SOWERBY2), reg.candidates("Sowerby II", NomCode.ZOOLOGICAL));
  }

  /** IPNI lists alternative forenames in brackets, and a variant ending with the family name gives initials too */
  @Test
  public void initialsOfVariantsWithoutBracketedAlternatives() {
    assertEquals("C. B. ", MemoryPersonStore.initials("Carl (Karl, Carel, Carolus) Bořivoj (Boriwog, Boriwag)"));
    var presl = new Person("wd:Q5", "Q5", null, null, List.of(), "Presl", "Carl (Karl, Carel, Carolus) Bořivoj (Boriwog, Boriwag)",
      null, 1794, 1852, null, null, Set.of(), PersonSource.IPNI);
    var reg = new MemoryPersonStore(new PersonFiles.Content(List.of(presl),
      List.of(new PersonName("wd:Q5", "C.Presl", PersonNameKind.STANDARD, PersonFormCode.BOT, PersonSource.IPNI),
        new PersonName("wd:Q5", "Karel Bořivoj Presl", PersonNameKind.VARIANT, PersonFormCode.ANY, PersonSource.WIKIDATA)),
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

  /** a variant written with initials, "J.C. Sowerby", gives both initials, not his father's bare "J. Sowerby" */
  @Test
  public void initialsOfDottedNames() {
    assertEquals("J. C. ", MemoryPersonStore.initials("J.C."));
    var jdc = new Person("wd:Q7", "Q7", null, null, List.of(), "Sowerby", "James de Carle", null, 1787, 1871, null, null, Set.of(),
      PersonSource.WIKIDATA);
    var reg = new MemoryPersonStore(new PersonFiles.Content(List.of(jdc),
      List.of(new PersonName("wd:Q7", "J.C. Sowerby", PersonNameKind.VARIANT, PersonFormCode.ANY, PersonSource.WIKIDATA)), List.of()));
    assertEquals(Set.of(), reg.candidates("J. Sowerby", NomCode.ZOOLOGICAL));
    assertEquals(Set.of(jdc), reg.candidates("J. C. Sowerby", NomCode.ZOOLOGICAL));
  }

  /**
   * The comparator hands over authors normalized already, and normalizing is not always idempotent: a capital Đ is only
   * folded once lower cased, and removing an e can make a new "ae", "oe" or "ue". Such names resolve all the same.
   */
  @Test
  public void normalizedCitationsOfUnstableKeys() {
    var dinh = person("wd:Q21", "Q21", "Đinh", "Van", null);
    var mcqueen = person("wd:Q22", "Q22", "McQueen", "Anna", null);
    var saeed = person("wd:Q23", "Q23", "Saeed", "Omar", null);
    var reg = new MemoryPersonStore(new PersonFiles.Content(List.of(dinh, mcqueen, saeed),
      List.of(new PersonName("wd:Q21", "Van Đinh", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.WIKIDATA),
        new PersonName("wd:Q22", "Anna McQueen", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.WIKIDATA),
        new PersonName("wd:Q23", "Omar Saeed", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.WIKIDATA)),
      List.of()));
    for (var e : Map.of("Đinh", dinh, "McQueen", mcqueen, "Saeed", saeed, "A. McQueen", mcqueen).entrySet()) {
      assertEquals(e.getKey(), Set.of(e.getValue()), reg.candidates(e.getKey(), NomCode.ZOOLOGICAL));
      assertEquals(e.getKey(), Set.of(e.getValue()), reg.candidates(AuthorshipNormalizer.normalize(e.getKey()), NomCode.ZOOLOGICAL));
    }
  }
}
