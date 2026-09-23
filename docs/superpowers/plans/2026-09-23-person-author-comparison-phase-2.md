# Person Author Comparison, Phase 2: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A person registry of authors keyed by Wikidata, IPNI and ZooBank ids. It is loaded in `core` and grown from
Wikidata and IPNI by a hand run harvest, with a placeholder for ZooBank dumps. The first harvest is committed.

**Architecture:** Three TSV files (`persons`, `names`, `relations`) in `core/src/main/resources/authorship/persons/`.
`PersonFiles` reads and writes them, and `PersonRegistry` indexes every name form under its `AuthorshipNormalizer` key,
plus two derived forms per person. The harvest (test scope, `life.catalogue.matching.person.harvest`) reads
`PersonRecord`s from pluggable `PersonSource`s. It joins them on authority ids only, merges them into the existing files
by fill-only rules, and writes the files back together with a review report.

**Tech Stack:** Java 25, JUnit 4, Jackson, `java.net.http`, the Wikidata SPARQL endpoint, the IPNI search API.

**Spec:** `docs/2026-09-23-person-author-comparison.md`, sections "2. The person registry" and "3. The harvest", phase 2.

## Global Constraints

- Work in the worktree `/Users/markus/code/col/backend/.claude/worktrees/author-corpus` on branch `fix/author-nomcode`.
  Every shell needs `export JAVA_HOME=$HOME/.sdkman/candidates/java/25.0.3-librca`.
- Single test classes run as `mvn -o -pl core test -Dtest=<Class> -Dsurefire.failIfNoSpecifiedTests=false`, without `-q`.
  `-pl core` takes `api` and `dao` from `~/.m2`, which hold this branch since phase 1. Re-install them
  (`mvn -o -q -pl dao -am install -DskipTests`) only if `api` or `dao` change.
- Registry code lives in `core/src/main/java/life/catalogue/matching/person/` and the files in
  `core/src/main/resources/authorship/persons/`, never in `api`. The harvest is test scope in
  `core/src/test/java/life/catalogue/matching/person/harvest/`.
- File columns, verbatim:
  - `persons.tsv`: `id wikidata ipni zoobank formerIds family given suffix born died activeFrom activeTo groups source`
  - `names.tsv`: `person form kind code source`
  - `relations.tsv`: `person relation other source`
  - Tab delimited with a header, pipe separated lists, empty cell = null, sources written in lower case.
- Ids: `wd:Q…` if the person has a Wikidata id, else `ipni:…`, else `zb:…`, else a curated `clb:N`. A person that gains
  a better id moves the old one to `formerIds`.
- Sources are joined on shared authority ids only, never on names.
- A harvest fills empty cells and adds lines. It never overwrites a value and never removes a curated line.
- When sources disagree within one run: IPNI wins for a person with an IPNI and no ZooBank id, ZooBank for one with a
  ZooBank and no IPNI id, Wikidata otherwise. Every disagreement goes to the report. Years count as the same when they
  are at most 2 apart.
- `kind` ∈ `STANDARD CITATION FULL VARIANT`, `code` ∈ `BOT ZOO ANY`, `relation` ∈ `PARENT SIBLING`, where
  `person PARENT other` means that other is a parent of person.
- Tests never touch the network: sources take a `Fetcher`, and tests hand in canned responses.
- HTTP: User-Agent `col-backend-person-harvest/1.0 (https://www.checklistbank.org)`. Pause 1 s between Wikidata requests
  and 250 ms between IPNI requests, with retries and backoff. Every response is cached on disk so that a rerun resumes.
- Style: 2 space indent, 140 columns, `javax.annotation.Nullable`, comments explain why.
- Every commit message ends with `Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg`.

## Review Focus

- A request that fails with 429 or a timeout halfway through a run must not lose the run. Responses already fetched are
  served from the cache on the rerun (pinned in Task 3).
- A Wikidata person without any name form must not be written, since every person needs a name form (pinned in Task 5).
- A label holding a tab or a newline, which Wikidata has, must not break a TSV row (pinned in Task 1).
- Two Wikidata items that claim one IPNI id must stay two persons, and the conflict is reported (pinned in Task 5).
- An IPNI surname prefix with more than 10,000 authors is split, not truncated (pinned in Task 7).

---

### Task 1: Registry model and files

**Files:**
- Create: `core/src/main/java/life/catalogue/matching/person/NameKind.java`, `FormCode.java`, `RelationType.java`,
  `Provenance.java`, `Person.java`, `PersonName.java`, `PersonRelation.java`, `PersonFiles.java`
- Create: `core/src/main/resources/authorship/persons/persons.tsv`, `names.tsv` and `relations.tsv`, each holding its header line only
- Test: `core/src/test/java/life/catalogue/matching/person/PersonFilesTest.java`

**Interfaces:**
- Produces:
  - enums `NameKind`, `FormCode` (`boolean appliesTo(@Nullable NomCode)`), `RelationType` and `Provenance`
    (`String value()`, `static Provenance of(String)`)
  - `record Person(String id, @Nullable String wikidata, @Nullable String ipni, @Nullable String zoobank, List<String> formerIds,
    @Nullable String family, @Nullable String given, @Nullable String suffix, @Nullable Integer born, @Nullable Integer died,
    @Nullable Integer activeFrom, @Nullable Integer activeTo, Set<TaxGroup> groups, Provenance source)`,
    `static @Nullable String idFor(wikidata, ipni, zoobank)` and `Set<String> allIds()`
  - `record PersonName(String person, String form, NameKind kind, FormCode code, Provenance source)`
  - `record PersonRelation(String person, RelationType relation, String other, Provenance source)`
  - `PersonFiles.Content(List<Person> persons, List<PersonName> names, List<PersonRelation> relations)` with `empty()`,
    `PersonFiles.readResources()`, `PersonFiles.read(Path dir)` and `PersonFiles.write(Path dir, Content)`

- [ ] **Step 1: Write the failing test**

`PersonFilesTest.java`:

```java
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
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -o -pl core test -Dtest=PersonFilesTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR, since the package `life.catalogue.matching.person` is missing.

- [ ] **Step 3: Create the enums and records**

`NameKind.java`:

```java
package life.catalogue.matching.person;

/**
 * How a name form cites a person.
 */
public enum NameKind {
  /** a botanical standard form, from IPNI or Wikidata P428 */
  STANDARD,
  /** a zoological author citation, from Wikidata P835 or ZooBank */
  CITATION,
  /** the full name of the person */
  FULL,
  /** any other spelling or alias */
  VARIANT
}
```

`FormCode.java`:

```java
package life.catalogue.matching.person;

import org.gbif.nameparser.api.NomCode;

import javax.annotation.Nullable;

/**
 * The names a form is used in, with the meaning the codes have in authormap.txt: a botanical standard form such as
 * "Sw." (Swartz) is no citation of anybody in zoology.
 */
public enum FormCode {
  BOT, ZOO, ANY;

  /**
   * Names of the zoological code use ZOO and ANY forms, all others BOT and ANY, as the author map does.
   */
  public boolean appliesTo(@Nullable NomCode code) {
    return this == ANY || (code == NomCode.ZOOLOGICAL ? this == ZOO : this == BOT);
  }
}
```

`RelationType.java`:

```java
package life.catalogue.matching.person;

/**
 * How two persons are related. Relatives working in one field are what author strings cannot tell apart.
 */
public enum RelationType {
  /** the other person is a parent of the person */
  PARENT,
  SIBLING
}
```

`Provenance.java`:

```java
package life.catalogue.matching.person;

/**
 * Who a line of the registry came from. A harvest never removes or overwrites a curated line.
 */
public enum Provenance {
  CURATED, IPNI, ZOOBANK, WIKIDATA;

  /** the lower case form the files hold */
  public String value() {
    return name().toLowerCase();
  }

  public static Provenance of(String value) {
    return valueOf(value.toUpperCase());
  }
}
```

`Person.java`:

```java
package life.catalogue.matching.person;

import life.catalogue.api.vocab.TaxGroup;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * An author as an authority knows them.
 *
 * @param id        the best identifier the person has, prefixed: wd:, else ipni:, else zb:, else a curated clb:
 * @param formerIds ids the person had before it gained a better one, so lines that refer to them keep resolving
 * @param family    the family name as an authority records it, never split off a full name
 * @param suffix    a generation or filius: "II", "Jr.", "f."
 * @param groups    the taxonomic groups an authority records the person worked on, never mined from names
 */
public record Person(
  String id,
  @Nullable String wikidata,
  @Nullable String ipni,
  @Nullable String zoobank,
  List<String> formerIds,
  @Nullable String family,
  @Nullable String given,
  @Nullable String suffix,
  @Nullable Integer born,
  @Nullable Integer died,
  @Nullable Integer activeFrom,
  @Nullable Integer activeTo,
  Set<TaxGroup> groups,
  Provenance source
) {
  public static final String WIKIDATA = "wd:";
  public static final String IPNI = "ipni:";
  public static final String ZOOBANK = "zb:";
  public static final String LOCAL = "clb:";

  /**
   * @return the id of a person with these authority ids: the best of them, prefixed. Null without any
   */
  @Nullable
  public static String idFor(@Nullable String wikidata, @Nullable String ipni, @Nullable String zoobank) {
    if (wikidata != null) return WIKIDATA + wikidata;
    if (ipni != null) return IPNI + ipni;
    if (zoobank != null) return ZOOBANK + zoobank;
    return null;
  }

  /**
   * @return every id the person can be referred to by: its id, its former ids and its prefixed authority ids
   */
  public Set<String> allIds() {
    Set<String> ids = new LinkedHashSet<>();
    ids.add(id);
    ids.addAll(formerIds);
    if (wikidata != null) ids.add(WIKIDATA + wikidata);
    if (ipni != null) ids.add(IPNI + ipni);
    if (zoobank != null) ids.add(ZOOBANK + zoobank);
    return ids;
  }
}
```

`PersonName.java`:

```java
package life.catalogue.matching.person;

/**
 * One way a person is cited or named.
 *
 * @param person any id of the person, see {@link Person#allIds()}
 */
public record PersonName(String person, String form, NameKind kind, FormCode code, Provenance source) {
}
```

`PersonRelation.java`:

```java
package life.catalogue.matching.person;

/**
 * @param person any id of the person, see {@link Person#allIds()}
 * @param other  any id of the related person. For {@link RelationType#PARENT} it is the parent of person
 */
public record PersonRelation(String person, RelationType relation, String other, Provenance source) {
}
```

- [ ] **Step 4: Create `PersonFiles` and the empty files**

```java
package life.catalogue.matching.person;

import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.common.io.Resources;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

/**
 * Reads and writes the three files of the person registry. They are tab delimited with a header that is verified,
 * lists are pipe separated and an empty cell is null. Written sorted, so a harvest diffs line by line.
 */
public class PersonFiles {
  public static final String RESOURCE_DIR = "authorship/persons/";
  static final String PERSONS = "persons.tsv";
  static final String NAMES = "names.tsv";
  static final String RELATIONS = "relations.tsv";
  static final List<String> PERSON_COLUMNS = List.of("id", "wikidata", "ipni", "zoobank", "formerIds", "family", "given",
    "suffix", "born", "died", "activeFrom", "activeTo", "groups", "source");
  static final List<String> NAME_COLUMNS = List.of("person", "form", "kind", "code", "source");
  static final List<String> RELATION_COLUMNS = List.of("person", "relation", "other", "source");
  private static final String LIST_SEPARATOR = "|";

  public record Content(List<Person> persons, List<PersonName> names, List<PersonRelation> relations) {
    public static Content empty() {
      return new Content(List.of(), List.of(), List.of());
    }
  }

  private PersonFiles() {
  }

  private interface Opener {
    BufferedReader open(String file) throws IOException;
  }

  /**
   * @return the registry that ships with the code
   */
  public static Content readResources() throws IOException {
    return read(f -> Resources.reader(RESOURCE_DIR + f));
  }

  public static Content read(Path dir) throws IOException {
    return read(f -> Files.newBufferedReader(dir.resolve(f), StandardCharsets.UTF_8));
  }

  private static Content read(Opener opener) throws IOException {
    return new Content(
      rows(opener, PERSONS, PERSON_COLUMNS, PersonFiles::person),
      rows(opener, NAMES, NAME_COLUMNS, r -> new PersonName(r[0], r[1], NameKind.valueOf(r[2]), FormCode.valueOf(r[3]), Provenance.of(r[4]))),
      rows(opener, RELATIONS, RELATION_COLUMNS, r -> new PersonRelation(r[0], RelationType.valueOf(r[1]), r[2], Provenance.of(r[3])))
    );
  }

  private static <T> List<T> rows(Opener opener, String file, List<String> columns, Function<String[], T> parser) throws IOException {
    try (BufferedReader reader = opener.open(file)) {
      String header = reader.readLine();
      if (header == null || !Arrays.asList(header.split("\t", -1)).equals(columns)) {
        throw new IllegalArgumentException("Unexpected header in " + file + ": " + header);
      }
      List<T> list = new ArrayList<>();
      String line;
      int n = 1;
      while ((line = reader.readLine()) != null) {
        n++;
        if (line.isEmpty()) continue;
        String[] row = line.split("\t", -1);
        if (row.length != columns.size()) {
          throw new IllegalArgumentException(file + " line " + n + " has " + row.length + " columns, not " + columns.size());
        }
        try {
          list.add(parser.apply(row));
        } catch (RuntimeException e) {
          throw new IllegalArgumentException(file + " line " + n + ": " + e.getMessage(), e);
        }
      }
      return list;
    }
  }

