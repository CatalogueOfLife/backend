package life.catalogue.matching.person;

import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonName;
import life.catalogue.api.model.PersonRelation;
import life.catalogue.api.vocab.PersonFormCode;
import life.catalogue.api.vocab.PersonNameKind;
import life.catalogue.api.vocab.PersonRelationType;
import life.catalogue.api.vocab.PersonSource;
import life.catalogue.api.vocab.TaxGroup;

import org.gbif.nameparser.api.NomCode;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class PersonFilesTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  static Person sowerby2() {
    return new Person("wd:Q2", "Q2", "9936-1", null, List.of("ipni:9936-1"), "Sowerby", "George Brettingham", "II",
      1812, 1884, null, null, Set.of(TaxGroup.Molluscs, TaxGroup.Angiosperms), PersonSource.WIKIDATA);
  }

  static PersonFiles.Content content() {
    return new PersonFiles.Content(
      List.of(sowerby2(), new Person("clb:1", null, null, null, List.of(), "Smith", null, null, null, null, 1850, 1870,
        Set.of(), PersonSource.CURATED)),
      List.of(new PersonName("wd:Q2", "G.B.Sowerby II", PersonNameKind.CITATION, PersonFormCode.ZOO, PersonSource.WIKIDATA),
        new PersonName("clb:1", "Smith", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.CURATED)),
      List.of(new PersonRelation("wd:Q2", PersonRelationType.PARENT, "wd:Q1", PersonSource.WIKIDATA))
    );
  }

  @Test
  public void roundTrip() throws Exception {
    var c = content();
    Path dir = tmp.newFolder().toPath();
    PersonFiles.write(dir, c);
    var read = PersonFiles.read(dir);
    // written sorted by id
    assertEquals(List.of("clb:1", "wd:Q2"), read.persons().stream().map(Person::id).toList());
    assertEquals(sowerby2(), read.persons().get(1));
    assertEquals(c.names().get(0), read.names().get(1));
    assertEquals(c.relations(), read.relations());
    assertTrue(Files.readString(dir.resolve("persons.tsv")).contains("\tAngiosperms|Molluscs\twikidata\t\t\n"));
  }

  /** Wikidata labels hold control characters now and then, a row must survive them */
  @Test
  public void tabsAndNewlinesBecomeSpaces() throws Exception {
    var c = new PersonFiles.Content(List.of(sowerby2()),
      List.of(new PersonName("wd:Q2", "George\tBrettingham\nSowerby", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.WIKIDATA)),
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
  public void ids() {
    assertEquals("wd:Q2", Person.idFor("Q2", "9936-1", null));
    assertEquals("ipni:9936-1", Person.idFor(null, "9936-1", "ABC"));
    assertEquals("zb:ABC", Person.idFor(null, null, "ABC"));
    assertNull(Person.idFor(null, null, null));
    assertEquals(Set.of("wd:Q2", "ipni:9936-1"), sowerby2().allIds());
  }

  @Test
  public void formCodes() {
    assertTrue(PersonFormCode.BOT.appliesTo(null));
    assertTrue(PersonFormCode.BOT.appliesTo(NomCode.BOTANICAL));
    assertFalse(PersonFormCode.BOT.appliesTo(NomCode.ZOOLOGICAL));
    assertTrue(PersonFormCode.ZOO.appliesTo(NomCode.ZOOLOGICAL));
    assertFalse(PersonFormCode.ZOO.appliesTo(null));
    assertTrue(PersonFormCode.ANY.appliesTo(NomCode.ZOOLOGICAL));
  }

  /** a retired person keeps its day and its successor through the files */
  @Test
  public void retiredRoundTrip() throws Exception {
    var retired = new Person("wd:Q9", "Q9", null, null, List.of(), "Doe", null, null, null, null, null, null, Set.of(),
      PersonSource.WIKIDATA, LocalDate.of(2026, 9, 24), "wd:Q2");
    Path dir = tmp.newFolder().toPath();
    PersonFiles.write(dir, new PersonFiles.Content(List.of(sowerby2(), retired), List.of(), List.of()));
    assertEquals(retired, PersonFiles.read(dir).persons().get(1));
  }

  /** the persons file of phase 3 has no retired and successor columns and still reads */
  @Test
  public void legacyPersonsHeader() throws Exception {
    Path dir = tmp.newFolder().toPath();
    PersonFiles.write(dir, PersonFiles.Content.empty());
    Files.writeString(dir.resolve("persons.tsv"), String.join("\t", PersonFiles.LEGACY_PERSON_COLUMNS) + "\n"
      + "wd:Q2\tQ2\t9936-1\t\tipni:9936-1\tSowerby\tGeorge Brettingham\tII\t1812\t1884\t\t\tAngiosperms|Molluscs\twikidata\n",
      StandardCharsets.UTF_8);
    assertEquals(List.of(sowerby2()), PersonFiles.read(dir).persons());
  }

  @Test
  public void zipRoundTrip() throws Exception {
    var out = new ByteArrayOutputStream();
    PersonFiles.writeZip(out, content());
    var read = PersonFiles.readZip(new ByteArrayInputStream(out.toByteArray()));
    assertEquals(Set.copyOf(content().persons()), Set.copyOf(read.persons()));
    assertEquals(Set.copyOf(content().names()), Set.copyOf(read.names()));
    assertEquals(content().relations(), read.relations());
  }

  /** a zip of the folder holding the files reads the same, other files are ignored, a missing one is named */
  @Test
  public void zipOfAFolderAndAMissingFile() throws Exception {
    var out = new ByteArrayOutputStream();
    PersonFiles.writeZip(out, content());
    Map<String, byte[]> files = new LinkedHashMap<>();
    try (var in = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
      ZipEntry e;
      while ((e = in.getNextEntry()) != null) {
        files.put(e.getName(), in.readAllBytes());
      }
    }
    files.put("README.txt", "hello".getBytes(StandardCharsets.UTF_8));
    var folder = PersonFiles.readZip(new ByteArrayInputStream(zip(files, "persons/")));
    assertEquals(Set.copyOf(content().persons()), Set.copyOf(folder.persons()));

    files.remove("relations.tsv");
    var e = assertThrows(IllegalArgumentException.class, () -> PersonFiles.readZip(new ByteArrayInputStream(zip(files, ""))));
    assertTrue(e.getMessage(), e.getMessage().contains("relations.tsv"));
  }

  private static byte[] zip(Map<String, byte[]> files, String folder) throws IOException {
    var out = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(out)) {
      for (var f : files.entrySet()) {
        zip.putNextEntry(new ZipEntry(folder + f.getKey()));
        zip.write(f.getValue());
        zip.closeEntry();
      }
    }
    return out.toByteArray();
  }
}
