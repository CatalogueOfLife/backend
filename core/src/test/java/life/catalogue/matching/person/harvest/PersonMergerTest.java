package life.catalogue.matching.person.harvest;

import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonName;
import life.catalogue.api.model.PersonRelation;
import life.catalogue.api.vocab.PersonFormCode;
import life.catalogue.api.vocab.PersonNameKind;
import life.catalogue.api.vocab.PersonRelationType;
import life.catalogue.api.vocab.PersonSource;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.matching.person.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;

import static org.junit.Assert.*;

public class PersonMergerTest {
  static final LocalDate DAY = LocalDate.of(2026, 9, 24);

  static PersonRecord.Builder wd(String q) {
    var b = new PersonRecord.Builder(PersonSource.WIKIDATA);
    b.wikidata = q;
    return b;
  }

  static PersonRecord.Builder ipni(String id) {
    var b = new PersonRecord.Builder(PersonSource.IPNI);
    b.ipni = id;
    return b;
  }

  static PersonRecord.Builder zb(String id) {
    var b = new PersonRecord.Builder(PersonSource.ZOOBANK);
    b.zoobank = id;
    return b;
  }

  static PersonMerger.Result merge(PersonFiles.Content existing, PersonRecord... records) {
    var r = new PersonMerger(DAY).merge(existing, List.of(records), Map.of());
    assertEquals(List.of(), new MemoryPersonStore(r.content()).problems());
    return r;
  }

  static Person person(PersonFiles.Content c, String id) {
    return c.persons().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
  }

  /** Wikidata links the IPNI author through P586: one person, IPNI winning its years, the disagreement reported */
  @Test
  public void joinsOnAuthorityIds() {
    var w = wd("Q1");
    w.ipni = "4084-1";
    w.label("Joseph Dalton Hooker");
    w.born(1817);
    w.died(1912);
    w.name("Hook.f.", PersonNameKind.STANDARD, PersonFormCode.BOT);
    var i = ipni("4084-1");
    i.family("Hooker");
    i.given("Joseph Dalton");
    i.suffix = "f.";
    i.born(1817);
    i.died(1911);
    i.name("Hook.f.", PersonNameKind.STANDARD, PersonFormCode.BOT);
    i.groups.add(TaxGroup.Fungi);

    var r = merge(PersonFiles.Content.empty(), i.build(), w.build());
    assertEquals(1, r.content().persons().size());
    Person p = r.content().persons().get(0);
    assertEquals("wd:Q1", p.id());
    assertEquals("4084-1", p.ipni());
    assertEquals(Integer.valueOf(1911), p.died());
    assertEquals("Hooker", p.family());
    assertEquals("f.", p.suffix());
    assertEquals(Set.of(TaxGroup.Fungi), p.groups());
    assertEquals(PersonSource.IPNI, p.source());
    // one line per form, whichever source gave it first
    assertEquals(2, r.content().names().size());
    assertEquals(1, r.report().added);
    // years 1911 and 1912 are within 2 of each other
    assertEquals(List.of(), r.report().conflicts);
  }

  @Test
  public void reportsADisagreement() {
    var w = wd("Q1");
    w.ipni = "1-1";
    w.label("A B");
    w.born(1700);
    var i = ipni("1-1");
    i.name("A.B.", PersonNameKind.STANDARD, PersonFormCode.BOT);
    i.born(1750);
    var r = merge(PersonFiles.Content.empty(), w.build(), i.build());
    assertEquals(Integer.valueOf(1750), r.content().persons().get(0).born());
    assertEquals(1, r.report().conflicts.size());
    assertTrue(r.report().conflicts.get(0), r.report().conflicts.get(0).contains("born"));
  }

