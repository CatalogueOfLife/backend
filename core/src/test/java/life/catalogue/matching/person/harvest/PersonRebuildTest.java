package life.catalogue.matching.person.harvest;

import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonName;
import life.catalogue.api.vocab.PersonFormCode;
import life.catalogue.api.vocab.PersonNameKind;
import life.catalogue.api.vocab.PersonSource;
import life.catalogue.matching.person.MemoryPersonStore;
import life.catalogue.matching.person.PersonFiles;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import static org.junit.Assert.*;

public class PersonRebuildTest {
  static final LocalDate DAY = LocalDate.of(2026, 9, 24);

  static HarvestSource source(String name, PersonRecord... records) {
    return new HarvestSource() {
      public String name() {
        return name;
      }

      public List<PersonRecord> read() {
        return List.of(records);
      }
    };
  }

  @Test
  public void rebuild() throws Exception {
    var w = new PersonRecord.Builder(PersonSource.WIKIDATA);
    w.wikidata = "Q1";
    w.label("Carl Linnaeus");
    w.family("Linnaeus");
    w.given("Carl");
    w.ipni = "12653-1";
    var i = new PersonRecord.Builder(PersonSource.IPNI);
    i.ipni = "12653-1";
    i.name("L.", PersonNameKind.STANDARD, PersonFormCode.BOT);

    var harvest = PersonRebuild.fetch(List.of(source("wikidata", w.build()), source("ipni", i.build())));
    var outcome = PersonRebuild.rebuild(PersonFiles.Content.empty(), harvest, Map.of(), DAY, null);
    assertEquals(List.of(), outcome.problems());
    assertEquals(1, outcome.content().persons().size());
    String report = outcome.report();
    assertTrue(report, report.contains("added 1"));
    assertTrue(report, report.contains("## Author map rows no person resolves"));
    // the author map row "C Linnaeus BOT ... L. ..." resolves through the IPNI standard form
    assertFalse(report, report.contains("  C Linnaeus\tBOT"));
  }

  /** the Wikidata items of the registry no record carries any more are the ones to ask for a redirect */
  @Test
  public void gone() throws Exception {
    var existing = new PersonFiles.Content(List.of(
      new Person("wd:Q1", "Q1", null, null, List.of(), null, null, null, null, null, null, null, Set.of(), PersonSource.WIKIDATA),
      new Person("wd:Q2", "Q2", null, null, List.of(), null, null, null, null, null, null, null, Set.of(), PersonSource.WIKIDATA),
      new Person("ipni:3-1", null, "3-1", null, List.of(), null, null, null, null, null, null, null, Set.of(), PersonSource.IPNI)),
      List.of(), List.of());
    var w = new PersonRecord.Builder(PersonSource.WIKIDATA);
    w.wikidata = "Q2";
    assertEquals(List.of("Q1"), PersonRebuild.gone(existing, PersonRebuild.fetch(List.of(source("wikidata", w.build())))));
  }

  /** an inconsistent registry lists its problems at the top of the report */
  @Test
  public void inconsistent() throws Exception {
    var existing = new PersonFiles.Content(
      List.of(new Person("clb:1", "Q9", null, null, List.of(), "Doe", null, null, null, null, null, null, Set.of(), PersonSource.CURATED)),
      List.of(new PersonName("clb:1", "Ann Doe", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.CURATED)),
      List.of());
    var outcome = PersonRebuild.rebuild(existing, PersonRebuild.fetch(List.of()), Map.of(), DAY, null);
    assertEquals(List.of("clb:1 is local but has authority ids"), outcome.problems());
    assertTrue(outcome.report(), outcome.report().contains("## Inconsistent, nothing written: 1"));
  }

  /**
   * A source that answers far too little would retire persons by the thousand: above the limit that is a problem, so
   * nothing is written unless the harvest is forced, without a limit.
   */
  @Test
  public void tooManyRetired() throws Exception {
    var existing = new PersonFiles.Content(List.of(
      new Person("wd:Q1", "Q1", null, null, List.of(), null, null, null, null, null, null, null, Set.of(), PersonSource.WIKIDATA),
      new Person("wd:Q2", "Q2", null, null, List.of(), null, null, null, null, null, null, null, Set.of(), PersonSource.WIKIDATA)),
      List.of(new PersonName("wd:Q1", "A. Doe", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.WIKIDATA),
        new PersonName("wd:Q2", "B. Doe", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.WIKIDATA)),
      List.of());
    var nothing = PersonRebuild.fetch(List.of());
    var limited = PersonRebuild.rebuild(existing, nothing, Map.of(), DAY, 1);
    assertEquals(List.of("2 persons would be retired, more than the limit of 1: a source may have answered too little"),
      limited.problems());
    assertTrue(limited.report(), limited.report().contains("## Inconsistent, nothing written: 1"));
    assertEquals(List.of(), PersonRebuild.rebuild(existing, nothing, Map.of(), DAY, 2).problems());
    assertEquals(List.of(), PersonRebuild.rebuild(existing, nothing, Map.of(), DAY, null).problems());
  }

  /** an author map row with a code nobody knows is reported and skipped, it does not fail the harvest */
  @Test
  public void malformedAuthorMapRow() {
    var store = new MemoryPersonStore(PersonFiles.Content.empty());
    StringBuilder sb = new StringBuilder();
    PersonRebuild.unresolvedAuthorMapRows(store, List.of(new String[]{"Linnaeus", "XYZ", "L."}, new String[]{"Nobody", "BOT", "Nob."}),
      sb);
    String report = sb.toString();
    assertTrue(report, report.contains("## Malformed author map rows: 1"));
    assertTrue(report, report.contains("  Linnaeus\tXYZ\tL."));
    assertTrue(report, report.contains("## Author map rows no person resolves: 1 of 1"));
  }

  /** without a reader for its format a dump must stop the harvest before anything is merged */
  @Test
  public void zooBankDumpNotReadYet() {
    var e = assertThrows(UnsupportedOperationException.class,
      () -> PersonRebuild.fetch(List.of(new ZooBankDumpSource(Path.of("dump.csv")))));
    assertTrue(e.getMessage(), e.getMessage().contains("ZooBank"));
  }
}