  private static Person person(String[] r) {
    return new Person(r[0], str(r[1]), str(r[2]), str(r[3]), list(r[4]), str(r[5]), str(r[6]), str(r[7]),
      num(r[8]), num(r[9]), num(r[10]), num(r[11]), groups(r[12]), Provenance.of(r[13]));
  }

  @Nullable
  private static String str(String x) {
    return StringUtils.trimToNull(x);
  }

  @Nullable
  private static Integer num(String x) {
    return StringUtils.isBlank(x) ? null : Integer.valueOf(x.trim());
  }

  private static List<String> list(String x) {
    return StringUtils.isBlank(x) ? List.of() : List.of(StringUtils.split(x, LIST_SEPARATOR));
  }

  private static Set<TaxGroup> groups(String x) {
    Set<TaxGroup> groups = EnumSet.noneOf(TaxGroup.class);
    for (String g : list(x)) {
      groups.add(TaxGroup.valueOf(g));
    }
    return groups;
  }

  public static void write(Path dir, Content c) throws IOException {
    Files.createDirectories(dir);
    write(dir.resolve(PERSONS), PERSON_COLUMNS, c.persons().stream()
      .sorted(Comparator.comparing(Person::id))
      .map(p -> new Object[]{p.id(), p.wikidata(), p.ipni(), p.zoobank(), String.join(LIST_SEPARATOR, p.formerIds()),
        p.family(), p.given(), p.suffix(), p.born(), p.died(), p.activeFrom(), p.activeTo(),
        p.groups().stream().sorted().map(Enum::name).collect(Collectors.joining(LIST_SEPARATOR)), p.source().value()})
      .toList());
    write(dir.resolve(NAMES), NAME_COLUMNS, c.names().stream()
      .sorted(Comparator.comparing(PersonName::person).thenComparing(PersonName::kind).thenComparing(PersonName::code)
        .thenComparing(PersonName::form))
      .map(n -> new Object[]{n.person(), n.form(), n.kind(), n.code(), n.source().value()})
      .toList());
    write(dir.resolve(RELATIONS), RELATION_COLUMNS, c.relations().stream()
      .sorted(Comparator.comparing(PersonRelation::person).thenComparing(PersonRelation::relation)
        .thenComparing(PersonRelation::other))
      .map(r -> new Object[]{r.person(), r.relation(), r.other(), r.source().value()})
      .toList());
  }

  private static void write(Path file, List<String> columns, List<Object[]> rows) throws IOException {
    try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
      w.write(String.join("\t", columns));
      w.write('\n');
      for (Object[] row : rows) {
        w.write(Arrays.stream(row).map(PersonFiles::cell).collect(Collectors.joining("\t")));
        w.write('\n');
      }
    }
  }

  /**
   * A tab or line break inside a value would break the row, Wikidata labels hold them now and then.
   */
  private static String cell(@Nullable Object x) {
    return x == null ? "" : StringUtils.normalizeSpace(x.toString().replaceAll("[\\t\\r\\n]", " "));
  }
}
```

Create the three resource files with their header line and a trailing newline:

```
core/src/main/resources/authorship/persons/persons.tsv:   id	wikidata	ipni	zoobank	formerIds	family	given	suffix	born	died	activeFrom	activeTo	groups	source
core/src/main/resources/authorship/persons/names.tsv:     person	form	kind	code	source
core/src/main/resources/authorship/persons/relations.tsv: person	relation	other	source
```

Write them with `printf 'id\twikidata\t…\n' > …` so that the separators are real tabs.

- [ ] **Step 5: Run the test**

Run: `mvn -o -pl core test -Dtest=PersonFilesTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 6 tests pass.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/life/catalogue/matching/person/ core/src/main/resources/authorship/persons/ \
  core/src/test/java/life/catalogue/matching/person/PersonFilesTest.java
git commit -m "feat(authorship): the files of a person registry

Persons keyed by Wikidata, IPNI and ZooBank ids with their name forms,
years, authority taxon groups and relations, in three sorted TSV files in
core. Empty for now, a harvest fills them.

Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg"
```

---

### Task 2: PersonRegistry

**Files:**
- Create: `core/src/main/java/life/catalogue/matching/person/PersonRegistry.java`
- Test: `core/src/test/java/life/catalogue/matching/person/PersonRegistryTest.java`,
  `core/src/test/java/life/catalogue/matching/person/PersonRegistryFilesTest.java`

**Interfaces:**
- Consumes: Task 1's records and `PersonFiles`, and `AuthorshipNormalizer.normalize(String)` from `api`
- Produces: `new PersonRegistry(PersonFiles.Content)`, `static PersonRegistry get()`,
  `Set<Person> candidates(String citation, @Nullable NomCode code)`, `@Nullable Person get(String anyId)`,
  `Set<Person> relatives(Person)`, `List<String> problems()`, `int size()`

- [ ] **Step 1: Write the failing tests**

`PersonRegistryTest.java`:

```java
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
```

`PersonRegistryFilesTest.java`:

```java
package life.catalogue.matching.person;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Guards the registry that ships with the code: every reference resolves, ids are unique and consistent, every
 * person has a name.
 */
public class PersonRegistryFilesTest {

  @Test
  public void committedRegistryIsConsistent() throws Exception {
    assertEquals(List.of(), new PersonRegistry(PersonFiles.readResources()).problems());
  }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `mvn -o -pl core test -Dtest='PersonRegistryTest,PersonRegistryFilesTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR, since `PersonRegistry` does not exist yet.

- [ ] **Step 3: Implement `PersonRegistry`**

```java
package life.catalogue.matching.person;

import life.catalogue.common.tax.AuthorshipNormalizer;

import org.gbif.nameparser.api.NomCode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

import javax.annotation.Nullable;

/**
 * The persons of the registry with every name form they are cited by, looked up the way citations are compared:
 * under their {@link AuthorshipNormalizer#normalize(String)} key. Next to the forms of the files, two forms are
 * derived per person with a family name: the initials of the given names with family name and suffix
 * ("G. B. Sowerby II") and the bare family name ("Sowerby"). A key may name several persons; that is intended, a
 * bare surname proposes candidates only.
 * <p>
 * Loaded once and only by what asks for it, so the string comparison pays nothing.
 */
public class PersonRegistry {
  private static PersonRegistry instance;

  private record Form(Person person, FormCode code) {
  }

  private final int size;
  private final Map<String, Person> byId = new HashMap<>();
  private final Map<String, List<Form>> byKey = new HashMap<>();
  private final Map<String, Set<Person>> relatives = new HashMap<>();
  private final List<String> problems = new ArrayList<>();