  /** an IPNI person that Wikidata links later moves to its Q-id and keeps the IPNI one as a former id */
  @Test
  public void gainsABetterId() {
    var existing = new PersonFiles.Content(
      List.of(new Person("ipni:9-1", null, "9-1", null, List.of(), "Sowerby", "James", null, null, null, null, null, Set.of(), PersonSource.IPNI)),
      List.of(new PersonName("ipni:9-1", "Sowerby", PersonNameKind.STANDARD, PersonFormCode.BOT, PersonSource.IPNI),
        new PersonName("ipni:9-1", "J. Sow.", PersonNameKind.VARIANT, PersonFormCode.BOT, PersonSource.CURATED)),
      List.of());
    var w = wd("Q5");
    w.ipni = "9-1";
    w.label("James Sowerby");
    var i = ipni("9-1");
    i.name("Sowerby", PersonNameKind.STANDARD, PersonFormCode.BOT);
    var r = merge(existing, w.build(), i.build());
    Person p = r.content().persons().get(0);
    assertEquals("wd:Q5", p.id());
    assertEquals(List.of("ipni:9-1"), p.formerIds());
    // the harvested line moves, the curated one stays as written and still resolves
    assertTrue(r.content().names().contains(new PersonName("wd:Q5", "Sowerby", PersonNameKind.STANDARD, PersonFormCode.BOT, PersonSource.IPNI)));
    assertTrue(r.content().names().contains(new PersonName("ipni:9-1", "J. Sow.", PersonNameKind.VARIANT, PersonFormCode.BOT, PersonSource.CURATED)));
  }

  /** two Wikidata items claiming one IPNI author are two persons until somebody merges the items */
  @Test
  public void twoItemsOneIpniId() {
    var a = wd("Q1");
    a.ipni = "5-1";
    a.label("Anna Smith");
    var b = wd("Q2");
    b.ipni = "5-1";
    b.label("Anna Smith");
    var i = ipni("5-1");
    i.name("A.Sm.", PersonNameKind.STANDARD, PersonFormCode.BOT);
    var r = merge(PersonFiles.Content.empty(), a.build(), b.build(), i.build());
    assertEquals(2, r.content().persons().size());
    assertEquals("5-1", person(r.content(), "wd:Q1").ipni());
    assertNull(person(r.content(), "wd:Q2").ipni());
    assertEquals(1, r.report().ambiguous.size());
  }

  /** ZooBank joins through Wikidata's P2006 and wins the years of a person IPNI does not know */
  @Test
  public void zoobankRecord() {
    var w = wd("Q7");
    w.zoobank = "ABC";
    w.label("Richard Pyle");
    w.born(1960);
    var z = zb("ABC");
    z.name("Pyle", PersonNameKind.CITATION, PersonFormCode.ZOO);
    z.born(1967);
    z.family("Pyle");
    var r = merge(PersonFiles.Content.empty(), w.build(), z.build());
    Person p = r.content().persons().get(0);
    assertEquals("wd:Q7", p.id());
    assertEquals(Integer.valueOf(1967), p.born());
    assertEquals(1, r.report().conflicts.size());
  }

  /**
   * Years from two sources, or one source's own error (Wikidata has persons dying before they were born), must not
   * make an impossible person: the filled years are left out and reported, unknown beats wrong.
   */
  @Test
  public void bornAfterDiedIsLeftOut() {
    var w = wd("Q1");
    w.label("Joseph Donat Surian");
    w.born(1700);
    w.died(1691);
    var r = merge(PersonFiles.Content.empty(), w.build());
    Person p = r.content().persons().get(0);
    assertNull(p.born());
    assertNull(p.died());
    assertTrue(String.join("\n", r.report().conflicts), r.report().conflicts.stream().anyMatch(c -> c.contains("born 1700 after died 1691")));

    // a curated line keeps its years, the source's are only reported
    var existing = new PersonFiles.Content(
      List.of(new Person("wd:Q2", "Q2", null, null, List.of(), null, null, null, null, 1801, null, null, Set.of(), PersonSource.CURATED)),
      List.of(new PersonName("wd:Q2", "A. B. Koelpin", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.CURATED)),
      List.of());
    var k = wd("Q2");
    k.born(1805);
    r = merge(existing, k.build());
    p = r.content().persons().get(0);
    assertNull(p.born());
    assertEquals(Integer.valueOf(1801), p.died());
  }

