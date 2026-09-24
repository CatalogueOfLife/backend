package life.catalogue.matching.person.harvest;

import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.matching.person.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import static org.junit.Assert.*;

public class PersonMergerTest {

  static PersonRecord.Builder wd(String q) {
    var b = new PersonRecord.Builder(Provenance.WIKIDATA);
    b.wikidata = q;
    return b;
  }

  static PersonRecord.Builder ipni(String id) {
    var b = new PersonRecord.Builder(Provenance.IPNI);
    b.ipni = id;
    return b;
  }

  static PersonRecord.Builder zb(String id) {
    var b = new PersonRecord.Builder(Provenance.ZOOBANK);
    b.zoobank = id;
    return b;
  }

  static PersonMerger.Result merge(PersonFiles.Content existing, PersonRecord... records) {
    var r = new PersonMerger().merge(existing, List.of(records), Map.of());
    assertEquals(List.of(), new PersonRegistry(r.content()).problems());
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
    w.name("Hook.f.", NameKind.STANDARD, FormCode.BOT);
    var i = ipni("4084-1");
    i.family("Hooker");
    i.given("Joseph Dalton");
    i.suffix = "f.";
    i.born(1817);
    i.died(1911);
    i.name("Hook.f.", NameKind.STANDARD, FormCode.BOT);
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
    assertEquals(Provenance.IPNI, p.source());
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
    i.name("A.B.", NameKind.STANDARD, FormCode.BOT);
    i.born(1750);
    var r = merge(PersonFiles.Content.empty(), w.build(), i.build());
    assertEquals(Integer.valueOf(1750), r.content().persons().get(0).born());
    assertEquals(1, r.report().conflicts.size());
    assertTrue(r.report().conflicts.get(0), r.report().conflicts.get(0).contains("born"));
  }

  /** a value in the files is never overwritten, a curated line never removed */
  @Test
  public void fillsOnly() {
    var existing = new PersonFiles.Content(
      List.of(new Person("wd:Q1", "Q1", null, null, List.of(), "Linnaeus", null, null, 1707, null, null, null, Set.of(), Provenance.CURATED)),
      List.of(new PersonName("wd:Q1", "L.", NameKind.STANDARD, FormCode.BOT, Provenance.CURATED)),
      List.of());
    var w = wd("Q1");
    w.label("Carl Linnaeus");
    w.born(1708);
    w.died(1778);
    var r = merge(existing, w.build());
    Person p = r.content().persons().get(0);
    assertEquals(Integer.valueOf(1707), p.born());
    assertEquals(Integer.valueOf(1778), p.died());
    assertEquals(Provenance.CURATED, p.source());
    assertEquals(2, r.content().names().size());
  }

  /** an IPNI person that Wikidata links later moves to its Q-id and keeps the IPNI one as a former id */
  @Test
  public void gainsABetterId() {
    var existing = new PersonFiles.Content(
      List.of(new Person("ipni:9-1", null, "9-1", null, List.of(), "Sowerby", "James", null, null, null, null, null, Set.of(), Provenance.IPNI)),
      List.of(new PersonName("ipni:9-1", "Sowerby", NameKind.STANDARD, FormCode.BOT, Provenance.IPNI),
        new PersonName("ipni:9-1", "J. Sow.", NameKind.VARIANT, FormCode.BOT, Provenance.CURATED)),
      List.of());
    var w = wd("Q5");
    w.ipni = "9-1";
    w.label("James Sowerby");
    var r = merge(existing, w.build());
    Person p = r.content().persons().get(0);
    assertEquals("wd:Q5", p.id());
    assertEquals(List.of("ipni:9-1"), p.formerIds());
    // the harvested line moves, the curated one stays as written and still resolves
    assertTrue(r.content().names().contains(new PersonName("wd:Q5", "Sowerby", NameKind.STANDARD, FormCode.BOT, Provenance.IPNI)));
    assertTrue(r.content().names().contains(new PersonName("ipni:9-1", "J. Sow.", NameKind.VARIANT, FormCode.BOT, Provenance.CURATED)));
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
    i.name("A.Sm.", NameKind.STANDARD, FormCode.BOT);
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
    z.name("Pyle", NameKind.CITATION, FormCode.ZOO);
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

    // a year already in the files stays, only the filled one goes
    var existing = new PersonFiles.Content(
      List.of(new Person("wd:Q2", "Q2", null, null, List.of(), null, null, null, null, 1801, null, null, Set.of(), Provenance.CURATED)),
      List.of(new PersonName("wd:Q2", "A. B. Koelpin", NameKind.FULL, FormCode.ANY, Provenance.CURATED)),
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
    i.name("Doe", NameKind.STANDARD, FormCode.BOT);
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
      List.of(new Person("wd:Q5", "Q5", "1-1", null, List.of(), null, null, null, null, null, null, null, Set.of(), Provenance.WIKIDATA),
        new Person("wd:Q6", "Q6", "2-2", null, List.of(), null, null, null, null, null, null, null, Set.of(), Provenance.WIKIDATA)),
      List.of(new PersonName("wd:Q5", "A. Doe", NameKind.FULL, FormCode.ANY, Provenance.WIKIDATA),
        new PersonName("wd:Q6", "B. Doe", NameKind.FULL, FormCode.ANY, Provenance.WIKIDATA)),
      List.of());
    var w = wd("Q5");
    w.ipni = "2-2";
    w.label("A. Doe");
    var r = merge(existing, w.build());
    assertEquals(2, r.content().persons().size());
    assertEquals("1-1", person(r.content(), "wd:Q5").ipni());
    assertEquals("2-2", person(r.content(), "wd:Q6").ipni());
    assertEquals(List.of("wd:Q6"), r.report().notSeen);
    assertFalse(r.report().ambiguous.isEmpty());
  }