  public static synchronized PersonRegistry get() {
    if (instance == null) {
      try {
        instance = new PersonRegistry(PersonFiles.readResources());
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return instance;
  }

  public PersonRegistry(PersonFiles.Content c) {
    size = c.persons().size();
    for (Person p : c.persons()) {
      for (String id : p.allIds()) {
        Person prev = byId.putIfAbsent(id, p);
        if (prev != null && prev != p) {
          problems.add("id " + id + " is held by " + prev.id() + " and " + p.id());
        }
      }
      if (p.id().startsWith(Person.LOCAL)) {
        if (p.wikidata() != null || p.ipni() != null || p.zoobank() != null) {
          problems.add(p.id() + " is local but has authority ids");
        }
      } else if (!p.id().equals(Person.idFor(p.wikidata(), p.ipni(), p.zoobank()))) {
        problems.add(p.id() + " should be " + Person.idFor(p.wikidata(), p.ipni(), p.zoobank()));
      }
      if (p.born() != null && p.died() != null && p.born() > p.died()) {
        problems.add(p.id() + " was born after it died");
      }
      if (p.family() != null) {
        add(p.family(), p, FormCode.ANY);
        add(initials(p.given()) + p.family() + (p.suffix() == null ? "" : " " + p.suffix()), p, FormCode.ANY);
      }
    }
    Set<Person> named = Collections.newSetFromMap(new IdentityHashMap<>());
    for (PersonName n : c.names()) {
      Person p = byId.get(n.person());
      if (p == null) {
        problems.add("name " + n.form() + " refers to unknown person " + n.person());
        continue;
      }
      named.add(p);
      add(n.form(), p, n.code());
    }
    for (Person p : c.persons()) {
      if (!named.contains(p)) {
        problems.add(p.id() + " has no name");
      }
    }
    for (PersonRelation r : c.relations()) {
      Person a = byId.get(r.person());
      Person b = byId.get(r.other());
      if (a == null || b == null) {
        problems.add("relation " + r.person() + " " + r.relation() + " " + r.other() + " refers to unknown person "
          + (a == null ? r.person() : r.other()));
        continue;
      }
      relatives.computeIfAbsent(a.id(), k -> new LinkedHashSet<>()).add(b);
      relatives.computeIfAbsent(b.id(), k -> new LinkedHashSet<>()).add(a);
    }
  }

  private void add(String form, Person p, FormCode code) {
    String key = AuthorshipNormalizer.normalize(form);
    if (key != null) {
      List<Form> forms = byKey.computeIfAbsent(key, k -> new ArrayList<>(1));
      Form f = new Form(p, code);
      if (!forms.contains(f)) {
        forms.add(f);
      }
    }
  }

  /**
   * @return "G. B. " for "George Brettingham", "J. B. " for "Jean-Baptiste", empty for none
   */
  static String initials(@Nullable String given) {
    if (given == null) return "";
    StringBuilder sb = new StringBuilder();
    for (String part : given.split("[\\s-]+")) {
      String letters = part.replaceAll("^[^\\p{L}]+", "");
      if (!letters.isEmpty()) {
        sb.append(letters.charAt(0)).append(". ");
      }
    }
    return sb.toString();
  }

  /**
   * @return the persons a citation may name under the code of the name: several for an ambiguous citation such as a
   *         bare surname, none for an author the registry does not know
   */
  public Set<Person> candidates(String citation, @Nullable NomCode code) {
    String key = AuthorshipNormalizer.normalize(citation);
    if (key == null) return Set.of();
    Set<Person> persons = new LinkedHashSet<>();
    for (Form f : byKey.getOrDefault(key, List.of())) {
      if (f.code().appliesTo(code)) {
        persons.add(f.person());
      }
    }
    return persons;
  }

  /**
   * @param anyId an id, a former id or a prefixed authority id
   */
  @Nullable
  public Person get(String anyId) {
    return byId.get(anyId);
  }

  /**
   * @return parents, children and siblings
   */
  public Set<Person> relatives(Person p) {
    return relatives.getOrDefault(p.id(), Set.of());
  }

  /**
   * @return what is wrong with the files, empty for a consistent registry
   */
  public List<String> problems() {
    return Collections.unmodifiableList(problems);
  }

  public int size() {
    return size;
  }
}
```

- [ ] **Step 4: Run the tests**

Run: `mvn -o -pl core test -Dtest='PersonRegistryTest,PersonRegistryFilesTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 7 tests pass. Two outcomes have a known cause:
- `derivedForms` fails on `G.B. Sowerby II` or `O. Swartz`: print `AuthorshipNormalizer.normalize` of the citation and of
  the derived form. They must fold to the same key: `g b sowerby ii` and `o swartz`.
- `relativesBothWays`: `Person` is a record, so a `Set.of(SOWERBY1)` equals the registry's instance by value.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/life/catalogue/matching/person/PersonRegistry.java \
  core/src/test/java/life/catalogue/matching/person/PersonRegistryTest.java \
  core/src/test/java/life/catalogue/matching/person/PersonRegistryFilesTest.java
git commit -m "feat(authorship): look up the persons a citation may name

PersonRegistry indexes every name form of a person under the key citations
are compared by, plus the initials with family name and suffix and the bare
family name. A bare surname proposes every person of that name, the code
of the name restricts standard forms and citations. A test guards the
committed files.

Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg"
```

---

### Task 3: Harvest records, sources and a caching fetcher

**Files:**
- Create in `core/src/test/java/life/catalogue/matching/person/harvest/`: `PersonRecord.java`, `PersonSource.java`,
  `Fetcher.java`, `HttpFetcher.java`, `CachingFetcher.java`
- Test: `core/src/test/java/life/catalogue/matching/person/harvest/CachingFetcherTest.java`

**Interfaces:**
- Consumes: Task 1's enums
- Produces:
  - `record PersonRecord(Provenance source, @Nullable String wikidata, @Nullable String ipni, @Nullable String zoobank,
    @Nullable String family, @Nullable String given, @Nullable String suffix, @Nullable Integer born, @Nullable Integer died,
    @Nullable Integer activeFrom, @Nullable Integer activeTo, Set<TaxGroup> groups, List<PersonRecord.Form> names,
    List<PersonRecord.Link> relations)` with `record Form(String form, NameKind kind, FormCode code)`,
    `record Link(RelationType relation, String other)` (`other` a prefixed authority id), `Set<String> ids()` (own id
    first) and `class Builder`
  - `interface PersonSource { String name(); List<PersonRecord> read() throws Exception; default String stats() }`
  - `interface Fetcher { String get(String url) throws Exception; }`, `new HttpFetcher(String accept, Duration pause)`,
    `new CachingFetcher(Path dir, Fetcher delegate)`

- [ ] **Step 1: Write the failing test**

`CachingFetcherTest.java`:

```java
package life.catalogue.matching.person.harvest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class CachingFetcherTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  /** a harvest that dies halfway resumes from what it fetched before */
  @Test
  public void servesFromDiskAcrossRuns() throws Exception {
    Path dir = tmp.newFolder().toPath();
    List<String> asked = new ArrayList<>();
    Fetcher net = url -> {
      asked.add(url);
      return "body of " + url;
    };
    assertEquals("body of a", new CachingFetcher(dir, net).get("a"));
    assertEquals("body of a", new CachingFetcher(dir, net).get("a"));
    assertEquals("body of b", new CachingFetcher(dir, net).get("b"));
    assertEquals(List.of("a", "b"), asked);
  }

  @Test
  public void aFailureIsNotCached() throws Exception {
    Path dir = tmp.newFolder().toPath();
    Fetcher failing = url -> {
      throw new IllegalStateException("HTTP 429");
    };
    assertThrows(IllegalStateException.class, () -> new CachingFetcher(dir, failing).get("a"));
    assertEquals("ok", new CachingFetcher(dir, url -> "ok").get("a"));
  }

  @Test
  public void recordIdsOwnFirst() {
    var b = new PersonRecord.Builder(life.catalogue.matching.person.Provenance.IPNI);
    b.wikidata = "Q1";
    b.ipni = "1-1";
    assertEquals(List.of("ipni:1-1", "wd:Q1"), List.copyOf(b.build().ids()));
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -o -pl core test -Dtest=CachingFetcherTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement**

`Fetcher.java`:

```java
package life.catalogue.matching.person.harvest;

/**
 * Fetches a URL. Sources take one so tests can hand in canned answers.
 */
@FunctionalInterface
public interface Fetcher {
  String get(String url) throws Exception;
}
```

`HttpFetcher.java`:

```java
package life.catalogue.matching.person.harvest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Fetches politely: a pause before every request, growing with each retry, so a rate limit (HTTP 429) or a
 * timeout of the Wikidata query service costs a wait, not the run.
 */
public class HttpFetcher implements Fetcher {
  private static final String USER_AGENT = "col-backend-person-harvest/1.0 (https://www.checklistbank.org)";
  private static final int MAX_RETRIES = 6;
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
  private final String accept;
  private final Duration pause;

  public HttpFetcher(String accept, Duration pause) {
    this.accept = accept;
    this.pause = pause;
  }

  @Override
  public String get(String url) throws Exception {
    Exception last = null;
    for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
      Thread.sleep(pause.toMillis() * attempt * attempt);
      try {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
          .header("Accept", accept)
          .header("User-Agent", USER_AGENT)
          .timeout(Duration.ofMinutes(3)).GET().build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() == 200) {
          return resp.body();
        }
        last = new IllegalStateException("HTTP " + resp.statusCode() + " for " + url);
      } catch (Exception e) {
        last = e;
      }
      System.err.println("  fetch attempt " + attempt + "/" + MAX_RETRIES + " failed: " + last.getMessage());
    }
    throw last;
  }
}
```

`CachingFetcher.java`:

```java
package life.catalogue.matching.person.harvest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.apache.commons.codec.digest.DigestUtils;

/**
 * Keeps every answer on disk under the SHA-1 of its URL, so a harvest that dies halfway resumes where it stopped.
 * Delete the directory for a fresh harvest.
 */
public class CachingFetcher implements Fetcher {
  private final Path dir;
  private final Fetcher delegate;

  public CachingFetcher(Path dir, Fetcher delegate) {
    this.dir = dir;
    this.delegate = delegate;
  }

  @Override
  public String get(String url) throws Exception {
    Path f = dir.resolve(DigestUtils.sha1Hex(url));
    if (Files.exists(f)) {
      return Files.readString(f, StandardCharsets.UTF_8);
    }
    String body = delegate.get(url);
    Files.createDirectories(dir);
    Path tmp = Files.createTempFile(dir, "fetch", ".tmp");
    Files.writeString(tmp, body, StandardCharsets.UTF_8);
    Files.move(tmp, f, StandardCopyOption.ATOMIC_MOVE);
    return body;
  }
}
```

If `org.apache.commons.codec.digest.DigestUtils` is not on the core test classpath (check with
`grep -rl DigestUtils core/src dao/src | head -1`), hash with `java.security.MessageDigest.getInstance("SHA-1")` and
`java.util.HexFormat.of().formatHex(...)` instead.

`PersonSource.java`:

```java
package life.catalogue.matching.person.harvest;

import java.util.List;

/**
 * An authority the registry is harvested from.
 */
public interface PersonSource {
  String name();

  List<PersonRecord> read() throws Exception;

  /**
   * @return what the source could not map, for the review report
   */
  default String stats() {
    return "";
  }
}
```

`PersonRecord.java`:

```java
package life.catalogue.matching.person.harvest;

import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.matching.person.FormCode;
import life.catalogue.matching.person.NameKind;
import life.catalogue.matching.person.Person;
import life.catalogue.matching.person.Provenance;
import life.catalogue.matching.person.RelationType;

import java.util.*;

import javax.annotation.Nullable;

/**
 * One person as one authority knows them.
 *
 * @param relations to other persons by a prefixed authority id of the same source, e.g. wd:Q42
 */
public record PersonRecord(
  Provenance source,
  @Nullable String wikidata,
  @Nullable String ipni,
  @Nullable String zoobank,
  @Nullable String family,
  @Nullable String given,
  @Nullable String suffix,
  @Nullable Integer born,
  @Nullable Integer died,
  @Nullable Integer activeFrom,
  @Nullable Integer activeTo,
  Set<TaxGroup> groups,
  List<Form> names,
  List<Link> relations
) {
  public record Form(String form, NameKind kind, FormCode code) {
  }

  public record Link(RelationType relation, String other) {
  }

  /**
   * @return the prefixed authority ids, the one of the record's own source first
   */
  public Set<String> ids() {
    Set<String> ids = new LinkedHashSet<>();
    String own = switch (source) {
      case WIKIDATA -> wikidata == null ? null : Person.WIKIDATA + wikidata;
      case IPNI -> ipni == null ? null : Person.IPNI + ipni;
      case ZOOBANK -> zoobank == null ? null : Person.ZOOBANK + zoobank;
      case CURATED -> null;
    };
    if (own != null) ids.add(own);
    if (wikidata != null) ids.add(Person.WIKIDATA + wikidata);
    if (ipni != null) ids.add(Person.IPNI + ipni);
    if (zoobank != null) ids.add(Person.ZOOBANK + zoobank);
    return ids;
  }

  /**
   * Collects a person across several answers of a source.
   */
  public static final class Builder {
    final Provenance source;
    public String wikidata;
    public String ipni;
    public String zoobank;
    public String suffix;
    String label;
    final List<String> family = new ArrayList<>();
    final List<String> given = new ArrayList<>();
    Integer born;
    Integer died;
    Integer activeFrom;
    Integer activeTo;
    final Set<TaxGroup> groups = EnumSet.noneOf(TaxGroup.class);
    final List<Form> names = new ArrayList<>();
    final List<Link> relations = new ArrayList<>();

    public Builder(Provenance source) {
      this.source = source;
    }

    /** the full name the person is known by, which also orders family and given names */
    void label(String label) {
      this.label = label;
      name(label, NameKind.FULL, FormCode.ANY);
    }

    void name(@Nullable String form, NameKind kind, FormCode code) {
      if (form == null || form.isBlank()) return;
      Form f = new Form(form.trim(), kind, code);
      if (!names.contains(f)) {
        names.add(f);
      }
    }

    void family(@Nullable String x) {
      if (x != null && !x.isBlank() && !family.contains(x.trim())) family.add(x.trim());
    }

    void given(@Nullable String x) {
      if (x != null && !x.isBlank() && !given.contains(x.trim())) given.add(x.trim());
    }

    void born(@Nullable Integer y) {
      born = min(born, y);
    }

    void died(@Nullable Integer y) {
      died = max(died, y);
    }

    void activeFrom(@Nullable Integer y) {
      activeFrom = min(activeFrom, y);
    }

    void activeTo(@Nullable Integer y) {
      activeTo = max(activeTo, y);
    }

    void link(RelationType type, String other) {
      Link l = new Link(type, other);
      if (!relations.contains(l)) {
        relations.add(l);
      }
    }

    private static Integer min(Integer a, Integer b) {
      return a == null ? b : b == null ? a : Math.min(a, b);
    }

    private static Integer max(Integer a, Integer b) {
      return a == null ? b : b == null ? a : Math.max(a, b);
    }

    public PersonRecord build() {
      return new PersonRecord(source, wikidata, ipni, zoobank, Names.ordered(family, label), Names.ordered(given, label),
        suffix != null ? suffix : Names.suffix(label), born, died, activeFrom, activeTo, Set.copyOf(groups),
        List.copyOf(names), List.copyOf(relations));
    }
  }
}
```

`Builder.build()` uses `Names`, which is Task 4. To compile Task 3 on its own, create `Names.java` now with exactly this
content. Task 4 fills in the bodies test first:

```java
package life.catalogue.matching.person.harvest;

import java.util.List;

import javax.annotation.Nullable;

final class Names {
  private Names() {
  }

  @Nullable
  static String ordered(List<String> parts, @Nullable String label) {
    return parts.isEmpty() ? null : String.join(" ", parts);
  }

  @Nullable
  static String suffix(@Nullable String label) {
    return null;
  }
}
```

- [ ] **Step 4: Run the test**

Run: `mvn -o -pl core test -Dtest=CachingFetcherTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 3 tests pass.

- [ ] **Step 5: Commit**

```bash
git add core/src/test/java/life/catalogue/matching/person/harvest/
git commit -m "test(authorship): records, sources and a caching fetcher for the person harvest

Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg"
```

---

### Task 4: Reading years, groups and names

**Files:**
- Create: `core/src/test/java/life/catalogue/matching/person/harvest/Years.java`, `Groups.java`
- Modify: `core/src/test/java/life/catalogue/matching/person/harvest/Names.java` (replace the Task 3 stub)
- Test: `YearsTest.java`, `GroupsTest.java` and `NamesTest.java` in the same package

**Interfaces:**
- Produces:
  - `Years.Span(@Nullable Integer born, @Nullable Integer died, @Nullable Integer activeFrom, @Nullable Integer activeTo)`
    and `Years.NONE`
  - `static Years.Span Years.ipni(@Nullable String dates)` and `static @Nullable Integer Years.wikidata(@Nullable String time)`
  - `static Set<TaxGroup> Groups.ipni(@Nullable String taxonGroups, Consumer<String> unmapped)` and
    `static Set<TaxGroup> Groups.field(String englishLabel)`
  - `static @Nullable String Names.ordered(List<String> parts, @Nullable String label)`,
    `static @Nullable String Names.suffix(@Nullable String label)` and
    `static @Nullable String Names.surnameFirst(String ipniAlternativeName)`

- [ ] **Step 1: Write the failing tests**

`YearsTest.java`:

```java
package life.catalogue.matching.person.harvest;

import org.junit.Test;

import static org.junit.Assert.*;

public class YearsTest {

  @Test
  public void ipniDates() {
    assertEquals(new Years.Span(1757, 1822, null, null), Years.ipni("1757-1822"));
    assertEquals(new Years.Span(1967, null, null, null), Years.ipni("1967-"));
    assertEquals(new Years.Span(null, null, 1980, 1980), Years.ipni("fl. 1980"));
    assertEquals(new Years.Span(null, null, 1977, null), Years.ipni("fl. 1977-"));
    assertEquals(new Years.Span(null, null, 1850, 1870), Years.ipni("fl. 1850-1870"));
    assertEquals(new Years.Span(1950, null, null, null), Years.ipni("b. 1950"));
    assertEquals(new Years.Span(null, 1890, null, null), Years.ipni("d. 1890"));
    assertEquals(new Years.Span(1757, 1822, null, null), Years.ipni("(c. 1757-1822?)"));
    assertEquals(Years.NONE, Years.ipni(""));
    assertEquals(Years.NONE, Years.ipni(null));
    assertEquals(Years.NONE, Years.ipni("19th century"));
  }

  @Test
  public void wikidataTimes() {
    assertEquals(Integer.valueOf(1788), Years.wikidata("1788-01-01T00:00:00Z"));
    assertEquals(Integer.valueOf(1788), Years.wikidata("+1788-06-11T00:00:00Z"));
    assertNull(Years.wikidata("-0350-01-01T00:00:00Z"));
    assertNull(Years.wikidata("t123456"));
    assertNull(Years.wikidata(null));
  }
}
```

`GroupsTest.java`:

```java
package life.catalogue.matching.person.harvest;

import life.catalogue.api.vocab.TaxGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import static org.junit.Assert.*;

public class GroupsTest {

  @Test
  public void ipniGroups() {
    List<String> unmapped = new ArrayList<>();
    assertEquals(Set.of(TaxGroup.Fungi, TaxGroup.Angiosperms, TaxGroup.Gymnosperms, TaxGroup.Algae, TaxGroup.Pteridophytes),
      Groups.ipni("Mycology, Spermatophytes, Algae, Pteridophytes", unmapped::add));
    // fossils and pre-Linnaean works are no group of organisms and are dropped without a word
    assertEquals(Set.of(TaxGroup.Bryophytes), Groups.ipni("Bryophytes, Fossils, Pre-Linnaean", unmapped::add));
    assertEquals(Set.of(), Groups.ipni("", unmapped::add));
    assertEquals(Set.of(), Groups.ipni("Lichens", unmapped::add));
    assertEquals(List.of("Lichens"), unmapped);
  }

  @Test
  public void fieldsOfWork() {
    assertEquals(Set.of(TaxGroup.Plants, TaxGroup.Fungi), Groups.field("botany"));
    assertEquals(Set.of(TaxGroup.Molluscs), Groups.field("Malacology"));
    assertEquals(Set.of(TaxGroup.Coleoptera), Groups.field("coleopterology"));
    assertEquals(Set.of(), Groups.field("politics"));
  }
}
```

`NamesTest.java`:

```java
package life.catalogue.matching.person.harvest;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

public class NamesTest {

  /** Wikidata's given and family names are unordered statements, the label knows their order */
  @Test
  public void ordered() {
    assertEquals("George Brettingham", Names.ordered(List.of("Brettingham", "George"), "George Brettingham Sowerby II"));
    assertEquals("Ruiz López", Names.ordered(List.of("López", "Ruiz"), "Hipólito Ruiz López"));
    // without a label the order of the statements stands
    assertEquals("Maria Anna", Names.ordered(List.of("Maria", "Anna"), null));
    assertNull(Names.ordered(List.of(), "Carl Linnaeus"));
  }

  @Test
  public void suffix() {
    assertEquals("II", Names.suffix("George Brettingham Sowerby II"));
    assertEquals("I", Names.suffix("George Brettingham Sowerby I"));
    assertEquals("Jr.", Names.suffix("John Smith Jr"));
    assertEquals("Jr.", Names.suffix("John Smith, Jr."));
    assertNull(Names.suffix("Carl Linnaeus"));
    assertNull(Names.suffix("Ai"));
    assertNull(Names.suffix(null));
  }

  @Test
  public void surnameFirst() {
    assertEquals("James DeCarle Sowerby", Names.surnameFirst("Sowerby, James DeCarle"));
    assertEquals("Nees", Names.surnameFirst("Nees"));
    assertNull(Names.surnameFirst(" "));
  }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `mvn -o -pl core test -Dtest='YearsTest,GroupsTest,NamesTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR for `Years` and `Groups`, and `NamesTest` failures against the stub.

- [ ] **Step 3: Implement**

`Years.java`:

```java
package life.catalogue.matching.person.harvest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * Reads the years of a person from what the authorities write.
 */
final class Years {
  record Span(@Nullable Integer born, @Nullable Integer died, @Nullable Integer activeFrom, @Nullable Integer activeTo) {
  }

  static final Span NONE = new Span(null, null, null, null);
  private static final Pattern FLORUIT = Pattern.compile("^fl\\.?\\s*(\\d{4})s?\\s*(-\\s*(\\d{4})?)?$", Pattern.CASE_INSENSITIVE);
  private static final Pattern BORN = Pattern.compile("^b\\.?\\s*(\\d{4})$", Pattern.CASE_INSENSITIVE);
  private static final Pattern DIED = Pattern.compile("^d\\.?\\s*(\\d{4})$", Pattern.CASE_INSENSITIVE);
  private static final Pattern LIFE = Pattern.compile("^(\\d{4})?\\s*-\\s*(\\d{4})?$");
  private static final Pattern WIKIDATA = Pattern.compile("^\\+?(\\d{1,4})-\\d\\d-\\d\\dT");

  private Years() {
  }

  /**
   * @param dates IPNI's "1757-1822", "1967-", "fl. 1980", "fl. 1977-", "b. 1950", "d. 1890", with circa and question
   *              marks ignored. A single floruit year is a span of that year alone.
   */
  static Span ipni(@Nullable String dates) {
    if (dates == null) return NONE;
    String s = dates.replaceAll("(?i)\\b(?:ca?\\.|circa)\\s*", "")
      .replaceAll("[()\\[\\]?]", "")
      .replace('–', '-')
      .trim();
    Matcher m = FLORUIT.matcher(s);
    if (m.find()) {
      Integer from = Integer.valueOf(m.group(1));
      Integer to = m.group(2) == null ? from : m.group(3) == null ? null : Integer.valueOf(m.group(3));
      return new Span(null, null, from, to);
    }
    m = BORN.matcher(s);
    if (m.find()) return new Span(Integer.valueOf(m.group(1)), null, null, null);
    m = DIED.matcher(s);
    if (m.find()) return new Span(null, Integer.valueOf(m.group(1)), null, null);
    m = LIFE.matcher(s);
    if (m.find() && (m.group(1) != null || m.group(2) != null)) {
      return new Span(m.group(1) == null ? null : Integer.valueOf(m.group(1)), m.group(2) == null ? null : Integer.valueOf(m.group(2)), null, null);
    }
    return NONE;
  }

  /**
   * @return the year of a Wikidata time value like "+1788-01-01T00:00:00Z", null for a year before the common era
   */
  @Nullable
  static Integer wikidata(@Nullable String time) {
    if (time == null) return null;
    Matcher m = WIKIDATA.matcher(time);
    return m.find() ? Integer.valueOf(m.group(1)) : null;
  }
}
```

`Groups.java`:

```java
package life.catalogue.matching.person.harvest;

import life.catalogue.api.vocab.TaxGroup;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import static java.util.Map.entry;
import static life.catalogue.api.vocab.TaxGroup.*;

/**
 * Maps what an authority records a person worked on to taxonomic groups. Only authorities are asked: an author string
 * is not a person, and mining our own names would give an ambiguous string the groups of everyone who shares it.
 */
final class Groups {
  private static final Map<String, Set<TaxGroup>> IPNI = Map.of(
    "Spermatophytes", Set.of(Angiosperms, Gymnosperms),
    "Pteridophytes", Set.of(Pteridophytes),
    "Bryophytes", Set.of(Bryophytes),
    "Algae", Set.of(Algae),
    "Mycology", Set.of(Fungi)
  );
  /** IPNI values that are no group of organisms */
  private static final Set<String> IPNI_IGNORED = Set.of("Fossils", "Pre-Linnaean");
  /**
   * Wikidata fields of work (P101) by their English label. Botany keeps fungi: botanists described them for centuries.
   */
  private static final Map<String, Set<TaxGroup>> FIELDS = Map.ofEntries(
    entry("botany", Set.of(Plants, Fungi)),
    entry("phycology", Set.of(Algae)),
    entry("bryology", Set.of(Bryophytes)),
    entry("pteridology", Set.of(Pteridophytes)),
    entry("mycology", Set.of(Fungi)),
    entry("lichenology", Set.of(Fungi)),
    entry("zoology", Set.of(Animals)),
    entry("entomology", Set.of(Insects)),
    entry("coleopterology", Set.of(Coleoptera)),
    entry("lepidopterology", Set.of(Lepidoptera)),
    entry("dipterology", Set.of(Diptera)),
    entry("hymenopterology", Set.of(Hymenoptera)),
    entry("myrmecology", Set.of(Hymenoptera)),
    entry("arachnology", Set.of(Arachnids)),
    entry("acarology", Set.of(Arachnids)),
    entry("carcinology", Set.of(Crustacean)),
    entry("malacology", Set.of(Molluscs)),
    entry("conchology", Set.of(Molluscs)),
    entry("ichthyology", Set.of(Chordates)),
    entry("herpetology", Set.of(Chordates)),
    entry("ornithology", Set.of(Chordates)),
    entry("mammalogy", Set.of(Chordates)),
    entry("protistology", Set.of(Protists)),
    entry("protozoology", Set.of(Protists)),
    entry("bacteriology", Set.of(Bacteria)),
    entry("virology", Set.of(Viruses))
  );

  private Groups() {
  }

  /**
   * @param taxonGroups IPNI's "Mycology, Spermatophytes, Algae"
   * @param unmapped    receives every value that is neither mapped nor known to be no group
   */
  static Set<TaxGroup> ipni(@Nullable String taxonGroups, Consumer<String> unmapped) {
    Set<TaxGroup> groups = EnumSet.noneOf(TaxGroup.class);
    if (taxonGroups == null) return groups;
    for (String g : taxonGroups.split(",")) {
      String v = g.trim();
      if (v.isEmpty() || IPNI_IGNORED.contains(v)) continue;
      Set<TaxGroup> mapped = IPNI.get(v);
      if (mapped == null) {
        unmapped.accept(v);
      } else {
        groups.addAll(mapped);
      }
    }
    return groups;
  }

  /**
   * @return the groups of a Wikidata field of work, none for a field that is no group of organisms
   */
  static Set<TaxGroup> field(String englishLabel) {
    return FIELDS.getOrDefault(englishLabel.trim().toLowerCase(), Set.of());
  }
}
```

`Names.java`, replacing the stub:

```java
package life.catalogue.matching.person.harvest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

/**
 * Structured names from what the authorities give. A family name is never split off a full name: the last word is
 * not the surname in "Geoffroy Saint-Hilaire" or "Ruiz López".
 */
final class Names {
  private static final Pattern SUFFIX = Pattern.compile("[\\s,]+(I{1,3}|IV|Jr|Sr)\\.?$");

  private Names() {
  }

  /**
   * @return the parts in the order they appear in the label, parts the label lacks last; null for none
   */
  @Nullable
  static String ordered(List<String> parts, @Nullable String label) {
    if (parts.isEmpty()) return null;
    List<String> sorted = new ArrayList<>(parts);
    if (label != null) {
      sorted.sort(Comparator.comparingInt(p -> {
        int idx = label.indexOf(p);
        return idx < 0 ? Integer.MAX_VALUE : idx;
      }));
    }
    return String.join(" ", sorted);
  }

  /**
   * @return a generation (I to IV) or Jr./Sr. ending the label, null for none
   */
  @Nullable
  static String suffix(@Nullable String label) {
    if (label == null) return null;
    Matcher m = SUFFIX.matcher(label.trim());
    if (!m.find()) return null;
    String s = m.group(1);
    return s.equals("Jr") || s.equals("Sr") ? s + "." : s;
  }

  /**
   * @param ipniAlternativeName one of IPNI's alternative names, "Sowerby, James DeCarle"
   * @return "James DeCarle Sowerby", null for a blank one
   */
  @Nullable
  static String surnameFirst(String ipniAlternativeName) {
    String x = StringUtils.trimToNull(ipniAlternativeName);
    if (x == null) return null;
    int comma = x.indexOf(',');
    return comma < 0 ? x : (x.substring(comma + 1).trim() + " " + x.substring(0, comma).trim()).trim();
  }
}
```

- [ ] **Step 4: Run the tests**

Run: `mvn -o -pl core test -Dtest='YearsTest,GroupsTest,NamesTest,CachingFetcherTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add core/src/test/java/life/catalogue/matching/person/harvest/
git commit -m "test(authorship): read years, groups and names from IPNI and Wikidata

Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg"
```

---

### Task 5: PersonMerger

**Files:**
- Create: `core/src/test/java/life/catalogue/matching/person/harvest/PersonMerger.java`, `MergeReport.java`
- Test: `core/src/test/java/life/catalogue/matching/person/harvest/PersonMergerTest.java`

**Interfaces:**
- Consumes: `PersonFiles.Content`, `PersonRecord` and `PersonRegistry` (only in the tests, to prove consistency)
- Produces: `new PersonMerger().merge(PersonFiles.Content existing, List<PersonRecord> records, Map<String, String> wikidataRedirects)`
  returning `PersonMerger.Result(PersonFiles.Content content, MergeReport report)`, and `MergeReport.render()` with the
  public int fields `existing`, `added`, `merged`, `redirected`, `withoutName`, `withoutId`, `relationsDropped` and
  the lists `conflicts`, `ambiguous` and `notSeen`

- [ ] **Step 1: Write the failing test**

`PersonMergerTest.java`:

```java
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
    assertEquals(List.of(), r.report().conflicts);
    // years 1911 and 1912 are within 2 of each other
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
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -o -pl core test -Dtest=PersonMergerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement `MergeReport`**

```java
package life.catalogue.matching.person.harvest;

import java.util.ArrayList;
import java.util.List;

/**
 * What a merge did and everything a person should look at.
 */
public class MergeReport {
  private static final int LIST_LIMIT = 500;
  public int existing;
  public int added;
  public int merged;
  public int redirected;
  public int withoutName;
  public int withoutId;
  public int relationsDropped;
  public final List<String> conflicts = new ArrayList<>();
  public final List<String> ambiguous = new ArrayList<>();
  public final List<String> notSeen = new ArrayList<>();

  public String render() {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("persons before %,d, added %,d, merged into another %,d, following a Wikidata redirect %,d%n",
      existing, added, merged, redirected));
    sb.append(String.format("dropped: records without a name %,d, records without an id %,d, relations to no person %,d%n",
      withoutName, withoutId, relationsDropped));
    list(sb, "Sources disagree", conflicts);
    list(sb, "Authority ids claimed by two persons", ambiguous);
    list(sb, "In the files but in no source any more", notSeen);
    return sb.toString();
  }

  private static void list(StringBuilder sb, String title, List<String> lines) {
    sb.append(String.format("%n## %s: %,d%n", title, lines.size()));
    lines.stream().limit(LIST_LIMIT).forEach(l -> sb.append("  ").append(l).append('\n'));
  }
}
```

- [ ] **Step 4: Implement `PersonMerger`**

```java
package life.catalogue.matching.person.harvest;

import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.matching.person.*;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;

import javax.annotation.Nullable;

import static life.catalogue.matching.person.Provenance.*;

/**
 * Merges the records of a harvest into the registry files.
 * <ul>
 *   <li>Records and persons are joined on shared authority ids only, never on names.</li>
 *   <li>A value in the files is never overwritten and a line never removed, curated or not: the harvest only fills
 *   empty cells and adds lines.</li>
 *   <li>Where the records of one run disagree, IPNI wins for a person with an IPNI and no ZooBank id, ZooBank for one
 *   with a ZooBank and no IPNI id, Wikidata otherwise, and the disagreement is reported.</li>
 *   <li>Two persons with different ids of one authority are never merged.</li>
 * </ul>
 */
public class PersonMerger {
  private static final int YEAR_TOLERANCE = 2;
  private final MergeReport report = new MergeReport();

  public record Result(PersonFiles.Content content, MergeReport report) {
  }

  private static final class Draft {
    String id;
    String wikidata;
    String ipni;
    String zoobank;
    final List<String> formerIds = new ArrayList<>();
    String family;
    String given;
    String suffix;
    Integer born;
    Integer died;
    Integer activeFrom;
    Integer activeTo;
    final Set<TaxGroup> groups = EnumSet.noneOf(TaxGroup.class);
    Provenance source;
    boolean existing;
    final List<PersonRecord> records = new ArrayList<>();

    static Draft of(Person p) {
      Draft d = new Draft();
      d.id = p.id();
      d.wikidata = p.wikidata();
      d.ipni = p.ipni();
      d.zoobank = p.zoobank();
      d.formerIds.addAll(p.formerIds());
      d.family = p.family();
      d.given = p.given();
      d.suffix = p.suffix();
      d.born = p.born();
      d.died = p.died();
      d.activeFrom = p.activeFrom();
      d.activeTo = p.activeTo();
      d.groups.addAll(p.groups());
      d.source = p.source();
      d.existing = true;
      return d;
    }

    boolean local() {
      return id != null && id.startsWith(Person.LOCAL);
    }

    Set<String> authorityIds() {
      Set<String> ids = new LinkedHashSet<>();
      if (wikidata != null) ids.add(Person.WIKIDATA + wikidata);
      if (ipni != null) ids.add(Person.IPNI + ipni);
      if (zoobank != null) ids.add(Person.ZOOBANK + zoobank);
      return ids;
    }

    Person toPerson() {
      return new Person(id, wikidata, ipni, zoobank, List.copyOf(formerIds), family, given, suffix, born, died, activeFrom,
        activeTo, Set.copyOf(groups), source);
    }
  }

  /**
   * @param wikidataRedirects Wikidata items of the files that became a redirect, old Q-id to new Q-id
   */
  public Result merge(PersonFiles.Content existing, List<PersonRecord> records, Map<String, String> wikidataRedirects) {
    List<Draft> drafts = new ArrayList<>();
    Map<String, Draft> index = new HashMap<>();
    for (Person p : existing.persons()) {
      Draft d = Draft.of(p);
      String moved = d.wikidata == null ? null : wikidataRedirects.get(d.wikidata);
      if (moved != null) {
        d.wikidata = moved;
        report.redirected++;
      }
      drafts.add(d);
      d.authorityIds().forEach(id -> index.put(id, d));
    }
    report.existing = drafts.size();

    // Wikidata records carry the links between the authorities, so they go first
    List<PersonRecord> ordered = new ArrayList<>(records);
    ordered.sort(Comparator.comparingInt(r -> r.source() == WIKIDATA ? 0 : 1));
    for (PersonRecord r : ordered) {
      if (r.ids().isEmpty()) {
        report.withoutId++;
        continue;
      }
      attach(r, drafts, index).records.add(r);
    }

    Map<String, String> rekeyed = assignIds(drafts);
    for (Draft d : drafts) {
      fill(d);
    }
    List<PersonName> names = names(existing, drafts, rekeyed);
    Set<String> named = new HashSet<>();
    names.forEach(n -> named.add(rekeyed.getOrDefault(n.person(), n.person())));
    List<Draft> kept = new ArrayList<>();
    for (Draft d : drafts) {
      if (named.contains(d.id) || d.existing) {
        kept.add(d);
        if (!d.existing) report.added++;
      } else {
        report.withoutName++;
      }
    }
    List<PersonRelation> relations = relations(existing, kept, rekeyed);
    return new Result(new PersonFiles.Content(kept.stream().map(Draft::toPerson).toList(), names, relations), report);
  }

  private Draft attach(PersonRecord r, List<Draft> drafts, Map<String, Draft> index) {
    List<Draft> matches = new ArrayList<>();
    for (String id : r.ids()) {
      Draft d = index.get(id);
      if (d == null || matches.contains(d)) continue;
      if (compatible(d, r)) {
        matches.add(d);
      } else {
        // another item of the same authority claims this id: two persons until somebody merges them upstream
        report.ambiguous.add(describe(d) + " and a " + r.source().value() + " record of " + String.join(", ", r.ids())
          + " share " + id);
      }
    }
    Draft d;
    if (matches.isEmpty()) {
      d = new Draft();
      drafts.add(d);
    } else {
      d = matches.get(0);
      for (Draft other : matches.subList(1, matches.size())) {
        if (compatible(d, other)) {
          absorb(d, other, drafts, index);
        } else {
          report.ambiguous.add(describe(other) + " and " + describe(d) + " are both linked by a " + r.source().value()
            + " record of " + String.join(", ", r.ids()));
        }
      }
    }
    link(d, r, index);
    return d;
  }

  private static boolean compatible(Draft a, Draft b) {
    return !differs(a.wikidata, b.wikidata) && !differs(a.ipni, b.ipni) && !differs(a.zoobank, b.zoobank);
  }

  private static boolean compatible(Draft d, PersonRecord r) {
    return !differs(d.wikidata, r.wikidata()) && !differs(d.ipni, r.ipni()) && !differs(d.zoobank, r.zoobank());
  }

  private static boolean differs(@Nullable String a, @Nullable String b) {
    return a != null && b != null && !a.equals(b);
  }

  private static String describe(Draft d) {
    return d.id != null ? d.id : String.join("/", d.authorityIds());
  }

  /** the other draft is the same person: its ids, values and records move over */
  private void absorb(Draft d, Draft other, List<Draft> drafts, Map<String, Draft> index) {
    if (d.wikidata == null) d.wikidata = other.wikidata;
    if (d.ipni == null) d.ipni = other.ipni;
    if (d.zoobank == null) d.zoobank = other.zoobank;
    if (other.id != null) d.formerIds.add(other.id);
    d.formerIds.addAll(other.formerIds);
    if (d.family == null) d.family = other.family;
    if (d.given == null) d.given = other.given;
    if (d.suffix == null) d.suffix = other.suffix;
    if (d.born == null) d.born = other.born;
    if (d.died == null) d.died = other.died;
    if (d.activeFrom == null) d.activeFrom = other.activeFrom;
    if (d.activeTo == null) d.activeTo = other.activeTo;
    if (d.groups.isEmpty()) d.groups.addAll(other.groups);
    if (d.source == null || (!d.existing && other.existing)) d.source = other.source;
    d.existing |= other.existing;
    d.records.addAll(other.records);
    drafts.remove(other);
    d.authorityIds().forEach(id -> index.put(id, d));
    report.merged++;
  }

  /** the draft takes the ids of the record it lacks, unless another person holds them */
  private void link(Draft d, PersonRecord r, Map<String, Draft> index) {
    d.wikidata = linkId(d, d.wikidata, r.wikidata(), Person.WIKIDATA, index);
    d.ipni = linkId(d, d.ipni, r.ipni(), Person.IPNI, index);
    d.zoobank = linkId(d, d.zoobank, r.zoobank(), Person.ZOOBANK, index);
  }

  private String linkId(Draft d, @Nullable String current, @Nullable String value, String prefix, Map<String, Draft> index) {
    if (value == null) return current;
    if (current != null) {
      if (!current.equals(value)) {
        report.ambiguous.add(describe(d) + " has " + prefix + current + " while a record gives " + prefix + value);
      }
      return current;
    }
    Draft holder = index.get(prefix + value);
    if (holder != null && holder != d) {
      return null;
    }
    index.put(prefix + value, d);
    return value;
  }

  /**
   * @return old id to new id of every draft whose id changed
   */
  private static Map<String, String> assignIds(List<Draft> drafts) {
    Map<String, String> rekeyed = new HashMap<>();
    for (Draft d : drafts) {
      if (d.local()) continue;
      String id = Person.idFor(d.wikidata, d.ipni, d.zoobank);
      if (d.id != null && !d.id.equals(id) && !d.formerIds.contains(d.id)) {
        d.formerIds.add(d.id);
      }
      d.id = id;
      d.formerIds.remove(id);
      for (String former : d.formerIds) {
        rekeyed.put(former, id);
      }
    }
    return rekeyed;
  }

  private static int rank(Draft d, Provenance p) {
    List<Provenance> order = d.ipni != null && d.zoobank == null ? List.of(IPNI, WIKIDATA, ZOOBANK)
      : d.zoobank != null && d.ipni == null ? List.of(ZOOBANK, WIKIDATA, IPNI)
      : List.of(WIKIDATA, IPNI, ZOOBANK);
    int i = order.indexOf(p);
    return i < 0 ? order.size() : i;
  }

  private void fill(Draft d) {
    if (d.records.isEmpty()) {
      if (d.existing && !d.local() && d.source != CURATED) {
        report.notSeen.add(d.id);
      }
      return;
    }
    List<PersonRecord> recs = new ArrayList<>(d.records);
    recs.sort(Comparator.comparingInt(r -> rank(d, r.source())));
    BiPredicate<String, String> sameText = String::equalsIgnoreCase;
    BiPredicate<Integer, Integer> sameYear = (a, b) -> Math.abs(a - b) <= YEAR_TOLERANCE;
    d.family = fill(d, "family", d.family, recs, PersonRecord::family, sameText);
    d.given = fill(d, "given", d.given, recs, PersonRecord::given, sameText);
    d.suffix = fill(d, "suffix", d.suffix, recs, PersonRecord::suffix, sameText);
    d.born = fill(d, "born", d.born, recs, PersonRecord::born, sameYear);
    d.died = fill(d, "died", d.died, recs, PersonRecord::died, sameYear);
    d.activeFrom = fill(d, "activeFrom", d.activeFrom, recs, PersonRecord::activeFrom, sameYear);
    d.activeTo = fill(d, "activeTo", d.activeTo, recs, PersonRecord::activeTo, sameYear);
    if (d.groups.isEmpty()) {
      recs.forEach(r -> d.groups.addAll(r.groups()));
    }
    if (d.source == null) {
      d.source = recs.get(0).source();
    }
  }

  private <T> T fill(Draft d, String field, @Nullable T current, List<PersonRecord> recs, Function<PersonRecord, T> getter,
                     BiPredicate<T, T> same) {
    T chosen = null;
    Provenance from = null;
    for (PersonRecord r : recs) {
      T v = getter.apply(r);
      if (v == null) continue;
      if (chosen == null) {
        chosen = v;
        from = r.source();
      } else if (!same.test(chosen, v)) {
        report.conflicts.add(d.id + " " + field + ": " + from.value() + " " + chosen + ", " + r.source().value() + " " + v);
      }
    }
    return current != null ? current : chosen;
  }

  private static List<PersonName> names(PersonFiles.Content existing, List<Draft> drafts, Map<String, String> rekeyed) {
    List<PersonName> names = new ArrayList<>();
    Set<List<Object>> seen = new HashSet<>();
    for (PersonName n : existing.names()) {
      String resolved = rekeyed.getOrDefault(n.person(), n.person());
      PersonName m = n.source() == CURATED ? n : new PersonName(resolved, n.form(), n.kind(), n.code(), n.source());
      if (seen.add(List.of(resolved, n.form(), n.kind(), n.code()))) {
        names.add(m);
      }
    }
    for (Draft d : drafts) {
      for (PersonRecord r : d.records) {
        for (PersonRecord.Form f : r.names()) {
          if (seen.add(List.of(d.id, f.form(), f.kind(), f.code()))) {
            names.add(new PersonName(d.id, f.form(), f.kind(), f.code(), r.source()));
          }
        }
      }
    }
    return names;
  }

  private List<PersonRelation> relations(PersonFiles.Content existing, List<Draft> kept, Map<String, String> rekeyed) {
    Map<String, Draft> byAnyId = new HashMap<>();
    for (Draft d : kept) {
      byAnyId.put(d.id, d);
      d.formerIds.forEach(id -> byAnyId.put(id, d));
      d.authorityIds().forEach(id -> byAnyId.put(id, d));
    }
    List<PersonRelation> relations = new ArrayList<>();
    Set<List<Object>> seen = new HashSet<>();
    for (PersonRelation r : existing.relations()) {
      String a = rekeyed.getOrDefault(r.person(), r.person());
      String b = rekeyed.getOrDefault(r.other(), r.other());
      if (seen.add(key(a, r.relation(), b))) {
        relations.add(r.source() == CURATED ? r : new PersonRelation(a, r.relation(), b, r.source()));
      }
    }
    for (Draft d : kept) {
      for (PersonRecord r : d.records) {
        for (PersonRecord.Link l : r.relations()) {
          Draft other = byAnyId.get(l.other());
          if (other == null || other == d) {
            report.relationsDropped++;
            continue;
          }
          boolean fresh = seen.add(key(d.id, l.relation(), other.id));
          if (l.relation() == RelationType.SIBLING) {
            fresh &= seen.add(key(other.id, l.relation(), d.id));
          }
          if (fresh) {
            relations.add(new PersonRelation(d.id, l.relation(), other.id, r.source()));
          }
        }
      }
    }
    return relations;
  }

  private static List<Object> key(String a, RelationType t, String b) {
    return List.of(a, t, b);
  }
}
```

The `relations` test expects the sibling pair once and the link to `wd:Q404` dropped. Q3's sibling link to Q2 is
processed after Q2's link to Q3 was added: `seen` holds both directions, so it is not added again.

- [ ] **Step 5: Run the test**

Run: `mvn -o -pl core test -Dtest=PersonMergerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 10 tests pass. Debug a failure against the merge rule its test comment names: the rule is fixed, the code is
not. The expected order of the `relations` list is the order of creation, since `PersonFiles.write` only sorts on writing.

- [ ] **Step 6: Commit**

```bash
git add core/src/test/java/life/catalogue/matching/person/harvest/
git commit -m "test(authorship): merge harvested persons into the registry files

Joined on authority ids only, filling empty cells and adding lines, never
overwriting a value or removing a line. Disagreements of one run go to IPNI
for IPNI authors, ZooBank for ZooBank ones and Wikidata otherwise, and to
the report. A person that gains a better id keeps the old one as former id.

Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg"
```

---

### Task 6: WikidataPersonSource

**Files:**
- Create: `core/src/test/java/life/catalogue/matching/person/harvest/WikidataPersonSource.java`
- Test: `core/src/test/java/life/catalogue/matching/person/harvest/WikidataPersonSourceTest.java`

**Interfaces:**
- Consumes: `Fetcher`, `PersonRecord.Builder`, `Years.wikidata` and `Groups.field`
- Produces: `new WikidataPersonSource(Fetcher)`, `read()`, `Map<String, String> redirects(Collection<String> qids)`,
  `stats()`, and the pure `static int addIds(JsonNode, IdProperty, Map<String, PersonRecord.Builder>)` and
  `void addFacts(JsonNode, Map<String, PersonRecord.Builder>)` used by the test

- [ ] **Step 1: Write the failing test**

```java
package life.catalogue.matching.person.harvest;

import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.matching.person.FormCode;
import life.catalogue.matching.person.NameKind;
import life.catalogue.matching.person.RelationType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.*;

public class WikidataPersonSourceTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  static JsonNode rows(String... rows) throws Exception {
    return MAPPER.readTree("{\"results\":{\"bindings\":[" + String.join(",", rows) + "]}}");
  }

  static String id(String q, String v) {
    return "{\"person\":{\"type\":\"uri\",\"value\":\"http://www.wikidata.org/entity/" + q + "\"},\"v\":{\"type\":\"literal\",\"value\":\"" + v + "\"}}";
  }

  static String fact(String q, String p, String v) {
    return "{\"person\":{\"type\":\"uri\",\"value\":\"http://www.wikidata.org/entity/" + q + "\"},\"p\":{\"type\":\"literal\",\"value\":\""
      + p + "\"},\"v\":{\"type\":\"literal\",\"value\":\"" + v + "\"}}";
  }

  @Test
  public void idsAndFacts() throws Exception {
    Map<String, PersonRecord.Builder> persons = new TreeMap<>();
    assertEquals(1, WikidataPersonSource.addIds(rows(id("Q2", "9936-1")), WikidataPersonSource.IdProperty.P586, persons));
    WikidataPersonSource.addIds(rows(id("Q2", "G.B.Sowerby II")), WikidataPersonSource.IdProperty.P835, persons);
    WikidataPersonSource.addIds(rows(id("Q2", "abc-def")), WikidataPersonSource.IdProperty.P2006, persons);
    var source = new WikidataPersonSource(url -> {
      throw new AssertionError("no network");
    });
    source.addFacts(rows(
      fact("Q2", "label", "George Brettingham Sowerby II"),
      fact("Q2", "alias", "G. B. Sowerby"),
      fact("Q2", "family", "Sowerby"),
      fact("Q2", "given", "Brettingham"),
      fact("Q2", "given", "George"),
      fact("Q2", "born", "+1812-08-12T00:00:00Z"),
      fact("Q2", "died", "1884-07-26T00:00:00Z"),
      fact("Q2", "field", "malacology"),
      fact("Q2", "field", "politics"),
      fact("Q2", "parent", "http://www.wikidata.org/entity/Q1"),
      fact("Q2", "sibling", "http://www.wikidata.org/entity/Q3")
    ), persons);
    PersonRecord r = persons.get("Q2").build();
    assertEquals("Q2", r.wikidata());
    assertEquals("9936-1", r.ipni());
    assertEquals("ABC-DEF", r.zoobank());
    assertEquals("Sowerby", r.family());
    assertEquals("George Brettingham", r.given());
    assertEquals("II", r.suffix());
    assertEquals(Integer.valueOf(1812), r.born());
    assertEquals(Integer.valueOf(1884), r.died());
    assertEquals(Set.of(TaxGroup.Molluscs), r.groups());
    assertTrue(r.names().contains(new PersonRecord.Form("G.B.Sowerby II", NameKind.CITATION, FormCode.ZOO)));
    assertTrue(r.names().contains(new PersonRecord.Form("George Brettingham Sowerby II", NameKind.FULL, FormCode.ANY)));
    assertTrue(r.names().contains(new PersonRecord.Form("G. B. Sowerby", NameKind.VARIANT, FormCode.ANY)));
    assertEquals(List.of(new PersonRecord.Link(RelationType.PARENT, "wd:Q1"), new PersonRecord.Link(RelationType.SIBLING, "wd:Q3")),
      r.relations());
    assertTrue(source.stats(), source.stats().contains("politics"));
  }

  @Test
  public void standardFormIsBotanical() throws Exception {
    Map<String, PersonRecord.Builder> persons = new TreeMap<>();
    WikidataPersonSource.addIds(rows(id("Q5", "Sw.")), WikidataPersonSource.IdProperty.P428, persons);
    assertEquals(List.of(new PersonRecord.Form("Sw.", NameKind.STANDARD, FormCode.BOT)), persons.get("Q5").build().names());
  }

  /** read() pages the id queries and batches the facts, all through the fetcher */
  @Test
  public void read() throws Exception {
    var source = new WikidataPersonSource(url -> {
      String q = java.net.URLDecoder.decode(url, java.nio.charset.StandardCharsets.UTF_8);
      if (q.contains("VALUES ?person")) return "{\"results\":{\"bindings\":[" + fact("Q9", "label", "Olof Swartz") + "]}}";
      if (q.contains("wdt:P428") && q.contains("OFFSET 0")) return "{\"results\":{\"bindings\":[" + id("Q9", "Sw.") + "]}}";
      return "{\"results\":{\"bindings\":[]}}";
    });
    List<PersonRecord> records = source.read();
    assertEquals(1, records.size());
    assertEquals("Q9", records.get(0).wikidata());
    assertEquals(2, records.get(0).names().size());
  }

  @Test
  public void redirects() throws Exception {
    var source = new WikidataPersonSource(url -> "{\"results\":{\"bindings\":[{\"old\":{\"type\":\"uri\",\"value\":\"http://www.wikidata.org/entity/Q1\"},"
      + "\"new\":{\"type\":\"uri\",\"value\":\"http://www.wikidata.org/entity/Q2\"}}]}}");
    assertEquals(Map.of("Q1", "Q2"), source.redirects(List.of("Q1")));
    assertEquals(Map.of(), source.redirects(List.of()));
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -o -pl core test -Dtest=WikidataPersonSourceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement**

```java
package life.catalogue.matching.person.harvest;

import life.catalogue.matching.person.FormCode;
import life.catalogue.matching.person.NameKind;
import life.catalogue.matching.person.Person;
import life.catalogue.matching.person.Provenance;
import life.catalogue.matching.person.RelationType;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Persons from Wikidata: everyone with a botanist author abbreviation (P428), a zoologist author citation (P835), an
 * IPNI author id (P586) or a ZooBank author id (P2006). The ids are paged one property at a time. The facts of the
 * persons are then asked for in batches of items, one UNION per fact so that multi valued facts never multiply rows:
 * English label and aliases, family and given names (P734, P735), birth and death (P569, P570), active years (P2031,
 * P2032, P1317), field of work (P101), parents (P22, P25) and siblings (P3373).
 */
public class WikidataPersonSource implements PersonSource {
  static final String ENDPOINT = "https://query.wikidata.org/sparql";
  static final String ENTITY = "http://www.wikidata.org/entity/";
  static final int PAGE = 5000;
  static final int BATCH = 400;
  // Wikidata labels occasionally hold raw control characters that strict JSON rejects
  private static final ObjectMapper MAPPER = JsonMapper.builder().enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS).build();

  enum IdProperty {
    P428, P835, P586, P2006
  }

  private final Fetcher fetcher;
  private final Map<String, Integer> unmappedFields = new TreeMap<>();
  private int persons;

  public WikidataPersonSource(Fetcher fetcher) {
    this.fetcher = fetcher;
  }

  @Override
  public String name() {
    return "wikidata";
  }

  @Override
  public List<PersonRecord> read() throws Exception {
    Map<String, PersonRecord.Builder> builders = new TreeMap<>();
    for (IdProperty p : IdProperty.values()) {
      for (int offset = 0; ; offset += PAGE) {
        int rows = addIds(query("SELECT ?person ?v WHERE { ?person wdt:" + p + " ?v } ORDER BY ?person ?v LIMIT " + PAGE
          + " OFFSET " + offset), p, builders);
        System.out.printf("  wikidata %s offset %d: %d rows%n", p, offset, rows);
        if (rows < PAGE) break;
      }
    }
    List<String> qids = new ArrayList<>(builders.keySet());
    for (int i = 0; i < qids.size(); i += BATCH) {
      addFacts(query(factQuery(qids.subList(i, Math.min(i + BATCH, qids.size())))), builders);
      if ((i / BATCH) % 20 == 0) {
        System.out.printf("  wikidata facts %d of %d persons%n", i, qids.size());
      }
    }
    persons = builders.size();
    return builders.values().stream().map(PersonRecord.Builder::build).toList();
  }

  static String factQuery(List<String> qids) {
    String values = qids.stream().map(q -> "wd:" + q).collect(Collectors.joining(" "));
    return "SELECT ?person ?p ?v WHERE { VALUES ?person { " + values + " }"
      + " { ?person rdfs:label ?v FILTER(LANG(?v) = \"en\") BIND(\"label\" AS ?p) }"
      + " UNION { ?person skos:altLabel ?v FILTER(LANG(?v) = \"en\") BIND(\"alias\" AS ?p) }"
      + " UNION { ?person wdt:P734 ?x . ?x rdfs:label ?v FILTER(LANG(?v) = \"en\") BIND(\"family\" AS ?p) }"
      + " UNION { ?person wdt:P735 ?x . ?x rdfs:label ?v FILTER(LANG(?v) = \"en\") BIND(\"given\" AS ?p) }"
      + " UNION { ?person wdt:P569 ?v BIND(\"born\" AS ?p) }"
      + " UNION { ?person wdt:P570 ?v BIND(\"died\" AS ?p) }"
      + " UNION { ?person wdt:P2031 ?v BIND(\"activeFrom\" AS ?p) }"
      + " UNION { ?person wdt:P2032 ?v BIND(\"activeTo\" AS ?p) }"
      + " UNION { ?person wdt:P1317 ?v BIND(\"floruit\" AS ?p) }"
      + " UNION { ?person wdt:P101 ?x . ?x rdfs:label ?v FILTER(LANG(?v) = \"en\") BIND(\"field\" AS ?p) }"
      + " UNION { ?person wdt:P22 ?v BIND(\"parent\" AS ?p) }"
      + " UNION { ?person wdt:P25 ?v BIND(\"parent\" AS ?p) }"
      + " UNION { ?person wdt:P3373 ?v BIND(\"sibling\" AS ?p) }"
      + " }";
  }

  /**
   * @return the number of rows, for paging
   */
  static int addIds(JsonNode json, IdProperty p, Map<String, PersonRecord.Builder> builders) {
    int rows = 0;
    for (JsonNode b : json.path("results").path("bindings")) {
      rows++;
      String q = qid(text(b, "person"));
      String v = text(b, "v");
      if (q == null || v == null) continue;
      PersonRecord.Builder pb = builders.computeIfAbsent(q, k -> {
        var x = new PersonRecord.Builder(Provenance.WIKIDATA);
        x.wikidata = k;
        return x;
      });
      switch (p) {
        case P428 -> pb.name(v, NameKind.STANDARD, FormCode.BOT);
        case P835 -> pb.name(v, NameKind.CITATION, FormCode.ZOO);
        // an item with several IPNI or ZooBank ids keeps the first in id order, deterministically
        case P586 -> pb.ipni = pb.ipni == null ? v : pb.ipni;
        case P2006 -> pb.zoobank = pb.zoobank == null ? v.toUpperCase() : pb.zoobank;
      }
    }
    return rows;
  }

  void addFacts(JsonNode json, Map<String, PersonRecord.Builder> builders) {
    for (JsonNode b : json.path("results").path("bindings")) {
      PersonRecord.Builder pb = builders.get(qid(text(b, "person")));
      String p = text(b, "p");
      String v = text(b, "v");
      if (pb == null || p == null || v == null) continue;
      switch (p) {
        case "label" -> pb.label(v);
        case "alias" -> pb.name(v, NameKind.VARIANT, FormCode.ANY);
        case "family" -> pb.family(v);
        case "given" -> pb.given(v);
        case "born" -> pb.born(Years.wikidata(v));
        case "died" -> pb.died(Years.wikidata(v));
        case "activeFrom" -> pb.activeFrom(Years.wikidata(v));
        case "activeTo" -> pb.activeTo(Years.wikidata(v));
        case "floruit" -> {
          pb.activeFrom(Years.wikidata(v));
          pb.activeTo(Years.wikidata(v));
        }
        case "field" -> {
          var groups = Groups.field(v);
          if (groups.isEmpty()) {
            unmappedFields.merge(v.toLowerCase(), 1, Integer::sum);
          }
          pb.groups.addAll(groups);
        }
        case "parent" -> link(pb, RelationType.PARENT, v);
        case "sibling" -> link(pb, RelationType.SIBLING, v);
        default -> {
        }
      }
    }
  }

  private static void link(PersonRecord.Builder pb, RelationType type, String iri) {
    String q = qid(iri);
    if (q != null) {
      pb.link(type, Person.WIKIDATA + q);
    }
  }

  /**
   * @return old Q-id to the Q-id it redirects to, for the items given that became a redirect
   */
  public Map<String, String> redirects(Collection<String> qids) throws Exception {
    Map<String, String> redirects = new HashMap<>();
    List<String> list = new ArrayList<>(qids);
    for (int i = 0; i < list.size(); i += BATCH) {
      String values = list.subList(i, Math.min(i + BATCH, list.size())).stream().map(q -> "wd:" + q).collect(Collectors.joining(" "));
      for (JsonNode b : query("SELECT ?old ?new WHERE { VALUES ?old { " + values + " } ?old owl:sameAs ?new }")
        .path("results").path("bindings")) {
        String old = qid(text(b, "old"));
        String now = qid(text(b, "new"));
        if (old != null && now != null) {
          redirects.put(old, now);
        }
      }
    }
    return redirects;
  }

  private JsonNode query(String sparql) throws Exception {
    return MAPPER.readTree(fetcher.get(ENDPOINT + "?format=json&query=" + URLEncoder.encode(sparql, StandardCharsets.UTF_8)));
  }

  private static String qid(String iri) {
    return iri != null && iri.startsWith(ENTITY) ? iri.substring(ENTITY.length()) : null;
  }

  private static String text(JsonNode b, String field) {
    JsonNode n = b.path(field).path("value");
    return n.isMissingNode() ? null : n.asText();
  }

  @Override
  public String stats() {
    StringBuilder sb = new StringBuilder(String.format("wikidata: %,d persons%n", persons));
    sb.append("fields of work mapped to no group, by persons:\n");
    unmappedFields.entrySet().stream()
      .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
      .limit(50)
      .forEach(e -> sb.append(String.format("  %6d  %s%n", e.getValue(), e.getKey())));
    return sb.toString();
  }
}
```

- [ ] **Step 4: Run the test**

Run: `mvn -o -pl core test -Dtest=WikidataPersonSourceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add core/src/test/java/life/catalogue/matching/person/harvest/
git commit -m "test(authorship): harvest persons from Wikidata

Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg"
```

---

### Task 7: IpniPersonSource

**Files:**
- Create: `core/src/test/java/life/catalogue/matching/person/harvest/IpniPersonSource.java`
- Test: `core/src/test/java/life/catalogue/matching/person/harvest/IpniPersonSourceTest.java`

**Interfaces:**
- Consumes: `Fetcher`, `PersonRecord.Builder`, `Years.ipni`, `Groups.ipni` and `Names.surnameFirst`
- Produces: `new IpniPersonSource(Fetcher)`, `read()`, `stats()` and `@Nullable PersonRecord parse(JsonNode author)`

- [ ] **Step 1: Write the failing test**

```java
package life.catalogue.matching.person.harvest;

import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.matching.person.FormCode;
import life.catalogue.matching.person.NameKind;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.*;

public class IpniPersonSourceTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  static String author(String id, String std, String forename, String surname, String dates, String groups, String alt) {
    return String.format("{\"id\":\"%s\",\"standardForm\":\"%s\",\"forename\":\"%s\",\"surname\":\"%s\",\"dates\":\"%s\","
      + "\"taxonGroups\":\"%s\",\"alternativeNames\":\"%s\",\"suppressed\":false}", id, std, forename, surname, dates, groups, alt);
  }

  static String page(int total, String cursor, String... authors) {
    return "{\"totalResults\":" + total + ",\"cursor\":" + (cursor == null ? "null" : "\"" + cursor + "\"")
      + ",\"results\":[" + String.join(",", authors) + "]}";
  }

  @Test
  public void parse() throws Exception {
    var source = new IpniPersonSource(url -> "");
    PersonRecord r = source.parse(MAPPER.readTree(author("9934-1", "J.C.Sowerby", "James de Carle", "Sowerby", "1787-1871",
      "Mycology, Algae, Fossils", "Sowerby, James DeCarle")));
    assertEquals("9934-1", r.ipni());
    assertEquals("Sowerby", r.family());
    assertEquals("James de Carle", r.given());
    assertNull(r.suffix());
    assertEquals(Integer.valueOf(1787), r.born());
    assertEquals(Integer.valueOf(1871), r.died());
    assertEquals(Set.of(TaxGroup.Fungi, TaxGroup.Algae), r.groups());
    assertEquals(List.of(new PersonRecord.Form("J.C.Sowerby", NameKind.STANDARD, FormCode.BOT),
      new PersonRecord.Form("James de Carle Sowerby", NameKind.FULL, FormCode.ANY),
      new PersonRecord.Form("James DeCarle Sowerby", NameKind.VARIANT, FormCode.ANY)), r.names());
    assertEquals("f.", source.parse(MAPPER.readTree(author("4084-1", "Hook.f.", "Joseph Dalton", "Hooker", "1817-1911", "", ""))).suffix());
    assertNull(source.parse(MAPPER.readTree("{\"id\":\"1-1\",\"suppressed\":true,\"standardForm\":\"X\"}")));
  }

  /** a prefix with more than 10,000 authors is split into longer prefixes instead of being cut off */
  @Test
  public void splitsBigPrefixes() throws Exception {
    var source = new IpniPersonSource(url -> {
      String q = URLDecoder.decode(url, StandardCharsets.UTF_8);
      if (q.contains("surname:*")) return page(3, null);
      if (q.contains("surname:S*")) return page(12000, "c1", author("1-1", "S.", "", "Sa", "", "", ""));
      if (q.contains("surname:Sa*") && q.contains("cursor=*")) return page(2, "c2", author("2-1", "Sa.", "", "Saa", "", "", ""));
      if (q.contains("surname:Sa*") && q.contains("cursor=c2")) return page(2, "c3", author("3-1", "Sab.", "", "Sab", "", "", ""));
      if (q.contains("surname:Sa*")) return page(2, "c4");
      return page(0, null);
    });
    List<PersonRecord> records = source.read();
    assertEquals(List.of("2-1", "3-1"), records.stream().map(PersonRecord::ipni).toList());
    assertTrue(source.stats(), source.stats().contains("IPNI finds 3 authors"));
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -o -pl core test -Dtest=IpniPersonSourceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement**

```java
package life.catalogue.matching.person.harvest;

import life.catalogue.matching.person.FormCode;
import life.catalogue.matching.person.NameKind;
import life.catalogue.matching.person.Provenance;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Persons from IPNI. There is no bulk download, but the author search pages with a cursor and stops after 10,000
 * records per query. So it is asked by surname prefix, A to Z, and a prefix with more authors is split into longer
 * prefixes. Authors whose surname starts with no letter A to Z are what the total of all authors shows missing.
 */
public class IpniPersonSource implements PersonSource {
  static final String ENDPOINT = "https://www.ipni.org/api/1/search";
  static final int PAGE = 500;
  static final int MAX_RECORDS = 10000;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final Fetcher fetcher;
  private final Map<String, Integer> unmappedGroups = new TreeMap<>();
  private final List<String> unparsedDates = new ArrayList<>();
  private int total;
  private int harvested;
  private int suppressed;

  public IpniPersonSource(Fetcher fetcher) {
    this.fetcher = fetcher;
  }

  @Override
  public String name() {
    return "ipni";
  }

  @Override
  public List<PersonRecord> read() throws Exception {
    total = page("", "*").path("totalResults").asInt();
    Map<String, PersonRecord> byId = new TreeMap<>();
    for (char c = 'A'; c <= 'Z'; c++) {
      harvest(String.valueOf(c), byId);
    }
    harvested = byId.size();
    return List.copyOf(byId.values());
  }

  private void harvest(String prefix, Map<String, PersonRecord> byId) throws Exception {
    JsonNode page = page(prefix, "*");
    if (page.path("totalResults").asInt() > MAX_RECORDS) {
      for (char c = 'a'; c <= 'z'; c++) {
        harvest(prefix + c, byId);
      }
      return;
    }
    System.out.printf("  ipni %s: %d authors%n", prefix, page.path("totalResults").asInt());
    while (true) {
      JsonNode results = page.path("results");
      for (JsonNode a : results) {
        PersonRecord r = parse(a);
        if (r != null) {
          byId.putIfAbsent(r.ipni(), r);
        }
      }
      String cursor = page.path("cursor").isTextual() ? page.path("cursor").asText() : null;
      if (cursor == null || results.size() == 0) break;
      page = page(prefix, cursor);
    }
  }

  private JsonNode page(String prefix, String cursor) throws Exception {
    String q = "author surname:" + prefix + "*";
    return MAPPER.readTree(fetcher.get(ENDPOINT + "?perPage=" + PAGE + "&f=f_authors&cursor="
      + URLEncoder.encode(cursor, StandardCharsets.UTF_8) + "&q=" + URLEncoder.encode(q, StandardCharsets.UTF_8)));
  }

  /**
   * @return the person of one IPNI author record, null for a suppressed record or one without id
   */
  @Nullable
  PersonRecord parse(JsonNode a) {
    String id = StringUtils.trimToNull(a.path("id").asText(null));
    if (id == null) return null;
    if (a.path("suppressed").asBoolean(false)) {
      suppressed++;
      return null;
    }
    var b = new PersonRecord.Builder(Provenance.IPNI);
    b.ipni = id;
    String std = StringUtils.trimToNull(a.path("standardForm").asText(null));
    String forename = StringUtils.trimToNull(a.path("forename").asText(null));
    String surname = StringUtils.trimToNull(a.path("surname").asText(null));
    b.name(std, NameKind.STANDARD, FormCode.BOT);
    if (surname != null) {
      b.name(forename == null ? surname : forename + " " + surname, NameKind.FULL, FormCode.ANY);
    }
    for (String alt : a.path("alternativeNames").asText("").split(";")) {
      b.name(Names.surnameFirst(alt), NameKind.VARIANT, FormCode.ANY);
    }
    b.family(surname);
    b.given(forename);
    if (std != null && std.matches(".*\\bf\\.$")) {
      b.suffix = "f.";
    }
    String dates = StringUtils.trimToNull(a.path("dates").asText(null));
    Years.Span span = Years.ipni(dates);
    if (dates != null && span.equals(Years.NONE) && unparsedDates.size() < 100) {
      unparsedDates.add(dates);
    }
    b.born(span.born());
    b.died(span.died());
    b.activeFrom(span.activeFrom());
    b.activeTo(span.activeTo());
    b.groups.addAll(Groups.ipni(a.path("taxonGroups").asText(null), g -> unmappedGroups.merge(g, 1, Integer::sum)));
    return b.build();
  }

  @Override
  public String stats() {
    return String.format("ipni: IPNI finds %,d authors, %,d harvested, %,d suppressed%n", total, harvested, suppressed)
      + "taxon groups mapped to nothing: " + unmappedGroups + "\n"
      + "dates not understood, the first 100: " + unparsedDates + "\n";
  }
}
```

- [ ] **Step 4: Run the test**

Run: `mvn -o -pl core test -Dtest=IpniPersonSourceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 2 tests pass. In `splitsBigPrefixes` only `S` has authors: 12,000 of them, so `S` is split and `Sa` pages
twice before an empty page. The fake answers every other prefix with none.

- [ ] **Step 5: Commit**

```bash
git add core/src/test/java/life/catalogue/matching/person/harvest/
git commit -m "test(authorship): harvest persons from IPNI

Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg"
```

---

### Task 8: The harvest run and the ZooBank placeholder

**Files:**
- Create: `core/src/test/java/life/catalogue/matching/person/harvest/ZooBankDumpSource.java`, `PersonHarvest.java`
- Test: `core/src/test/java/life/catalogue/matching/person/harvest/PersonHarvestTest.java`

**Interfaces:**
- Consumes: everything above, and the `api` resource `authorship/authormap.txt` through `Resources.tabRows`
- Produces: `PersonHarvest.main(<persons dir> <work dir> [--zoobank <dump>])` and
  `static String run(Path dir, List<PersonSource> sources, Function<Collection<String>, Map<String, String>> redirects)`,
  which returns the report text

- [ ] **Step 1: Write the failing test**

```java
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
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -o -pl core test -Dtest=PersonHarvestTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement `ZooBankDumpSource`**

```java
package life.catalogue.matching.person.harvest;

import java.nio.file.Path;
import java.util.List;

/**
 * Persons from a ZooBank author dump. ZooBank has no bulk access; author dumps have been requested, so this is a
 * placeholder whose reader is written once a dump and its format exist. What it maps to is fixed already:
 * the author UUID becomes the zb: id and the zoobank column; the names ZooBank cites the author by become
 * zoological CITATION forms and other name records of the author VARIANT forms; the lifespan becomes born and died;
 * a Wikidata, IPNI or ORCID link the dump carries is a join key. The merge side is tested with hand made records.
 */
public class ZooBankDumpSource implements PersonSource {
  private final Path dump;

  public ZooBankDumpSource(Path dump) {
    this.dump = dump;
  }

  @Override
  public String name() {
    return "zoobank";
  }

  @Override
  public List<PersonRecord> read() {
    throw new UnsupportedOperationException("No reader for ZooBank dumps yet, the format of " + dump
      + " is not known. Write ZooBankDumpSource.read() for it, see docs/AUTHOR-PERSONS.md");
  }
}
```

- [ ] **Step 4: Implement `PersonHarvest`**

```java
package life.catalogue.matching.person.harvest;

import life.catalogue.common.io.Resources;
import life.catalogue.matching.person.FormCode;
import life.catalogue.matching.person.PersonFiles;
import life.catalogue.matching.person.PersonRegistry;
import life.catalogue.matching.person.Provenance;

import org.gbif.nameparser.api.NomCode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;

/**
 * Grows the person registry from its authorities, run by hand. It reads the three files, reads every source, merges
 * by the rules of {@link PersonMerger}, and writes the files back only if the result is consistent. The review report
 * holds what the merge could not decide, what the sources could not map and the author map rows no person resolves -
 * candidates for curated lines. Every answer is cached in the work dir, so a rerun resumes.
 * <pre>
 * mvn -q -pl core test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
 *   -Dexec.args="-Xmx4g -cp %classpath life.catalogue.matching.person.harvest.PersonHarvest \
 *     src/main/resources/authorship/persons target/person-harvest"
 * </pre>
 */
public class PersonHarvest {
  private static final String AUTHOR_MAP = "authorship/authormap.txt";
  private static final int UNRESOLVED_LIMIT = 500;

  public static void main(String[] args) throws Exception {
    if (args.length != 2 && !(args.length == 4 && args[2].equals("--zoobank"))) {
      System.err.println("Usage: PersonHarvest <persons dir> <work dir> [--zoobank <dump>]");
      System.exit(1);
    }
    Path dir = Path.of(args[0]);
    Path work = Path.of(args[1]);
    var wikidata = new WikidataPersonSource(new CachingFetcher(work.resolve("cache/wikidata"),
      new HttpFetcher("application/sparql-results+json", Duration.ofSeconds(1))));
    var ipni = new IpniPersonSource(new CachingFetcher(work.resolve("cache/ipni"),
      new HttpFetcher("application/json", Duration.ofMillis(250))));
    List<PersonSource> sources = new ArrayList<>(List.of(wikidata, ipni));
    if (args.length == 4) {
      sources.add(new ZooBankDumpSource(Path.of(args[3])));
    }
    String report = run(dir, sources, qids -> {
      try {
        return wikidata.redirects(qids);
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    });
    Files.createDirectories(work);
    Files.writeString(work.resolve("report.txt"), report, StandardCharsets.UTF_8);
    System.out.println(report.lines().limit(40).reduce("", (x, y) -> x + y + "\n"));
    System.out.println("Full report in " + work.resolve("report.txt"));
  }

  /**
   * @return the review report
   */
  static String run(Path dir, List<PersonSource> sources, Function<Collection<String>, Map<String, String>> redirects) throws Exception {
    PersonFiles.Content existing = PersonFiles.read(dir);
    List<PersonRecord> records = new ArrayList<>();
    StringBuilder stats = new StringBuilder();
    for (PersonSource s : sources) {
      List<PersonRecord> read = s.read();
      System.out.printf("%s: %,d records%n", s.name(), read.size());
      records.addAll(read);
      stats.append(s.stats());
    }
    Set<String> seen = new HashSet<>();
    records.forEach(r -> {
      if (r.wikidata() != null) seen.add(r.wikidata());
    });
    List<String> gone = existing.persons().stream()
      .map(p -> p.wikidata())
      .filter(q -> q != null && !seen.contains(q))
      .toList();
    var result = new PersonMerger().merge(existing, records, gone.isEmpty() ? Map.of() : redirects.apply(gone));
    var registry = new PersonRegistry(result.content());
    if (!registry.problems().isEmpty()) {
      throw new IllegalStateException("The merge is inconsistent, nothing written: " + registry.problems().subList(0,
        Math.min(20, registry.problems().size())));
    }
    PersonFiles.write(dir, result.content());

    StringBuilder sb = new StringBuilder("# Person harvest\n\n");
    sb.append(String.format("persons %,d, names %,d, relations %,d%n", result.content().persons().size(),
      result.content().names().size(), result.content().relations().size()));
    sb.append(result.report().render()).append('\n').append(stats);
    unresolvedAuthorMapRows(registry, sb);
    return sb.toString();
  }

  /**
   * Author map rows none of whose forms name a person: hand edits worth keeping become curated lines.
   */
  private static void unresolvedAuthorMapRows(PersonRegistry registry, StringBuilder sb) {
    List<String> unresolved = new ArrayList<>();
    int rows = 0;
    for (String[] row : (Iterable<String[]>) Resources.tabRows(AUTHOR_MAP)::iterator) {
      if (row.length < 3) continue;
      rows++;
      FormCode code = FormCode.valueOf(row[1].trim().toUpperCase());
      List<NomCode> codes = switch (code) {
        case BOT -> List.of(NomCode.BOTANICAL);
        case ZOO -> List.of(NomCode.ZOOLOGICAL);
        case ANY -> List.of(NomCode.BOTANICAL, NomCode.ZOOLOGICAL);
      };
      boolean found = false;
      for (int i = 0; i < row.length && !found; i++) {
        if (i == 1) continue;
        for (NomCode c : codes) {
          if (!registry.candidates(row[i], c).isEmpty()) {
            found = true;
            break;
          }
        }
      }
      if (!found) {
        unresolved.add(String.join("\t", row));
      }
    }
    sb.append(String.format("%n## Author map rows no person resolves: %,d of %,d%n", unresolved.size(), rows));
    unresolved.stream().limit(UNRESOLVED_LIMIT).forEach(r -> sb.append("  ").append(r).append('\n'));
  }
}
```

- [ ] **Step 5: Run the test**

Run: `mvn -o -pl core test -Dtest=PersonHarvestTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 2 tests pass.

- [ ] **Step 6: Run the whole person package**

Run: `mvn -o -pl core test -Dtest='life.catalogue.matching.person.**' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: all pass.

- [ ] **Step 7: Commit**

```bash
git add core/src/test/java/life/catalogue/matching/person/harvest/
git commit -m "test(authorship): the person harvest run, with a placeholder for ZooBank dumps

Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg"
```

---

### Task 9: The first harvest

**Files:**
- Modify: `core/src/main/resources/authorship/persons/persons.tsv`, `names.tsv` and `relations.tsv` (the harvest writes them)
- Create: `docs/AUTHOR-PERSONS.md`
- Modify: `docs/2026-09-23-person-author-comparison.md` (Outcome and Status), `CLAUDE.md` (the list of ALL-CAPS docs)

**Interfaces:**
- Consumes: `PersonHarvest.main`
- Produces: the committed registry, which phase 3 loads through `PersonRegistry.get()`

- [ ] **Step 1: Run the harvest**

```bash
cd core
mvn -o -q test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
  -Dexec.args="-Xmx4g -cp %classpath life.catalogue.matching.person.harvest.PersonHarvest src/main/resources/authorship/persons target/person-harvest" \
  > target/person-harvest.log 2>&1
cd ..
```
Run it in the background: it asks Wikidata some 250 times and IPNI some 150 times, with pauses. If it dies, rerun the
same command: the cache in `core/target/person-harvest/cache` makes it resume.
Expected: `core/target/person-harvest/report.txt` exists, and the three files hold tens of thousands of lines.

- [ ] **Step 2: Read the report and check the result**

```bash
head -60 core/target/person-harvest/report.txt
wc -l core/src/main/resources/authorship/persons/*.tsv
du -sh core/src/main/resources/authorship/persons
grep -P '^wd:Q\d+\t' core/src/main/resources/authorship/persons/persons.tsv | grep -i 'Sowerby' | head
mvn -o -pl core test -Dtest='life.catalogue.matching.person.**' -Dsurefire.failIfNoSpecifiedTests=false
```
Expected:
- the report counts persons, conflicts, ambiguous ids and unresolved author map rows;
- the Sowerbys appear with their `II`/`III` suffixes where Wikidata has them;
- `PersonRegistryFilesTest` passes on the real data.

If the files are far above 20 MB, record that in the Outcome of Step 4; do not gzip them in this task.

- [ ] **Step 3: Write `docs/AUTHOR-PERSONS.md`**

A reference of current behaviour, in the style of `docs/AUTHOR-CORPUS.md`, with these sections:
- **What it is:** persons keyed by authority ids, used by nothing in production yet. The person matcher of
  [2026-09-23-person-author-comparison.md](2026-09-23-person-author-comparison.md) is phase 3.
- **The files:** the table of columns from Global Constraints, the id rule, the lists and empty cells, the meaning of
  `kind`, `code` and `relation`.
- **Looking up a citation:** the normalized key, the two derived forms, and the code restriction.
- **Harvesting:** the command of Task 9 Step 1, the cache, the sources with their properties and fields, the merge
  rules, the report sections, and what to do with unresolved author map rows (add curated lines; a person without an
  authority id gets a `clb:N` id, N being one above the highest in use).
- **ZooBank:** the placeholder and its target mapping, and that the harvest refuses `--zoobank` until the reader exists.
- **Curating by hand:** a curated line is never removed or overwritten; the integrity test in `PersonRegistryFilesTest`
  must pass.

- [ ] **Step 4: Record the outcome**

In `docs/2026-09-23-person-author-comparison.md`:
- Change the Status line to say that phases 0, 1 and 2 are implemented on `fix/author-nomcode` and phase 3 is not.
- Append a `**Phase 2, the registry** (<date>)` paragraph to the Outcome: persons, names and relations counted, the
  split by source, the size on disk, the numbers of conflicts, ambiguous ids and unresolved author map rows, how long
  the harvest took, and every deviation from this plan.

In `CLAUDE.md`, add `AUTHOR-PERSONS.md` to the list of ALL-CAPS docs in "Documentation Conventions", after
`AUTHOR-CORPUS.md`.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/resources/authorship/persons/ docs/AUTHOR-PERSONS.md docs/2026-09-23-person-author-comparison.md CLAUDE.md
git commit -m "feat(authorship): the first harvest of the person registry

<persons> persons from Wikidata and IPNI with <names> name forms and
<relations> relations. See docs/AUTHOR-PERSONS.md.

Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg"
```

Fill the three numbers from Step 2's `wc -l`, less one header line each.