  /** a person active before being born is as impossible as one dying before */
  @Test
  public void activeBeforeBornIsLeftOut() {
    var w = wd("Q1");
    w.ipni = "7-1";
    w.label("Ann Doe");
    w.born(1900);
    var i = ipni("7-1");
    i.name("Doe", PersonNameKind.STANDARD, PersonFormCode.BOT);
    i.activeFrom(1844);
    var r = merge(PersonFiles.Content.empty(), w.build(), i.build());
    Person p = r.content().persons().get(0);
    assertNull(p.born());
    assertNull(p.activeFrom());
  }

  /** an authority is always right about its own id: Wikidata moving its IPNI link does not move the item */
  @Test
  public void ownIdWins() {
    var existing = new PersonFiles.Content(
      List.of(new Person("wd:Q5", "Q5", "1-1", null, List.of(), null, null, null, null, null, null, null, Set.of(), PersonSource.WIKIDATA),
        new Person("wd:Q6", "Q6", "2-2", null, List.of(), null, null, null, null, null, null, null, Set.of(), PersonSource.WIKIDATA)),
      List.of(new PersonName("wd:Q5", "A. Doe", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.WIKIDATA),
        new PersonName("wd:Q6", "B. Doe", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.WIKIDATA)),
      List.of());
    var w = wd("Q5");
    w.ipni = "2-2";
    w.label("A. Doe");
    var r = merge(existing, w.build());
    assertEquals(2, r.content().persons().size());
    assertEquals("1-1", person(r.content(), "wd:Q5").ipni());
    assertEquals("2-2", person(r.content(), "wd:Q6").ipni());
    assertEquals(List.of("wd:Q6"), r.report().retired);
    assertFalse(r.report().ambiguous.isEmpty());
  }

  /** Wikidata merging a duplicate item redirects one person of the files onto another: they become one */
  @Test
  public void redirectOntoAnotherPerson() {
    var existing = new PersonFiles.Content(
      List.of(new Person("wd:Q1", "Q1", null, null, List.of(), null, null, null, 1800, null, null, null, Set.of(), PersonSource.WIKIDATA),
        new Person("wd:Q2", "Q2", null, null, List.of(), null, null, null, null, 1870, null, null, Set.of(), PersonSource.WIKIDATA)),
      List.of(new PersonName("wd:Q1", "A. Doe", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.WIKIDATA),
        new PersonName("wd:Q2", "Ann Doe", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.WIKIDATA)),
      List.of());
    var w = wd("Q2");
    w.label("Ann Doe");
    var r = new PersonMerger(DAY).merge(existing, List.of(w.build()), Map.of("Q1", "Q2"));
    assertEquals(List.of(), new MemoryPersonStore(r.content()).problems());
    assertEquals(1, r.content().persons().size());
    Person p = r.content().persons().get(0);
    assertEquals("wd:Q2", p.id());
    assertEquals(List.of("wd:Q1"), p.formerIds());
    // the old id finds the person it was joined into
    assertSame(p, new MemoryPersonStore(r.content()).get("wd:Q1"));
    // the values follow the item that stays, and it gives none
    assertNull(p.born());
    assertNull(p.died());
    assertEquals(1, r.report().joined.size());
    assertTrue(r.report().joined.get(0), r.report().joined.get(0).startsWith("wd:Q1 into wd:Q2"));
  }

  /** a curated person keeps its line and its values, even when a source links it to another person */
  @Test
  public void curatedIsNeverAbsorbed() {
    var existing = new PersonFiles.Content(
      List.of(new Person("zb:Z", null, null, "Z", List.of(), null, null, null, 1850, null, null, null, Set.of(), PersonSource.CURATED),
        new Person("wd:Q1", "Q1", null, null, List.of(), null, null, null, 1805, null, null, null, Set.of(), PersonSource.WIKIDATA)),
      List.of(new PersonName("zb:Z", "Zed", PersonNameKind.CITATION, PersonFormCode.ZOO, PersonSource.CURATED),
        new PersonName("wd:Q1", "Zed Doe", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.WIKIDATA)),
      List.of());
    var w = wd("Q1");
    w.zoobank = "Z";
    w.label("Zed Doe");
    var r = merge(existing, w.build());
    assertEquals(2, r.content().persons().size());
    Person z = person(r.content(), "zb:Z");
    assertEquals(Integer.valueOf(1850), z.born());
    assertEquals(PersonSource.CURATED, z.source());
    assertFalse(r.report().ambiguous.isEmpty());
  }