  /** Wikidata merging a duplicate item redirects one person of the files onto another: they become one */
  @Test
  public void redirectOntoAnotherPerson() {
    var existing = new PersonFiles.Content(
      List.of(new Person("wd:Q1", "Q1", null, null, List.of(), null, null, null, 1800, null, null, null, Set.of(), Provenance.WIKIDATA),
        new Person("wd:Q2", "Q2", null, null, List.of(), null, null, null, null, 1870, null, null, Set.of(), Provenance.WIKIDATA)),
      List.of(new PersonName("wd:Q1", "A. Doe", NameKind.FULL, FormCode.ANY, Provenance.WIKIDATA),
        new PersonName("wd:Q2", "Ann Doe", NameKind.FULL, FormCode.ANY, Provenance.WIKIDATA)),
      List.of());
    var w = wd("Q2");
    w.label("Ann Doe");
    var r = new PersonMerger().merge(existing, List.of(w.build()), Map.of("Q1", "Q2"));
    assertEquals(List.of(), new PersonRegistry(r.content()).problems());
    assertEquals(1, r.content().persons().size());
    Person p = r.content().persons().get(0);
    assertEquals("wd:Q2", p.id());
    assertEquals(List.of("wd:Q1"), p.formerIds());
    assertEquals(Integer.valueOf(1800), p.born());
    assertEquals(Integer.valueOf(1870), p.died());
  }

  /** a curated person keeps its line and its values, even when a source links it to another person */
  @Test
  public void curatedIsNeverAbsorbed() {
    var existing = new PersonFiles.Content(
      List.of(new Person("zb:Z", null, null, "Z", List.of(), null, null, null, 1850, null, null, null, Set.of(), Provenance.CURATED),
        new Person("wd:Q1", "Q1", null, null, List.of(), null, null, null, 1805, null, null, null, Set.of(), Provenance.WIKIDATA)),
      List.of(new PersonName("zb:Z", "Zed", NameKind.CITATION, FormCode.ZOO, Provenance.CURATED),
        new PersonName("wd:Q1", "Zed Doe", NameKind.FULL, FormCode.ANY, Provenance.WIKIDATA)),
      List.of());
    var w = wd("Q1");
    w.zoobank = "Z";
    w.label("Zed Doe");
    var r = merge(existing, w.build());
    assertEquals(2, r.content().persons().size());
    Person z = person(r.content(), "zb:Z");
    assertEquals(Integer.valueOf(1850), z.born());
    assertEquals(Provenance.CURATED, z.source());
    assertFalse(r.report().ambiguous.isEmpty());
  }

  /** two persons of the files joined by a new link keep one value per cell, and the other one is reported */
  @Test
  public void joiningReportsWhatItDrops() {
    var existing = new PersonFiles.Content(
      List.of(new Person("ipni:9-1", null, "9-1", null, List.of(), "Bojko", null, null, null, null, null, null, Set.of(), Provenance.IPNI),
        new Person("wd:Q1", "Q1", null, null, List.of(), "Boyko Bojko", null, null, null, null, null, null, Set.of(), Provenance.WIKIDATA)),
      List.of(new PersonName("ipni:9-1", "Bojko", NameKind.STANDARD, FormCode.BOT, Provenance.IPNI),
        new PersonName("wd:Q1", "Hugo Boyko Bojko", NameKind.FULL, FormCode.ANY, Provenance.WIKIDATA)),
      List.of());
    var w = wd("Q1");
    w.ipni = "9-1";
    w.label("Hugo Boyko Bojko");
    var r = merge(existing, w.build());
    assertEquals(1, r.content().persons().size());
    assertEquals(List.of("ipni:9-1"), r.content().persons().get(0).formerIds());
    assertTrue(String.join("\n", r.report().conflicts), r.report().conflicts.stream().anyMatch(c -> c.contains("family") && c.contains("Bojko")));
  }

