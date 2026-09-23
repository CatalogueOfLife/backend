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
}