  /** two persons of the files joined by a new link keep one value per cell, and the other one is reported */
  @Test
  public void joiningReportsWhatItDrops() {
    var existing = new PersonFiles.Content(
      List.of(new Person("ipni:9-1", null, "9-1", null, List.of(), "Bojko", null, null, null, null, null, null, Set.of(), PersonSource.IPNI),
        new Person("wd:Q1", "Q1", null, null, List.of(), "Boyko Bojko", null, null, null, null, null, null, Set.of(), PersonSource.WIKIDATA)),
      List.of(new PersonName("ipni:9-1", "Bojko", PersonNameKind.STANDARD, PersonFormCode.BOT, PersonSource.IPNI),
        new PersonName("wd:Q1", "Hugo Boyko Bojko", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.WIKIDATA)),
      List.of());
    var w = wd("Q1");
    w.ipni = "9-1";
    w.label("Hugo Boyko Bojko");
    var r = merge(existing, w.build());
    assertEquals(1, r.content().persons().size());
    assertEquals(List.of("ipni:9-1"), r.content().persons().get(0).formerIds());
    assertTrue(String.join("\n", r.report().conflicts), r.report().conflicts.stream().anyMatch(c -> c.contains("family") && c.contains("Bojko")));
  }

  @Test
  public void redirectedItem() {
    var existing = new PersonFiles.Content(
      List.of(new Person("wd:Q1", "Q1", null, null, List.of(), null, null, null, null, null, null, null, Set.of(), PersonSource.WIKIDATA)),
      List.of(new PersonName("wd:Q1", "A. Doe", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.WIKIDATA)),
      List.of());
    var w = wd("Q2");
    w.label("Ann Doe");
    var r = new PersonMerger(DAY).merge(existing, List.of(w.build()), Map.of("Q1", "Q2"));
    assertEquals(List.of(), new MemoryPersonStore(r.content()).problems());
    assertEquals(1, r.content().persons().size());
    Person p = r.content().persons().get(0);
    assertEquals("wd:Q2", p.id());
    assertEquals(List.of("wd:Q1"), p.formerIds());
    assertEquals(1, r.report().redirected);
  }

  @Test
  public void relations() {
    var father = wd("Q1");
    father.label("G. B. Sowerby I");
    var son = wd("Q2");
    son.label("G. B. Sowerby II");
    son.link(PersonRelationType.PARENT, "wd:Q1");
    son.link(PersonRelationType.SIBLING, "wd:Q404");
    var brother = wd("Q3");
    brother.label("Henry Sowerby");
    brother.link(PersonRelationType.SIBLING, "wd:Q2");
    son.link(PersonRelationType.SIBLING, "wd:Q3");
    var r = merge(PersonFiles.Content.empty(), father.build(), son.build(), brother.build());
    assertEquals(List.of(new PersonRelation("wd:Q2", PersonRelationType.PARENT, "wd:Q1", PersonSource.WIKIDATA),
      new PersonRelation("wd:Q2", PersonRelationType.SIBLING, "wd:Q3", PersonSource.WIKIDATA)), r.content().relations());
    assertEquals(1, r.report().relationsDropped);
  }

  /** every person needs a name form, a Wikidata item with an id only is not written */
  @Test
  public void withoutName() {
    var w = wd("Q1");
    w.zoobank = "X";
    var r = merge(PersonFiles.Content.empty(), w.build());
    assertEquals(List.of(), r.content().persons());
    assertEquals(1, r.report().withoutName);
  }