  /** after the first harvest the files hold a value, and only the report can show that a source now says otherwise */
  @Test
  public void filesAndSourceDisagree() {
    var existing = new PersonFiles.Content(
      List.of(new Person("wd:Q1", "Q1", null, null, List.of(), null, null, null, 1700, null, null, null, Set.of(), Provenance.WIKIDATA)),
      List.of(new PersonName("wd:Q1", "A. Doe", NameKind.FULL, FormCode.ANY, Provenance.WIKIDATA)),
      List.of());
    var w = wd("Q1");
    w.label("A. Doe");
    w.born(1750);
    var r = merge(existing, w.build());
    assertEquals(Integer.valueOf(1700), r.content().persons().get(0).born());
    assertEquals(1, r.report().changed.size());
    assertTrue(r.report().changed.get(0), r.report().changed.get(0).contains("born: files 1700, wikidata 1750"));
  }

  @Test
  public void redirectedItem() {
    var existing = new PersonFiles.Content(
      List.of(new Person("wd:Q1", "Q1", null, null, List.of(), null, null, null, null, null, null, null, Set.of(), Provenance.WIKIDATA)),
      List.of(new PersonName("wd:Q1", "A. Doe", NameKind.FULL, FormCode.ANY, Provenance.WIKIDATA)),
      List.of());
    var w = wd("Q2");
    w.label("Ann Doe");
    var r = new PersonMerger().merge(existing, List.of(w.build()), Map.of("Q1", "Q2"));
    assertEquals(List.of(), new PersonRegistry(r.content()).problems());
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
    son.link(RelationType.PARENT, "wd:Q1");
    son.link(RelationType.SIBLING, "wd:Q404");
    var brother = wd("Q3");
    brother.label("Henry Sowerby");
    brother.link(RelationType.SIBLING, "wd:Q2");
    son.link(RelationType.SIBLING, "wd:Q3");
    var r = merge(PersonFiles.Content.empty(), father.build(), son.build(), brother.build());
    assertEquals(List.of(new PersonRelation("wd:Q2", RelationType.PARENT, "wd:Q1", Provenance.WIKIDATA),
      new PersonRelation("wd:Q2", RelationType.SIBLING, "wd:Q3", Provenance.WIKIDATA)), r.content().relations());
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

  @Test
  public void notSeenIsKept() {
    var existing = new PersonFiles.Content(
      List.of(new Person("wd:Q1", "Q1", null, null, List.of(), null, null, null, null, null, null, null, Set.of(), Provenance.WIKIDATA)),
      List.of(new PersonName("wd:Q1", "A. Doe", NameKind.FULL, FormCode.ANY, Provenance.WIKIDATA)),
      List.of());
    var r = merge(existing);
    assertEquals(1, r.content().persons().size());
    assertEquals(List.of("wd:Q1"), r.report().notSeen);
  }

  /** a second IPNI id of an item is a former id of its person, and the IPNI author of that id joins it */
  @Test
  public void secondIpniIdOfAnItem() {
    var w = wd("Q1");
    w.ipni = "1-1";
    w.otherId(Person.IPNI + "2-2");
    w.label("Anna Smith");
    var i1 = ipni("1-1");
    i1.name("A.Sm.", NameKind.STANDARD, FormCode.BOT);
    var i2 = ipni("2-2");
    i2.name("A.Smith", NameKind.STANDARD, FormCode.BOT);
    var r = merge(PersonFiles.Content.empty(), w.build(), i1.build(), i2.build());
    assertEquals(1, r.content().persons().size());
    Person p = r.content().persons().get(0);
    assertEquals("wd:Q1", p.id());
    assertEquals("1-1", p.ipni());
    assertEquals(List.of("ipni:2-2"), p.formerIds());
    assertTrue(r.content().names().contains(new PersonName("wd:Q1", "A.Smith", NameKind.STANDARD, FormCode.BOT, Provenance.IPNI)));
    assertEquals(List.of(), r.report().ambiguous);
    assertEquals(List.of(), r.report().conflicts);
  }

  /** the files hold the second IPNI id as a person of its own, as the first harvest wrote 160 of them: the item joins it */
  @Test
  public void secondIpniIdJoinsAPersonOfTheFiles() {
    var existing = new PersonFiles.Content(
      List.of(new Person("ipni:2-2", null, "2-2", null, List.of(), "Smith", "Anna", null, null, null, null, null, Set.of(), Provenance.IPNI)),
      List.of(new PersonName("ipni:2-2", "A.Smith", NameKind.STANDARD, FormCode.BOT, Provenance.IPNI)),
      List.of());
    var w = wd("Q1");
    w.ipni = "1-1";
    w.otherId(Person.IPNI + "2-2");
    w.label("Anna Smith");
    var r = merge(existing, w.build());
    assertEquals(1, r.content().persons().size());
    Person p = r.content().persons().get(0);
    assertEquals("wd:Q1", p.id());
    assertEquals(List.of("ipni:2-2"), p.formerIds());
    assertEquals("Smith", p.family());
    assertEquals(List.of(), r.report().conflicts);
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
}
