package life.catalogue.matching.person;

import life.catalogue.api.vocab.TaxGroup;

import org.gbif.nameparser.api.NomCode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class PersonFilesTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  static Person sowerby2() {
    return new Person("wd:Q2", "Q2", "9936-1", null, List.of("ipni:9936-1"), "Sowerby", "George Brettingham", "II",
      1812, 1884, null, null, Set.of(TaxGroup.Molluscs, TaxGroup.Angiosperms), Provenance.WIKIDATA);
  }

  @Test
  public void roundTrip() throws Exception {
    var c = new PersonFiles.Content(
      List.of(sowerby2(), new Person("clb:1", null, null, null, List.of(), "Smith", null, null, null, null, 1850, 1870,
        Set.of(), Provenance.CURATED)),
      List.of(new PersonName("wd:Q2", "G.B.Sowerby II", NameKind.CITATION, FormCode.ZOO, Provenance.WIKIDATA),
        new PersonName("clb:1", "Smith", NameKind.FULL, FormCode.ANY, Provenance.CURATED)),
      List.of(new PersonRelation("wd:Q2", RelationType.PARENT, "wd:Q1", Provenance.WIKIDATA))
    );
    Path dir = tmp.newFolder().toPath();
    PersonFiles.write(dir, c);
    var read = PersonFiles.read(dir);
    // written sorted by id
    assertEquals(List.of("clb:1", "wd:Q2"), read.persons().stream().map(Person::id).toList());
    assertEquals(sowerby2(), read.persons().get(1));
    assertEquals(c.names().get(0), read.names().get(1));
    assertEquals(c.relations(), read.relations());
    assertTrue(Files.readString(dir.resolve("persons.tsv")).contains("\tAngiosperms|Molluscs\twikidata\n"));
  }

  /** Wikidata labels hold control characters now and then, a row must survive them */
  @Test
  public void tabsAndNewlinesBecomeSpaces() throws Exception {
    var c = new PersonFiles.Content(List.of(sowerby2()),
      List.of(new PersonName("wd:Q2", "George\tBrettingham\nSowerby", NameKind.FULL, FormCode.ANY, Provenance.WIKIDATA)),
      List.of());
    Path dir = tmp.newFolder().toPath();
    PersonFiles.write(dir, c);
    assertEquals("George Brettingham Sowerby", PersonFiles.read(dir).names().get(0).form());
  }

  @Test
  public void wrongHeaderFails() throws Exception {
    Path dir = tmp.newFolder().toPath();
    PersonFiles.write(dir, PersonFiles.Content.empty());
    Files.writeString(dir.resolve("names.tsv"), "person\tform\n", StandardCharsets.UTF_8);
    var e = assertThrows(IllegalArgumentException.class, () -> PersonFiles.read(dir));
    assertTrue(e.getMessage(), e.getMessage().contains("names.tsv"));
  }

  @Test
  public void committedFilesRead() throws Exception {
    assertNotNull(PersonFiles.readResources());
  }

  @Test
  public void ids() {
    assertEquals("wd:Q2", Person.idFor("Q2", "9936-1", null));
    assertEquals("ipni:9936-1", Person.idFor(null, "9936-1", "ABC"));
    assertEquals("zb:ABC", Person.idFor(null, null, "ABC"));
    assertNull(Person.idFor(null, null, null));
    assertEquals(Set.of("wd:Q2", "ipni:9936-1"), sowerby2().allIds());
  }

  @Test
  public void formCodes() {
    assertTrue(FormCode.BOT.appliesTo(null));
    assertTrue(FormCode.BOT.appliesTo(NomCode.BOTANICAL));
    assertFalse(FormCode.BOT.appliesTo(NomCode.ZOOLOGICAL));
    assertTrue(FormCode.ZOO.appliesTo(NomCode.ZOOLOGICAL));
    assertFalse(FormCode.ZOO.appliesTo(null));
    assertTrue(FormCode.ANY.appliesTo(NomCode.ZOOLOGICAL));
  }
}