  /** a second IPNI id of an item is a former id of its person, and the IPNI author of that id joins it */
  @Test
  public void secondIpniIdOfAnItem() {
    var w = wd("Q1");
    w.ipni = "1-1";
    w.otherId(Person.IPNI + "2-2");
    w.label("Anna Smith");
    var i1 = ipni("1-1");
    i1.name("A.Sm.", PersonNameKind.STANDARD, PersonFormCode.BOT);
    var i2 = ipni("2-2");
    i2.name("A.Smith", PersonNameKind.STANDARD, PersonFormCode.BOT);
    var r = merge(PersonFiles.Content.empty(), w.build(), i1.build(), i2.build());
    assertEquals(1, r.content().persons().size());
    Person p = r.content().persons().get(0);
    assertEquals("wd:Q1", p.id());
    assertEquals("1-1", p.ipni());
    assertEquals(List.of("ipni:2-2"), p.formerIds());
    assertTrue(r.content().names().contains(new PersonName("wd:Q1", "A.Smith", PersonNameKind.STANDARD, PersonFormCode.BOT, PersonSource.IPNI)));
    assertEquals(List.of(), r.report().ambiguous);
    assertEquals(List.of(), r.report().conflicts);
  }

  /** the files hold the second IPNI id as a person of its own, as the first harvest wrote 160 of them: the item joins it */
  @Test
  public void secondIpniIdJoinsAPersonOfTheFiles() {
    var existing = new PersonFiles.Content(
      List.of(new Person("ipni:2-2", null, "2-2", null, List.of(), "Smith", "Anna", null, null, null, null, null, Set.of(), PersonSource.IPNI)),
      List.of(new PersonName("ipni:2-2", "A.Smith", PersonNameKind.STANDARD, PersonFormCode.BOT, PersonSource.IPNI)),
      List.of());
    var w = wd("Q1");
    w.ipni = "1-1";
    w.otherId(Person.IPNI + "2-2");
    w.label("Anna Smith");
    w.family("Smith");
    var r = merge(existing, w.build());
    assertEquals(1, r.content().persons().size());
    Person p = r.content().persons().get(0);
    assertEquals("wd:Q1", p.id());
    assertEquals(List.of("ipni:2-2"), p.formerIds());
    assertEquals("Smith", p.family());
    assertEquals(List.of(), r.report().conflicts);
  }

  /**
   * A second IPNI id became a former id; Wikidata then stops listing it while IPNI still serves its record. The record
   * joins the person holding it as a former id instead of making a second person with that id, which would fail the
   * consistency check of every harvest to come.
   */
  @Test
  public void aFormerIdStillJoins() {
    var w = wd("Q1");
    w.ipni = "1-1";
    w.otherId(Person.IPNI + "2-2");
    w.label("Anna Smith");
    var i1 = ipni("1-1");
    i1.name("A.Sm.", PersonNameKind.STANDARD, PersonFormCode.BOT);
    var i2 = ipni("2-2");
    i2.name("A.Smith", PersonNameKind.STANDARD, PersonFormCode.BOT);
    var first = merge(PersonFiles.Content.empty(), w.build(), i1.build(), i2.build());
    assertEquals(List.of("ipni:2-2"), first.content().persons().get(0).formerIds());

    var w2 = wd("Q1");
    w2.ipni = "1-1";
    w2.label("Anna Smith");
    var r = merge(first.content(), w2.build(), i1.build(), i2.build());
    assertEquals(1, r.content().persons().size());
    Person p = r.content().persons().get(0);
    assertEquals("wd:Q1", p.id());
    assertEquals(List.of("ipni:2-2"), p.formerIds());
    assertTrue(r.content().names().contains(new PersonName("wd:Q1", "A.Smith", PersonNameKind.STANDARD, PersonFormCode.BOT, PersonSource.IPNI)));
  }

  /** a second id another item holds as its own stays with that item: reported, never a former id of two persons */
  @Test
  public void secondIdOfAnotherItemIsReported() {
    var a = wd("Q1");
    a.ipni = "2-2";
    a.label("Anna Smith");
    var b = wd("Q2");
    b.ipni = "1-1";
    b.otherId(Person.IPNI + "2-2");
    b.label("Anne Smith");
    var r = merge(PersonFiles.Content.empty(), a.build(), b.build());
    assertEquals(2, r.content().persons().size());
    assertEquals(List.of(), person(r.content(), "wd:Q2").formerIds());
    assertEquals(1, r.report().ambiguous.size());
  }

