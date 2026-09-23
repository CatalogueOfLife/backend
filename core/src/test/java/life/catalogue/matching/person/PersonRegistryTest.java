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
}
