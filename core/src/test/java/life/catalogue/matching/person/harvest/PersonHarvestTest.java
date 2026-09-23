package life.catalogue.matching.person.harvest;

import life.catalogue.matching.person.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class PersonHarvestTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  static PersonSource source(String name, PersonRecord... records) {
    return new PersonSource() {
      public String name() {
        return name;
      }

      public List<PersonRecord> read() {
        return List.of(records);
      }
    };
  }

  @Test
  public void run() throws Exception {
    Path dir = tmp.newFolder().toPath();
    PersonFiles.write(dir, PersonFiles.Content.empty());
    var w = new PersonRecord.Builder(Provenance.WIKIDATA);
    w.wikidata = "Q1";
    w.label("Carl Linnaeus");
    w.family("Linnaeus");
    w.given("Carl");
    var i = new PersonRecord.Builder(Provenance.IPNI);
    i.ipni = "12653-1";
    i.name("L.", NameKind.STANDARD, FormCode.BOT);
    w.ipni = "12653-1";

    String report = PersonHarvest.run(dir, List.of(source("wikidata", w.build()), source("ipni", i.build())), q -> Map.of());
    var c = PersonFiles.read(dir);
    assertEquals(1, c.persons().size());
    assertEquals(List.of(), new PersonRegistry(c).problems());
    assertTrue(report, report.contains("added 1"));
    // the author map rows no person resolves
    assertTrue(report, report.contains("## Author map rows no person resolves"));
    // the author map row "C Linnaeus BOT ... L. ..." resolves through the IPNI standard form
    assertFalse(report, report.contains("  C Linnaeus\tBOT"));
  }

  /** without a reader for its format a dump must stop the run before anything is written */
  @Test
  public void zooBankDumpNotReadYet() throws Exception {
    Path dir = tmp.newFolder().toPath();
    PersonFiles.write(dir, PersonFiles.Content.empty());
    var e = assertThrows(UnsupportedOperationException.class,
      () -> PersonHarvest.run(dir, List.of(new ZooBankDumpSource(dir.resolve("dump.csv"))), q -> Map.of()));
    assertTrue(e.getMessage(), e.getMessage().contains("ZooBank"));
    assertEquals(PersonFiles.Content.empty(), PersonFiles.read(dir));
  }
}