  /** a curated person keeps its line as it is, empty cells included; what the sources say otherwise is reported */
  @Test
  public void curatedLineWins() {
    var existing = new PersonFiles.Content(
      List.of(new Person("wd:Q1", "Q1", null, null, List.of(), "Linnaeus", null, null, 1707, null, null, null, Set.of(),
        PersonSource.CURATED)),
      List.of(new PersonName("wd:Q1", "L.", PersonNameKind.STANDARD, PersonFormCode.BOT, PersonSource.CURATED)),
      List.of());
    var w = wd("Q1");
    w.label("Carl Linnaeus");
    w.born(1708);
    w.died(1778);
    var r = merge(existing, w.build());
    Person p = r.content().persons().get(0);
    assertEquals(Integer.valueOf(1707), p.born());
    assertNull(p.died());
    assertEquals(PersonSource.CURATED, p.source());
    assertEquals(2, r.content().names().size());
    String kept = String.join("\n", r.report().curatedKept);
    assertTrue(kept, r.report().curatedKept.contains("wd:Q1 born: curated 1707, sources 1708"));
    assertTrue(kept, r.report().curatedKept.contains("wd:Q1 died: curated null, sources 1778"));
  }

  /** an IPNI birth year changing upstream changes the registry, and the report says from what to what */
  @Test
  public void followsTheSource() {
    var existing = new PersonFiles.Content(
      List.of(new Person("ipni:1-1", null, "1-1", null, List.of(), "Doe", null, null, 1800, null, null, null, Set.of(),
        PersonSource.IPNI)),
      List.of(new PersonName("ipni:1-1", "Doe", PersonNameKind.STANDARD, PersonFormCode.BOT, PersonSource.IPNI)),
      List.of());
    var i = ipni("1-1");
    i.family("Doe");
    i.name("Doe", PersonNameKind.STANDARD, PersonFormCode.BOT);
    i.born(1801);
    var r = merge(existing, i.build());
    assertEquals(Integer.valueOf(1801), r.content().persons().get(0).born());
    assertEquals(List.of("ipni:1-1 born: 1800 -> 1801"), r.report().changed);
  }

  /** a person no source has any more keeps its row and ids, retired, and loses its harvested forms */
  @Test
  public void notSeenIsRetired() {
    var existing = new PersonFiles.Content(
      List.of(new Person("wd:Q1", "Q1", null, null, List.of(), null, null, null, null, null, null, null, Set.of(),
        PersonSource.WIKIDATA)),
      List.of(new PersonName("wd:Q1", "A. Doe", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.WIKIDATA)),
      List.of());
    var r = merge(existing);
    Person p = r.content().persons().get(0);
    assertEquals(DAY, p.retired());
    assertEquals(List.of("wd:Q1"), r.report().retired);
    assertEquals(List.of(), r.content().names());
    assertEquals(List.of("wd:Q1 A. Doe FULL ANY"), r.report().formsRemoved);
  }

  /** a retired person a source names again is back */
  @Test
  public void retiredComesBack() {
    var existing = new PersonFiles.Content(
      List.of(new Person("wd:Q1", "Q1", null, null, List.of(), null, null, null, null, null, null, null, Set.of(),
        PersonSource.WIKIDATA, LocalDate.of(2026, 1, 1), null)),
      List.of(), List.of());
    var w = wd("Q1");
    w.label("A. Doe");
    var r = merge(existing, w.build());
    assertNull(r.content().persons().get(0).retired());
    assertEquals(List.of("wd:Q1"), r.report().unretired);
  }

  /** a source still listing a person but naming it no more retires it too: nobody can cite it */
  @Test
  public void noSourceNamesIt() {
    var existing = new PersonFiles.Content(
      List.of(new Person("wd:Q1", "Q1", null, null, List.of(), null, null, null, null, null, null, null, Set.of(),
        PersonSource.WIKIDATA)),
      List.of(new PersonName("wd:Q1", "A. Doe", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.WIKIDATA)),
      List.of());
    var r = merge(existing, wd("Q1").build());
    assertEquals(DAY, r.content().persons().get(0).retired());
    assertEquals(List.of("wd:Q1: no source names it"), r.report().retired);
  }

  /** a form a source no longer gives goes, a curated one stays */
  @Test
  public void formDropped() {
    var existing = new PersonFiles.Content(
      List.of(new Person("wd:Q1", "Q1", null, null, List.of(), null, null, null, null, null, null, null, Set.of(),
        PersonSource.WIKIDATA)),
      List.of(new PersonName("wd:Q1", "Ann Doe", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.WIKIDATA),
        new PersonName("wd:Q1", "Nan Doe", PersonNameKind.VARIANT, PersonFormCode.ANY, PersonSource.WIKIDATA),
        new PersonName("wd:Q1", "A. D.", PersonNameKind.VARIANT, PersonFormCode.ANY, PersonSource.CURATED)),
      List.of());
    var w = wd("Q1");
    w.label("Ann Doe");
    var r = merge(existing, w.build());
    assertEquals(Set.of("Ann Doe", "A. D."), r.content().names().stream().map(PersonName::form).collect(Collectors.toSet()));
    assertEquals(List.of("wd:Q1 Nan Doe VARIANT ANY"), r.report().formsRemoved);
    assertEquals(List.of(), r.report().formsAdded);
  }

  /** a sibling pair is one relation whichever of the two persons lists it */
  @Test
  public void siblingDirectionIsNoChange() {
    var existing = new PersonFiles.Content(
      List.of(new Person("wd:Q1", "Q1", null, null, List.of(), null, null, null, null, null, null, null, Set.of(),
          PersonSource.WIKIDATA),
        new Person("wd:Q2", "Q2", null, null, List.of(), null, null, null, null, null, null, null, Set.of(),
          PersonSource.WIKIDATA)),
      List.of(new PersonName("wd:Q1", "A. Doe", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.WIKIDATA),
        new PersonName("wd:Q2", "B. Doe", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.WIKIDATA)),
      List.of(new PersonRelation("wd:Q1", PersonRelationType.SIBLING, "wd:Q2", PersonSource.WIKIDATA)));
    var a = wd("Q1");
    a.label("A. Doe");
    var b = wd("Q2");
    b.label("B. Doe");
    b.link(PersonRelationType.SIBLING, "wd:Q1");
    var r = merge(existing, a.build(), b.build());
    assertEquals(List.of(new PersonRelation("wd:Q2", PersonRelationType.SIBLING, "wd:Q1", PersonSource.WIKIDATA)),
      r.content().relations());
    assertEquals(List.of(), r.report().relationsAdded);
    assertEquals(List.of(), r.report().relationsRemoved);
  }

  /** a relation a source no longer gives goes, a curated one stays */
  @Test
  public void relationDropped() {
    var existing = new PersonFiles.Content(
      List.of(new Person("wd:Q1", "Q1", null, null, List.of(), null, null, null, null, null, null, null, Set.of(),
          PersonSource.WIKIDATA),
        new Person("wd:Q2", "Q2", null, null, List.of(), null, null, null, null, null, null, null, Set.of(),
          PersonSource.WIKIDATA)),
      List.of(new PersonName("wd:Q1", "A. Doe", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.WIKIDATA),
        new PersonName("wd:Q2", "B. Doe", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.WIKIDATA)),
      List.of(new PersonRelation("wd:Q2", PersonRelationType.PARENT, "wd:Q1", PersonSource.WIKIDATA),
        new PersonRelation("wd:Q2", PersonRelationType.SIBLING, "wd:Q1", PersonSource.CURATED)));
    var a = wd("Q1");
    a.label("A. Doe");
    var b = wd("Q2");
    b.label("B. Doe");
    var r = merge(existing, a.build(), b.build());
    assertEquals(List.of(new PersonRelation("wd:Q2", PersonRelationType.SIBLING, "wd:Q1", PersonSource.CURATED)),
      r.content().relations());
    assertEquals(List.of("wd:Q2 PARENT wd:Q1"), r.report().relationsRemoved);
  }
}
