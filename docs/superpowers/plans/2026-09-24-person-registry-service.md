# Person Registry Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the person registry of phase 3 from committed TSV files into Postgres, serve it and standalone author
matching over the API, and keep it in step with Wikidata and IPNI through a harvest job that follows the sources while
curated lines win.

**Architecture:** The model and its enums move to `api`, storage and lookup to `dao` behind a `PersonStore` interface
with two implementations - `PgPersonStore` (bounded Caffeine caches, cleared by a `PersonsChanged` broker event) and
`MemoryPersonStore` (today's `PersonRegistry`, built from TSVs, for tests, corpus tools and the consistency check).
`PersonTables` rewrites the four tables by delete and COPY in one transaction, deriving forms and the any-id index on
the way. The harvest moves to `core` main; `PersonHarvestJob` reads the sources with no session open, then merges,
checks and writes under a table lock. Resources in `webservice` read through the store.

**Tech Stack:** Java 25, Maven, Dropwizard 5 / Jersey, MyBatis 3.5, Postgres 17 (pgjdbc `CopyManager`), Caffeine,
JUnit 4, Mockito, the GBIF name parser (`NameParser.PARSER.parseAuthorship`).

**Spec:** [`docs/2026-09-24-person-registry-service.md`](../../2026-09-24-person-registry-service.md)

## Global Constraints

- Work in the worktree `/Users/markus/code/col/backend/.claude/worktrees/author-corpus`, branch
  `feat/person-author-comparison`, one branch off master. Commit per task; every commit message ends with
  `Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg`. Push to origin at the end, no PR.
- Every maven command runs with `export JAVA_HOME=$HOME/.sdkman/candidates/java/25.0.3-librca` and offline (`-o`).
- core and webservice take `dao` and its tests jar from `~/.m2`: after any change in `api` or `dao`, run
  `mvn -o -q -pl dao -am install -DskipTests` before running core or webservice tests.
- Running one test class in a module: `mvn -o -pl <module> test -Dtest=<Class> -Dsurefire.failIfNoSpecifiedTests=false`.
  Redirect long output to a file and read its tail.
- Code style: 2 space indent, 140 columns, `javax.annotation.Nullable`, comments as dense as the surrounding code.
- The key of a form is `PersonKeys.key` - `AuthorshipNormalizer.normalize` repeated until it no longer changes - computed
  "in Java on writing and on looking up, never in SQL".
- Caches: "key and code to person ids (about 100,000 entries) and id to person (about 50,000)"; "The caches also expire
  an entry after an hour".
- Harvest: Wikidata and IPNI "serially, 1 s and 250 ms apart, retrying"; "It runs in the default lane under one
  serialization key"; "No database session is open while it fetches".
- API: `GET /person/{id}`, `GET /person/match?q=&code=&year=&group=`, `GET /person/match/authorship?q=&code=&group=`,
  `GET /person/export`; admin `POST /admin/persons/harvest`, `POST /admin/persons/import`. Citation statuses
  `RESOLVED`, `AMBIGUOUS`, `UNKNOWN`, `RULED_OUT`; an unreadable authorship is `UNPARSABLE`. "An unknown `code` or
  `group` is a 400."
- Enums `PersonNameKind` (STANDARD, CITATION, FULL, VARIANT, DERIVED), `PersonFormCode` (BOT, ZOO, ANY),
  `PersonRelationType` (PARENT, SIBLING), `PersonSource` (CURATED, IPNI, ZOOBANK, WIKIDATA) are pg enums too, value by
  value and in this order, or `PgSetupRuleTest.pgEnums` fails.
- Every schema change goes into `dao/src/main/resources/life/catalogue/db/dbschema.sql` and into a section at the top
  of the PROD changes list in `dbschema.md`.
- ALL-CAPS docs describe current behaviour only; the spec stays a dated record. No secrets, nothing from the private
  deploy repo.

## Decisions the spec left open

The plan settles these; each is recorded in the spec's Outcome in Task 10.

1. **Joins keep working through former ids.** A person the rebuild joins into another disappears into it, its id a
   former id of the survivor, so `GET /person/<old id>` answers the survivor directly. The `successor` column is carried
   by files, tables and import, checked to resolve, and set by nobody yet (curator tooling). The spec's test "a Wikidata
   redirect naming a successor" is the redirect test: the old id finds the survivor, the join is in the report.
2. **A curated person wins as a whole line.** Its empty cells are no longer filled from the sources (fill-only did);
   what the sources say otherwise is reported.
3. **A person a source still lists but no source names any more is retired too.** Otherwise one odd record would fail
   the consistency check of a whole harvest ("has no name").
4. **Impossible years** chosen from the sources leave all four years out, as fill-only did for new persons.
5. **`/person/{id}` shows the forms sources and curators gave**, not the `DERIVED` rows.
6. **`RULED_OUT` lists the candidates** the year or group excluded; `UNKNOWN` lists none.
7. **`IN (...)` lists instead of `= ANY(?)`**: the repository's mappers use `<foreach>`, and no array binding exists.
8. **`created`/`modified` survive the rewrite**: a person keeps its `created`, and its `modified` unless its row changed.
9. **The committed TSVs move to `core/src/test/resources`**: only tests and corpus tools read them, the main jar stops
   carrying 20 MB. Removing them from the repository (rollout step 3) waits until prod was imported.
10. **The harvest cache** lives in `persons.harvestDir`; a failed run leaves it for the next to resume from, a
    successful run deletes it.
11. **The import writes synchronously** in the request, like any other admin write.
12. **A blank string has no key** (`PersonKeys.key` returns null), so nothing is ever indexed or looked up under `""`.
13. Rollout steps 2 to 4 are operations after the deploy, not tasks here.

## Review Focus

1. A citation that normalizes to nothing (`.`) - expected `UNKNOWN` with a null key, never an exception or an SQL
   `IN ()`. Tests: Task 2 `PersonKeysTest.punctuationIsNoKey`, Task 5 `PgPersonStoreTest.byKeys`, Task 6
   `PersonMatchServiceTest.punctuationOnly`.
2. Names and relations that refer to a person by a former or an authority id - expected to be written under the
   person's own id and found by any of its ids. Test: Task 4 `PersonTablesTest.roundTrip`.
3. An import zip holding the files in a folder, holding other files too, or missing one - expected to read, or to be a
   400 naming the missing file. Test: Task 3 `PersonFilesTest.zipOfAFolderAndAMissingFile`.
4. Values with quotes, commas, apostrophes and backslashes (`d'Orb.`, `O'Brien, Jr`) - expected to survive COPY and
   its array literals unchanged. Test: Task 4 `PersonTablesTest.quoting`.
5. Parameters as the rest of the API takes them: `code=botanical` lower case, `year=18x` not a number - expected to be
   accepted, resp. a 400 and not Jersey's 404. Test: Task 7 `PersonResourceTest.parameters`.

---

### Task 1: Model and vocabularies in `api`, the in-memory registry in `dao`

A pure move with renames, plus the `DERIVED` kind and the pg enum types that keep `PgSetupRuleTest.pgEnums` green.
The committed TSVs move to core's test resources.

**Files:**
- Move: `core/src/main/java/life/catalogue/matching/person/{Person,PersonName,PersonRelation}.java` → `api/src/main/java/life/catalogue/api/model/`
- Move+rename: `NameKind` → `api/.../api/vocab/PersonNameKind.java`, `FormCode` → `PersonFormCode`, `RelationType` → `PersonRelationType`, `Provenance` → `PersonSource`
- Move+rename: `core/.../matching/person/PersonRegistry.java` → `dao/src/main/java/life/catalogue/matching/person/MemoryPersonStore.java`
- Move: `core/.../matching/person/PersonFiles.java` → `dao/src/main/java/life/catalogue/matching/person/PersonFiles.java`
- Move+rename: `core/src/test/.../matching/person/harvest/PersonSource.java` → `HarvestSource.java`
- Move: `core/src/test/.../matching/person/PersonRegistryTest.java` → `dao/src/test/java/life/catalogue/matching/person/MemoryPersonStoreTest.java`; `PersonFilesTest.java` → `dao/src/test/java/life/catalogue/matching/person/PersonFilesTest.java`
- Move: `core/src/main/resources/authorship/persons/` → `core/src/test/resources/authorship/persons/`
- Modify: `dao/src/main/resources/life/catalogue/db/dbschema.sql`, `dbschema.md`, `core/src/test/java/life/catalogue/matching/person/PersonRegistryFilesTest.java`
- Test: `dao/src/test/java/life/catalogue/junit/PgSetupRuleTest.java` (unchanged, the gate)

**Interfaces:**
- Produces: `life.catalogue.api.model.{Person, PersonName, PersonRelation}` (records unchanged but for their package and
  the enum types); `life.catalogue.api.vocab.{PersonNameKind, PersonFormCode, PersonRelationType, PersonSource}`;
  `life.catalogue.matching.person.MemoryPersonStore` (dao) with `public static synchronized MemoryPersonStore resources()`
  in place of `PersonRegistry.get()`; `PersonFiles` in dao; the harvest interface `HarvestSource` (core test, moves to
  main in Task 8).

- [ ] **Step 1: Move and rename**

```bash
cd /Users/markus/code/col/backend/.claude/worktrees/author-corpus
P=core/src/main/java/life/catalogue/matching/person
T=core/src/test/java/life/catalogue/matching/person
A=api/src/main/java/life/catalogue/api
D=dao/src/main/java/life/catalogue/matching/person
DT=dao/src/test/java/life/catalogue/matching/person
mkdir -p $D $DT core/src/test/resources/authorship
git mv $T/harvest/PersonSource.java $T/harvest/HarvestSource.java
git mv $P/Person.java $P/PersonName.java $P/PersonRelation.java $A/model/
git mv $P/NameKind.java $A/vocab/PersonNameKind.java
git mv $P/FormCode.java $A/vocab/PersonFormCode.java
git mv $P/RelationType.java $A/vocab/PersonRelationType.java
git mv $P/Provenance.java $A/vocab/PersonSource.java
git mv $P/PersonRegistry.java $D/MemoryPersonStore.java
git mv $P/PersonFiles.java $D/PersonFiles.java
git mv $T/PersonRegistryTest.java $DT/MemoryPersonStoreTest.java
git mv $T/PersonFilesTest.java $DT/PersonFilesTest.java
git mv core/src/main/resources/authorship/persons core/src/test/resources/authorship/persons

FILES=$(git ls-files $A/model/Person.java $A/model/PersonName.java $A/model/PersonRelation.java \
  "$A/vocab/Person*.java" "$D/*.java" "$DT/*.java" "$P/*.java" "$T/*.java" "$T/harvest/*.java")
# the harvest interface gives up its name first
perl -pi -e 's/\bPersonSource\b/HarvestSource/g' $(git ls-files "$T/harvest/*.java")
perl -pi -e '
  s/\bNameKind\b/PersonNameKind/g; s/\bFormCode\b/PersonFormCode/g; s/\bRelationType\b/PersonRelationType/g;
  s/\bProvenance\b/PersonSource/g; s/\bPersonRegistryTest\b/MemoryPersonStoreTest/g; s/\bPersonRegistry\b/MemoryPersonStore/g;
  s/MemoryPersonStore\.get\(\)/MemoryPersonStore.resources()/g;
  s/\blife\.catalogue\.matching\.person\.(PersonNameKind|PersonFormCode|PersonRelationType|PersonSource)\b/life.catalogue.api.vocab.$1/g;
  s/\blife\.catalogue\.matching\.person\.(Person|PersonName|PersonRelation)\b(?!\.)/life.catalogue.api.model.$1/g;
' $FILES
perl -pi -e 's/^package life\.catalogue\.matching\.person;/package life.catalogue.api.model;/' \
  $A/model/Person.java $A/model/PersonName.java $A/model/PersonRelation.java
perl -pi -e 's/^package life\.catalogue\.matching\.person;/package life.catalogue.api.vocab;/' \
  $A/vocab/PersonNameKind.java $A/vocab/PersonFormCode.java $A/vocab/PersonRelationType.java $A/vocab/PersonSource.java
perl -pi -e 's/public static synchronized MemoryPersonStore get\(\)/public static synchronized MemoryPersonStore resources()/' \
  $D/MemoryPersonStore.java
# explicit imports wherever a moved type is used outside its new package
for f in $FILES; do
  for c in model.Person model.PersonName model.PersonRelation vocab.PersonNameKind vocab.PersonFormCode \
           vocab.PersonRelationType vocab.PersonSource; do
    n=${c#*.}; pkg=life.catalogue.api.${c%.*}
    grep -q "^package $pkg;" "$f" && continue
    # a static import of an enum's constants does not import the type itself, so only a plain import counts
    grep -qE "^import life\.catalogue\.api\.$c;" "$f" && continue
    grep -qw "$n" "$f" || continue
    perl -0pi -e "s/^(package [^;]+;\n)/\$1\nimport life.catalogue.api.$c;\n/m" "$f"
  done
done
git status --short | head -40
```

Expected: the moves listed as renames, no untracked leftovers under `core/src/main/java/life/catalogue/matching/person`
but `PersonAuthorMatcher.java` and `PersonResolver.java`.

- [ ] **Step 2: Add `DERIVED`**

In `api/src/main/java/life/catalogue/api/vocab/PersonNameKind.java` replace the last constant:

```java
  /** any other spelling or alias */
  VARIANT,
  /** derived from the structured name and full names of the person when the registry is written or loaded: initials, family name and suffix */
  DERIVED
```

- [ ] **Step 3: Move the resources check to core**

In `dao/src/test/java/life/catalogue/matching/person/PersonFilesTest.java` delete the test `committedFilesRead` (dao
has no registry files on its classpath). In `core/src/test/java/life/catalogue/matching/person/PersonRegistryFilesTest.java`
add it, with `import static org.junit.Assert.assertNotNull;`:

```java
  @Test
  public void committedFilesRead() throws Exception {
    assertNotNull(PersonFiles.readResources());
  }
```

- [ ] **Step 4: Build and watch `pgEnums` fail**

```bash
export JAVA_HOME=$HOME/.sdkman/candidates/java/25.0.3-librca
mvn -o -q -pl dao -am install -DskipTests
mvn -o -pl dao test -Dtest=PgSetupRuleTest -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t1.log 2>&1; tail -30 /tmp/t1.log
```

Expected: the build compiles; `pgEnums` FAILS with `type "personformcode" does not exist` (or another of the four).

- [ ] **Step 5: Add the pg enum types**

In `dbschema.sql`, insert before `CREATE TYPE RANK AS ENUM (`:

```sql
CREATE TYPE PERSONFORMCODE AS ENUM (
  'BOT',
  'ZOO',
  'ANY'
);

CREATE TYPE PERSONNAMEKIND AS ENUM (
  'STANDARD',
  'CITATION',
  'FULL',
  'VARIANT',
  'DERIVED'
);

CREATE TYPE PERSONRELATIONTYPE AS ENUM (
  'PARENT',
  'SIBLING'
);

CREATE TYPE PERSONSOURCE AS ENUM (
  'CURATED',
  'IPNI',
  'ZOOBANK',
  'WIKIDATA'
);

```

In `dbschema.md`, insert right after the line `### PROD changes` and its blank line:

````markdown
#### 2026-09-24 the person registry
```sql
CREATE TYPE PERSONFORMCODE AS ENUM ('BOT', 'ZOO', 'ANY');
CREATE TYPE PERSONNAMEKIND AS ENUM ('STANDARD', 'CITATION', 'FULL', 'VARIANT', 'DERIVED');
CREATE TYPE PERSONRELATIONTYPE AS ENUM ('PARENT', 'SIBLING');
CREATE TYPE PERSONSOURCE AS ENUM ('CURATED', 'IPNI', 'ZOOBANK', 'WIKIDATA');
```
The person registry of author matching moves from files in the code into the database, see
`docs/2026-09-24-person-registry-service.md`.

````

- [ ] **Step 6: Run the gate and every moved test**

```bash
mvn -o -q -pl dao -am install -DskipTests
mvn -o -pl dao test -Dtest='PgSetupRuleTest,MemoryPersonStoreTest,PersonFilesTest' -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t1.log 2>&1; tail -30 /tmp/t1.log
mvn -o -pl core test -Dtest='life/catalogue/matching/person/**/*Test.java' -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t1c.log 2>&1; tail -30 /tmp/t1c.log
mvn -o -pl api test > /tmp/t1a.log 2>&1; tail -30 /tmp/t1a.log
```

Expected: all PASS. The core run includes `PersonRegistryFilesTest`, `RelativesFixtureTest`, `PersonAuthorMatcherTest`,
`PersonResolverTest`, `PersonCorpusReportTest` and the harvest tests.

- [ ] **Step 7: Commit**

```bash
git add -A api dao core docs
git commit -m "refactor(persons): model and vocab to api, in-memory registry to dao

Person, PersonName and PersonRelation move to api.model, the enums to api.vocab as PersonNameKind (with DERIVED),
PersonFormCode, PersonRelationType and PersonSource, with pg enum types. PersonRegistry becomes MemoryPersonStore in
dao next to PersonFiles; the harvest interface becomes HarvestSource. The committed TSVs move to core's test
resources, which only tests and the corpus tools read.

Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg"
```

---

### Task 2: Lookup by key - `PersonKeys`, `PersonForms`, `PersonStore`

**Files:**
- Create: `dao/src/main/java/life/catalogue/matching/person/PersonKeys.java`, `PersonForms.java`, `PersonStore.java`
- Create: `api/src/main/java/life/catalogue/api/model/PersonInfo.java`
- Rewrite: `dao/src/main/java/life/catalogue/matching/person/MemoryPersonStore.java`
- Rewrite: `core/src/main/java/life/catalogue/matching/person/PersonResolver.java`
- Modify: `core/src/main/java/life/catalogue/matching/person/PersonAuthorMatcher.java`
- Test: `dao/src/test/java/life/catalogue/matching/person/PersonKeysTest.java` (new), `MemoryPersonStoreTest.java`, `core/src/test/java/life/catalogue/matching/person/PersonResolverTest.java`

**Interfaces:**
- Consumes: Task 1's `MemoryPersonStore`, `PersonFiles.Content`.
- Produces:
  - `PersonKeys.key(@Nullable String form)` → `@Nullable String`
  - `PersonForms.of(Person)` → `List<String>`; `PersonForms.of(Person, PersonName)` → `@Nullable String`;
    package-private `initials(String)`, `givenOf(String, Person)`, `suffix(Person)`
  - `interface PersonStore { Person get(String anyId); Set<Person> byKey(String key, NomCode code);
    Map<String, Set<Person>> byKeys(Collection<String> keys, NomCode code); Set<Person> relatives(Person p);
    Set<String> keys(Person p, NomCode code); PersonInfo info(String anyId); default Set<Person> candidates(String citation, NomCode code); }`
  - `record PersonInfo(Person person, List<PersonName> names, List<PersonRelation> relations)` in `api.model`
  - `PersonResolver(PersonStore store, Margins margins)` with `candidates(String, NomCode)`,
    `narrow(Set<Person>, Integer, TaxGroup)`, `resolve(String, NomCode, Integer, TaxGroup)`, static `year(String)`
  - `PersonAuthorMatcher(PersonStore store, PersonResolver resolver, AuthorMatcher fallback, RelativesPolicy policy, Consumer<Explanation> listener)`

- [ ] **Step 1: Write the failing tests**

`dao/src/test/java/life/catalogue/matching/person/PersonKeysTest.java`:

```java
package life.catalogue.matching.person;

import life.catalogue.common.tax.AuthorshipNormalizer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PersonKeysTest {

  @Test
  public void keyOfAForm() {
    assertEquals("sw", PersonKeys.key("Sw."));
  }

  /** nothing is indexed or looked up under an empty key */
  @Test
  public void punctuationIsNoKey() {
    assertNull(PersonKeys.key(null));
    assertNull(PersonKeys.key(""));
    assertNull(PersonKeys.key(" "));
    assertNull(PersonKeys.key("."));
  }

  /** normalizing is not idempotent: a key normalized once more is still the same key */
  @Test
  public void fixpoint() {
    for (String name : new String[]{"Đinh", "McQueen", "Saeed"}) {
      assertEquals(name, PersonKeys.key(name), PersonKeys.key(AuthorshipNormalizer.normalize(name)));
    }
  }
}
```

Add to `MemoryPersonStoreTest` (with imports `life.catalogue.api.model.PersonInfo`, `java.util.List`,
`java.util.Map`) and replace the two calls `MemoryPersonStore.initials(` by `PersonForms.initials(`:

```java
  /** many keys at once, a key without persons left out */
  @Test
  public void byKeys() {
    var reg = registry();
    assertEquals(Map.of("sw", Set.of(SWARTZ), "sowerby", Set.of(SOWERBY1, SOWERBY2)),
      reg.byKeys(List.of("sw", "sowerby", "nobody"), NomCode.BOTANICAL));
    assertEquals(Map.of(), reg.byKeys(List.of(), NomCode.BOTANICAL));
  }

  /** a person with the forms and relations it has, the relation seen from both ends */
  @Test
  public void info() {
    var reg = registry();
    PersonInfo info = reg.info("wd:Q2");
    assertSame(SOWERBY2, info.person());
    assertEquals(List.of(new PersonName("wd:Q2", "G.B.Sowerby II", PersonNameKind.CITATION, PersonFormCode.ZOO, PersonSource.WIKIDATA)),
      info.names());
    assertEquals(List.of(new PersonRelation("wd:Q2", PersonRelationType.PARENT, "wd:Q1", PersonSource.WIKIDATA)), info.relations());
    assertEquals(info.relations(), reg.info("wd:Q1").relations());
    assertNull(reg.info("wd:Q404"));
  }
```

Add to `core/src/test/java/life/catalogue/matching/person/PersonResolverTest.java` (import `assertSame`):

```java
  /** the candidates of a citation before any narrowing, and the narrowing on its own */
  @Test
  public void candidatesThenNarrow() {
    Set<Person> both = resolver.candidates("J. Sowerby", ZOO);
    assertEquals(Set.of(JAMES1, JAMES2), both);
    assertEquals(Set.of(JAMES2), resolver.narrow(both, 1850, null));
    assertEquals(Set.of(), resolver.narrow(both, 1860, null));
    assertSame(both, resolver.narrow(both, null, null));
  }
```

- [ ] **Step 2: Run them to see them fail**

```bash
mvn -o -pl dao test-compile > /tmp/t2.log 2>&1; grep -E 'ERROR|cannot find symbol' /tmp/t2.log | head
```

Expected: compilation FAILS - `PersonKeys`, `PersonInfo`, `byKeys`, `info`, `PersonForms` do not exist.

- [ ] **Step 3: Write `PersonKeys`**

```java
package life.catalogue.matching.person;

import life.catalogue.common.tax.AuthorshipNormalizer;

import javax.annotation.Nullable;

/**
 * The key name forms and citations are looked up by.
 */
public final class PersonKeys {
  private PersonKeys() {
  }

  /**
   * {@link AuthorshipNormalizer#normalize(String)} until it no longer changes. The comparator hands over authors
   * normalized already, and normalizing is not always idempotent - a capital Đ is only folded once lower cased, and
   * removing an e can make a new "ae", "oe" or "ue" - so a key normalized once would miss them.
   *
   * @return the key, null for a form that leaves nothing to look up
   */
  @Nullable
  public static String key(@Nullable String form) {
    String key = AuthorshipNormalizer.normalize(form);
    for (int i = 0; key != null && i < 5; i++) {
      String next = AuthorshipNormalizer.normalize(key);
      if (key.equals(next)) break;
      key = next;
    }
    return key == null || key.isBlank() ? null : key;
  }
}
```

- [ ] **Step 4: Write `PersonForms`**

Move `suffix`, `givenOf` and `initials` out of `MemoryPersonStore` unchanged:

```java
package life.catalogue.matching.person;

import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonName;
import life.catalogue.api.vocab.PersonNameKind;
import life.catalogue.common.tax.AuthorshipNormalizer;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * The forms a person is cited by that no source lists, derived from its structured name and its full names: the bare
 * family name ("Sowerby"), the initials of the given names with family name and suffix ("G. B. Sowerby II"), the family
 * name with its suffix ("Hooker f."), and the initials of every full name or variant that ends with the family name, as
 * a source often lists fewer given names than its label holds. Nobiliary particles stay words ("A. P. de Candolle"),
 * bracketed alternatives of a forename give no initials. Derived forms apply to every code.
 */
public final class PersonForms {
  private PersonForms() {
  }

  /**
   * @return the forms derived from the structured name, none without a family name
   */
  public static List<String> of(Person p) {
    if (p.family() == null) return List.of();
    List<String> forms = new ArrayList<>(3);
    forms.add(p.family());
    forms.add(initials(p.given()) + p.family() + suffix(p));
    if (p.suffix() != null) {
      // relatives are cited by the family name and suffix alone: "Hooker f.", "Sowerby II"
      forms.add(p.family() + suffix(p));
    }
    return forms;
  }

  /**
   * @return the initials of a full name or variant that ends with the family name and suffix, followed by them; null for
   *         any other form
   */
  @Nullable
  public static String of(Person p, PersonName n) {
    if (p.family() == null || (n.kind() != PersonNameKind.FULL && n.kind() != PersonNameKind.VARIANT)) return null;
    String given = givenOf(n.form(), p);
    return given == null ? null : initials(given) + p.family() + suffix(p);
  }

  static String suffix(Person p) {
    return p.suffix() == null ? "" : " " + p.suffix();
  }

  /**
   * @return the words of a full name before the family name, "George Brettingham" of "George Brettingham Sowerby II",
   *         null for a name that does not end with the person's family name and suffix
   */
  @Nullable
  static String givenOf(String full, Person p) {
    String name = full.strip();
    String suffix = suffix(p);
    if (!suffix.isEmpty() && name.endsWith(suffix)) {
      name = name.substring(0, name.length() - suffix.length());
    }
    String family = " " + p.family();
    if (!name.endsWith(family)) return null;
    String given = name.substring(0, name.length() - family.length()).strip();
    return given.isEmpty() ? null : given;
  }

  /**
   * @return "G. B. " for "George Brettingham", "J. B. " for "Jean-Baptiste", "J. C. " for "J.C.", "A. P. de " for
   *         "Augustin Pyramus de", empty for none
   */
  static String initials(@Nullable String given) {
    if (given == null) return "";
    StringBuilder sb = new StringBuilder();
    // IPNI lists alternative forenames in brackets: "Carl (Karl, Carel, Carolus) Bořivoj"; a variant may be dotted: "J.C."
    for (String part : given.replaceAll("\\([^)]*\\)", " ").split("[\\s.-]+")) {
      if (AuthorshipNormalizer.PARTICLES.contains(part)) {
        sb.append(part).append(' ');
        continue;
      }
      String letters = part.replaceAll("^[^\\p{L}]+", "");
      if (!letters.isEmpty()) {
        sb.append(letters.charAt(0)).append(". ");
      }
    }
    return sb.toString();
  }
}
```

- [ ] **Step 5: Write `PersonInfo` and `PersonStore`**

`api/src/main/java/life/catalogue/api/model/PersonInfo.java`:

```java
package life.catalogue.api.model;

import java.util.List;

/**
 * A person with the name forms sources and curators gave it and the relations it has to others.
 */
public record PersonInfo(Person person, List<PersonName> names, List<PersonRelation> relations) {
}
```

`dao/src/main/java/life/catalogue/matching/person/PersonStore.java`:

```java
package life.catalogue.matching.person;

import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonInfo;
import life.catalogue.api.vocab.PersonFormCode;

import org.gbif.nameparser.api.NomCode;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * The person registry as author matching reads it. Persons are found by any id they answer to - their own, a former id
 * or a prefixed authority id - or by the {@link PersonKeys#key(String)} of a form, derived ones included. A form applies
 * to a name by its code, see {@link PersonFormCode#appliesTo(NomCode)}. A key may name several persons: a bare surname
 * proposes candidates only.
 */
public interface PersonStore {

  /**
   * @param anyId an id, a former id or a prefixed authority id
   * @return the person, null for none
   */
  @Nullable
  Person get(String anyId);

  /**
   * @return the persons with a form under the key whose code applies, empty for none
   */
  Set<Person> byKey(String key, @Nullable NomCode code);

  /**
   * @return the persons of every key that has any, as {@link #byKey}
   */
  Map<String, Set<Person>> byKeys(Collection<String> keys, @Nullable NomCode code);

  /**
   * @return parents, children and siblings
   */
  Set<Person> relatives(Person p);

  /**
   * @return the keys of every form of the person whose code applies, derived ones included
   */
  Set<String> keys(Person p, @Nullable NomCode code);

  /**
   * @return the person with its forms, derived ones excluded, and its relations; null for an id nobody answers to
   */
  @Nullable
  PersonInfo info(String anyId);

  /**
   * @param citation an author as cited, or already normalized, which folds to the same key
   * @return the persons the citation may name under the code of the name
   */
  default Set<Person> candidates(String citation, @Nullable NomCode code) {
    String key = PersonKeys.key(citation);
    return key == null ? Set.of() : byKey(key, code);
  }
}
```

- [ ] **Step 6: Rewrite `MemoryPersonStore`**

```java
package life.catalogue.matching.person;

import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonInfo;
import life.catalogue.api.model.PersonName;
import life.catalogue.api.model.PersonRelation;
import life.catalogue.api.vocab.PersonFormCode;

import org.gbif.nameparser.api.NomCode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

import javax.annotation.Nullable;

/**
 * The person registry in memory, built from its files: the store of the tests and the corpus tools, and the check of
 * every registry before it is written. Persons are looked up by the {@link PersonKeys#key(String)} of their forms and of
 * the forms {@link PersonForms} derives.
 */
public class MemoryPersonStore implements PersonStore {
  private static MemoryPersonStore resources;

  private record Form(Person person, PersonFormCode code) {
  }

  private record Keyed(String key, PersonFormCode code) {
  }

  private final int size;
  private final Map<String, Person> byId = new HashMap<>();
  private final Map<String, List<Form>> byKey = new HashMap<>();
  // identity: the persons the store hands out are its own instances, and records hash all their fields
  private final Map<Person, List<Keyed>> keysByPerson = new IdentityHashMap<>();
  private final Map<String, Set<Person>> relatives = new HashMap<>();
  private final Map<String, List<PersonName>> names = new HashMap<>();
  private final Map<String, List<PersonRelation>> relations = new HashMap<>();
  private final List<String> problems = new ArrayList<>();

  /**
   * @return the registry of the files on the classpath, loaded once: the corpus tools and their tests read it
   */
  public static synchronized MemoryPersonStore resources() {
    if (resources == null) {
      try {
        resources = new MemoryPersonStore(PersonFiles.readResources());
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return resources;
  }

  public MemoryPersonStore(PersonFiles.Content c) {
    size = c.persons().size();
    for (Person p : c.persons()) {
      if (p.id() == null) {
        problems.add("a person without an id: " + p.family());
        continue;
      }
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
      if (p.born() != null && p.activeFrom() != null && p.activeFrom() < p.born()) {
        problems.add(p.id() + " was active before it was born");
      }
      for (String form : PersonForms.of(p)) {
        add(form, p, PersonFormCode.ANY);
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
      names.computeIfAbsent(p.id(), k -> new ArrayList<>()).add(n);
      add(n.form(), p, n.code());
      String derived = PersonForms.of(p, n);
      if (derived != null) {
        add(derived, p, PersonFormCode.ANY);
      }
    }
    for (Person p : c.persons()) {
      if (p.id() != null && !named.contains(p)) {
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
      relations.computeIfAbsent(a.id(), k -> new ArrayList<>()).add(r);
      if (b != a) {
        relations.computeIfAbsent(b.id(), k -> new ArrayList<>()).add(r);
      }
    }
  }

  private void add(String form, Person p, PersonFormCode code) {
    String key = PersonKeys.key(form);
    if (key != null) {
      List<Form> forms = byKey.computeIfAbsent(key, k -> new ArrayList<>(1));
      Form f = new Form(p, code);
      if (!forms.contains(f)) {
        forms.add(f);
      }
      List<Keyed> keys = keysByPerson.computeIfAbsent(p, x -> new ArrayList<>(4));
      Keyed k = new Keyed(key, code);
      if (!keys.contains(k)) {
        keys.add(k);
      }
    }
  }

  @Override
  public Set<Person> byKey(String key, @Nullable NomCode code) {
    Set<Person> persons = new LinkedHashSet<>();
    for (Form f : byKey.getOrDefault(key, List.of())) {
      if (f.code().appliesTo(code)) {
        persons.add(f.person());
      }
    }
    return persons;
  }

  @Override
  public Map<String, Set<Person>> byKeys(Collection<String> keys, @Nullable NomCode code) {
    Map<String, Set<Person>> map = new HashMap<>();
    for (String key : keys) {
      Set<Person> persons = byKey(key, code);
      if (!persons.isEmpty()) {
        map.put(key, persons);
      }
    }
    return map;
  }

  @Override
  public Set<String> keys(Person p, @Nullable NomCode code) {
    Set<String> keys = new LinkedHashSet<>();
    for (Keyed k : keysByPerson.getOrDefault(p, List.of())) {
      if (k.code().appliesTo(code)) {
        keys.add(k.key());
      }
    }
    return keys;
  }

  @Override
  @Nullable
  public Person get(String anyId) {
    return byId.get(anyId);
  }

  @Override
  public Set<Person> relatives(Person p) {
    return relatives.getOrDefault(p.id(), Set.of());
  }

  @Override
  @Nullable
  public PersonInfo info(String anyId) {
    Person p = byId.get(anyId);
    if (p == null) return null;
    return new PersonInfo(p, List.copyOf(names.getOrDefault(p.id(), List.of())),
      List.copyOf(relations.getOrDefault(p.id(), List.of())));
  }

  /**
   * @return what is wrong with the registry, empty for a consistent one
   */
  public List<String> problems() {
    return Collections.unmodifiableList(problems);
  }

  public int size() {
    return size;
  }
}
```

- [ ] **Step 7: Rewrite `PersonResolver` and switch `PersonAuthorMatcher` to the store**

`PersonResolver.java` keeps `YEAR`, `Margins`, `possible` and `year` as they are; the rest becomes:

```java
/**
 * Resolves an author citation to the persons of the registry it may name under the code of the name, and narrows them by
 * what is known about the name: its year and its taxonomic group. A person without years or groups is never ruled out.
 * Nothing is cached here: the store caches what is worth it.
 */
public class PersonResolver {
  // YEAR and Margins unchanged

  private final PersonStore store;
  private final Margins margins;

  public PersonResolver(PersonStore store, Margins margins) {
    this.store = store;
    this.margins = margins;
  }

  /**
   * @param citation an author as cited, or as {@link life.catalogue.common.tax.AuthorshipNormalizer} normalized it, which
   *                 folds to the same key
   * @return the persons the citation may name, empty for none or if every one was ruled out
   */
  public Set<Person> resolve(String citation, @Nullable NomCode code, @Nullable Integer year, @Nullable TaxGroup group) {
    return narrow(candidates(citation, code), year, group);
  }

  /**
   * @return every person with a form under the citation's key whose code applies, before any narrowing
   */
  public Set<Person> candidates(String citation, @Nullable NomCode code) {
    return store.candidates(citation, code);
  }

  /**
   * @return the persons the year and group of a name leave possible, the very set if neither is known
   */
  public Set<Person> narrow(Set<Person> persons, @Nullable Integer year, @Nullable TaxGroup group) {
    if (persons.isEmpty() || (year == null && group == null)) {
      return persons;
    }
    return persons.stream().filter(p -> possible(p, year, group)).collect(Collectors.toCollection(LinkedHashSet::new));
  }

  // possible(...) and year(...) unchanged
}
```

Drop the now unused imports `Collections`, `Map`, `ConcurrentHashMap`. In `PersonAuthorMatcher` replace the field
`private final MemoryPersonStore registry;` by `private final PersonStore store;`, the constructor's first parameter by
`PersonStore store` (assign `this.store = store;`), and the two uses `registry.relatives(a)` and
`registry.keys(p, ctx.code())` by `store.relatives(a)` and `store.keys(p, ctx.code())`.

- [ ] **Step 8: Run the tests**

```bash
mvn -o -q -pl dao -am install -DskipTests
mvn -o -pl dao test -Dtest='PersonKeysTest,MemoryPersonStoreTest,PersonFilesTest' -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t2.log 2>&1; tail -30 /tmp/t2.log
mvn -o -pl core test -Dtest='life/catalogue/matching/person/**/*Test.java' -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t2c.log 2>&1; tail -30 /tmp/t2c.log
```

Expected: all PASS, `RelativesFixtureTest` and `PersonCorpusReportTest` included.

- [ ] **Step 9: Commit**

```bash
git add -A api dao core webservice docs && git commit -m "feat(persons): PersonStore interface with keys, derived forms and info

PersonKeys and PersonForms come out of the registry, MemoryPersonStore implements PersonStore with byKeys and info,
and the resolver and matcher read any store. The resolver's unbounded cache goes: a store caches what is worth it.

Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg"
```

---

### Task 3: Retired persons, successors and the files as a zip

**Files:**
- Modify: `api/src/main/java/life/catalogue/api/model/Person.java`
- Rewrite: `dao/src/main/java/life/catalogue/matching/person/PersonFiles.java`
- Modify: `dao/src/main/java/life/catalogue/matching/person/PersonForms.java`, `MemoryPersonStore.java`
- Test: `dao/src/test/java/life/catalogue/matching/person/PersonFilesTest.java`, `MemoryPersonStoreTest.java`

**Interfaces:**
- Consumes: Task 2's `PersonForms`, `MemoryPersonStore`.
- Produces:
  - `Person` gains the components `@Nullable LocalDate retired, @Nullable String successor` after `source`; the old
    14 argument constructor stays for a person nobody retired.
  - `PersonFiles.readZip(InputStream)` → `Content`; `PersonFiles.writeZip(OutputStream, Content)`;
    `PersonFiles.LEGACY_PERSON_COLUMNS`, `PERSON_COLUMNS` (with `retired`, `successor`), `FILES`.
  - A retired person derives no forms and needs no name; a successor must resolve.

- [ ] **Step 1: Write the failing tests**

In `PersonFilesTest`, extract the content of `roundTrip` into a helper and change its header assertion, then add four
tests (imports: `java.io.*`, `java.time.LocalDate`, `java.util.*`, `java.util.zip.*`):

```java
  static PersonFiles.Content content() {
    return new PersonFiles.Content(
      List.of(sowerby2(), new Person("clb:1", null, null, null, List.of(), "Smith", null, null, null, null, 1850, 1870,
        Set.of(), PersonSource.CURATED)),
      List.of(new PersonName("wd:Q2", "G.B.Sowerby II", PersonNameKind.CITATION, PersonFormCode.ZOO, PersonSource.WIKIDATA),
        new PersonName("clb:1", "Smith", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.CURATED)),
      List.of(new PersonRelation("wd:Q2", PersonRelationType.PARENT, "wd:Q1", PersonSource.WIKIDATA))
    );
  }
```

`roundTrip` uses `var c = content();` and its last assertion becomes
`assertTrue(Files.readString(dir.resolve("persons.tsv")).contains("\tAngiosperms|Molluscs\twikidata\t\t\n"));`.

```java
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
```

In `MemoryPersonStoreTest` (import `java.time.LocalDate`):

```java
  static final Person DOE = new Person("wd:Q8", "Q8", null, null, List.of("ipni:8-1"), "Doe", "Ann", null, null, null, null,
    null, Set.of(), PersonSource.WIKIDATA, LocalDate.of(2026, 9, 24), null);

  /** a retired person is found by its ids and its curated forms, derives nothing and needs no name */
  @Test
  public void retiredPerson() {
    var reg = new MemoryPersonStore(new PersonFiles.Content(List.of(DOE),
      List.of(new PersonName("wd:Q8", "Nan Doe-Roe", PersonNameKind.VARIANT, PersonFormCode.ANY, PersonSource.CURATED)), List.of()));
    assertEquals(List.of(), reg.problems());
    assertSame(DOE, reg.get("ipni:8-1"));
    assertEquals(Set.of(DOE), reg.candidates("Nan Doe-Roe", NomCode.BOTANICAL));
    assertEquals(Set.of(), reg.candidates("Doe", NomCode.BOTANICAL));
    assertEquals(Set.of(), reg.candidates("A. Doe", NomCode.BOTANICAL));
    assertEquals(List.of(), new MemoryPersonStore(new PersonFiles.Content(List.of(DOE), List.of(), List.of())).problems());
  }

  @Test
  public void unknownSuccessorIsAProblem() {
    var joined = new Person("wd:Q8", "Q8", null, null, List.of(), "Doe", null, null, null, null, null, null, Set.of(),
      PersonSource.WIKIDATA, LocalDate.of(2026, 9, 24), "wd:Q404");
    assertEquals(List.of("wd:Q8 has an unknown successor wd:Q404"),
      new MemoryPersonStore(new PersonFiles.Content(List.of(joined), List.of(), List.of())).problems());
  }
```

- [ ] **Step 2: Run them to see them fail**

```bash
mvn -o -q -pl api install -DskipTests; mvn -o -pl dao test-compile > /tmp/t3.log 2>&1; grep -E 'ERROR' /tmp/t3.log | head
```

Expected: compilation FAILS - no 16 argument `Person` constructor, no `LEGACY_PERSON_COLUMNS`, `readZip`, `writeZip`.

- [ ] **Step 3: Extend `Person`**

Add to the javadoc:

```java
 * @param retired   the day a harvest found no source having or naming the person any more, null while one does. A
 *                  retired person keeps its ids and is found by them, but no longer by its harvested or derived forms
 * @param successor the id of the person a retired one was joined into, where known
```

Add the components after `PersonSource source` and the constructor for a person nobody retired:

```java
  PersonSource source,
  @Nullable LocalDate retired,
  @Nullable String successor
) {
  ...constants unchanged...

  /**
   * A person no harvest retired.
   */
  public Person(String id, @Nullable String wikidata, @Nullable String ipni, @Nullable String zoobank, List<String> formerIds,
                @Nullable String family, @Nullable String given, @Nullable String suffix, @Nullable Integer born,
                @Nullable Integer died, @Nullable Integer activeFrom, @Nullable Integer activeTo, Set<TaxGroup> groups,
                PersonSource source) {
    this(id, wikidata, ipni, zoobank, formerIds, family, given, suffix, born, died, activeFrom, activeTo, groups, source, null, null);
  }
```

Import `java.time.LocalDate`. Add no `isRetired()`: Jackson would see a second `retired` property.

- [ ] **Step 4: Rewrite `PersonFiles`**

```java
package life.catalogue.matching.person;

import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonName;
import life.catalogue.api.model.PersonRelation;
import life.catalogue.api.vocab.PersonFormCode;
import life.catalogue.api.vocab.PersonNameKind;
import life.catalogue.api.vocab.PersonRelationType;
import life.catalogue.api.vocab.PersonSource;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.common.io.Resources;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

/**
 * Reads and writes the three files of the person registry, in a directory or a zip: the export of the API, the import
 * of the admin API and the registry of the tests and corpus tools. They are tab delimited with a header that is
 * verified, lists are pipe separated and an empty cell is null. Written sorted, so two exports diff line by line. The
 * persons file of phase 3, without the retired and successor columns, still reads.
 */
public class PersonFiles {
  public static final String RESOURCE_DIR = "authorship/persons/";
  static final String PERSONS = "persons.tsv";
  static final String NAMES = "names.tsv";
  static final String RELATIONS = "relations.tsv";
  static final List<String> FILES = List.of(PERSONS, NAMES, RELATIONS);
  static final List<String> LEGACY_PERSON_COLUMNS = List.of("id", "wikidata", "ipni", "zoobank", "formerIds", "family", "given",
    "suffix", "born", "died", "activeFrom", "activeTo", "groups", "source");
  static final List<String> PERSON_COLUMNS = Stream.concat(LEGACY_PERSON_COLUMNS.stream(), Stream.of("retired", "successor")).toList();
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
   * @return the registry of the files on the classpath, which the tests and corpus tools use
   */
  public static Content readResources() throws IOException {
    return read(f -> Resources.reader(RESOURCE_DIR + f));
  }

  public static Content read(Path dir) throws IOException {
    return read(f -> Files.newBufferedReader(dir.resolve(f), StandardCharsets.UTF_8));
  }

  /**
   * Reads the three files from a zip as {@link #writeZip} writes it. Files in a folder of the zip are found too, anything
   * else in it is ignored.
   *
   * @throws IllegalArgumentException if a file is missing or unreadable
   */
  public static Content readZip(InputStream zip) throws IOException {
    Map<String, byte[]> files = new HashMap<>();
    ZipInputStream in = new ZipInputStream(zip, StandardCharsets.UTF_8);
    ZipEntry e;
    while ((e = in.getNextEntry()) != null) {
      String name = e.getName().substring(e.getName().lastIndexOf('/') + 1);
      if (!e.isDirectory() && FILES.contains(name)) {
        files.put(name, in.readAllBytes());
      }
    }
    for (String f : FILES) {
      if (!files.containsKey(f)) {
        throw new IllegalArgumentException("The zip holds no " + f);
      }
    }
    return read(f -> new BufferedReader(new InputStreamReader(new ByteArrayInputStream(files.get(f)), StandardCharsets.UTF_8)));
  }

  private static Content read(Opener opener) throws IOException {
    return new Content(
      rows(opener, PERSONS, List.of(PERSON_COLUMNS, LEGACY_PERSON_COLUMNS), PersonFiles::person),
      rows(opener, NAMES, List.of(NAME_COLUMNS), r -> new PersonName(r[0], r[1], PersonNameKind.valueOf(r[2]),
        PersonFormCode.valueOf(r[3]), PersonSource.of(r[4]))),
      rows(opener, RELATIONS, List.of(RELATION_COLUMNS), r -> new PersonRelation(r[0], PersonRelationType.valueOf(r[1]), r[2],
        PersonSource.of(r[3])))
    );
  }

  /**
   * @param headers the headers the file may have, the current one first. A row under an older one is handed to the
   *                parser padded with nulls to the width of the current one
   */
  private static <T> List<T> rows(Opener opener, String file, List<List<String>> headers, Function<String[], T> parser)
    throws IOException {
    try (BufferedReader reader = opener.open(file)) {
      String header = reader.readLine();
      List<String> columns = header == null ? null : Arrays.asList(header.split("\t", -1));
      if (columns == null || !headers.contains(columns)) {
        throw new IllegalArgumentException("Unexpected header in " + file + ": " + header);
      }
      int width = headers.get(0).size();
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
          list.add(parser.apply(Arrays.copyOf(row, width)));
        } catch (RuntimeException e) {
          throw new IllegalArgumentException(file + " line " + n + ": " + e.getMessage(), e);
        }
      }
      return list;
    }
  }

  private static Person person(String[] r) {
    return new Person(r[0], str(r[1]), str(r[2]), str(r[3]), list(r[4]), str(r[5]), str(r[6]), str(r[7]),
      num(r[8]), num(r[9]), num(r[10]), num(r[11]), groups(r[12]), PersonSource.of(r[13]), date(r[14]), str(r[15]));
  }

  @Nullable
  private static String str(@Nullable String x) {
    return StringUtils.trimToNull(x);
  }

  @Nullable
  private static Integer num(@Nullable String x) {
    return StringUtils.isBlank(x) ? null : Integer.valueOf(x.trim());
  }

  @Nullable
  private static LocalDate date(@Nullable String x) {
    return StringUtils.isBlank(x) ? null : LocalDate.parse(x.trim());
  }

  private static List<String> list(@Nullable String x) {
    return StringUtils.isBlank(x) ? List.of() : List.of(StringUtils.split(x, LIST_SEPARATOR));
  }

  private static Set<TaxGroup> groups(@Nullable String x) {
    Set<TaxGroup> groups = EnumSet.noneOf(TaxGroup.class);
    for (String g : list(x)) {
      groups.add(TaxGroup.valueOf(g));
    }
    return groups;
  }

  public static void write(Path dir, Content c) throws IOException {
    Files.createDirectories(dir);
    for (String f : FILES) {
      try (Writer w = Files.newBufferedWriter(dir.resolve(f), StandardCharsets.UTF_8)) {
        write(w, f, c);
      }
    }
  }

  /**
   * Writes the three files as a zip to the stream, which is left open.
   */
  public static void writeZip(OutputStream out, Content c) throws IOException {
    ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
    Writer w = new BufferedWriter(new OutputStreamWriter(zip, StandardCharsets.UTF_8));
    for (String f : FILES) {
      zip.putNextEntry(new ZipEntry(f));
      write(w, f, c);
      w.flush();
      zip.closeEntry();
    }
    zip.finish();
  }

  private static void write(Writer w, String file, Content c) throws IOException {
    switch (file) {
      case PERSONS -> write(w, PERSON_COLUMNS, c.persons().stream()
        .sorted(Comparator.comparing(Person::id))
        .map(p -> new Object[]{p.id(), p.wikidata(), p.ipni(), p.zoobank(), String.join(LIST_SEPARATOR, p.formerIds()),
          p.family(), p.given(), p.suffix(), p.born(), p.died(), p.activeFrom(), p.activeTo(),
          p.groups().stream().sorted().map(Enum::name).collect(Collectors.joining(LIST_SEPARATOR)), p.source().value(),
          p.retired(), p.successor()})
        .toList());
      case NAMES -> write(w, NAME_COLUMNS, c.names().stream()
        .sorted(Comparator.comparing(PersonName::person).thenComparing(PersonName::kind).thenComparing(PersonName::code)
          .thenComparing(PersonName::form))
        .map(n -> new Object[]{n.person(), n.form(), n.kind(), n.code(), n.source().value()})
        .toList());
      case RELATIONS -> write(w, RELATION_COLUMNS, c.relations().stream()
        .sorted(Comparator.comparing(PersonRelation::person).thenComparing(PersonRelation::relation)
          .thenComparing(PersonRelation::other))
        .map(r -> new Object[]{r.person(), r.relation(), r.other(), r.source().value()})
        .toList());
      default -> throw new IllegalArgumentException("No registry file " + file);
    }
  }

  private static void write(Writer w, List<String> columns, List<Object[]> rows) throws IOException {
    w.write(String.join("\t", columns));
    w.write('\n');
    for (Object[] row : rows) {
      w.write(Arrays.stream(row).map(PersonFiles::cell).collect(Collectors.joining("\t")));
      w.write('\n');
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

- [ ] **Step 5: Retired persons in `PersonForms` and `MemoryPersonStore`**

In `PersonForms`, both `of` methods start with the retired check, and the class javadoc gains the sentence
"A retired person derives nothing: it is found by its ids, and by curated forms only.":

```java
  public static List<String> of(Person p) {
    if (p.family() == null || p.retired() != null) return List.of();
```

```java
  public static String of(Person p, PersonName n) {
    if (p.family() == null || p.retired() != null || (n.kind() != PersonNameKind.FULL && n.kind() != PersonNameKind.VARIANT)) {
      return null;
    }
```

In `MemoryPersonStore` replace the "has no name" loop by:

```java
    for (Person p : c.persons()) {
      if (p.id() == null) continue;
      // a retired person lost its harvested forms and may have no other
      if (p.retired() == null && !named.contains(p)) {
        problems.add(p.id() + " has no name");
      }
      if (p.successor() != null && !byId.containsKey(p.successor())) {
        problems.add(p.id() + " has an unknown successor " + p.successor());
      }
    }
```

- [ ] **Step 6: Run the tests**

```bash
mvn -o -q -pl dao -am install -DskipTests
mvn -o -pl dao test -Dtest='PersonKeysTest,MemoryPersonStoreTest,PersonFilesTest' -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t3.log 2>&1; tail -30 /tmp/t3.log
mvn -o -pl core test -Dtest='life/catalogue/matching/person/**/*Test.java' -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t3c.log 2>&1; tail -30 /tmp/t3c.log
```

Expected: all PASS; the committed files still read (legacy header) in `PersonRegistryFilesTest`.

- [ ] **Step 7: Commit**

```bash
git add -A api dao core webservice docs && git commit -m "feat(persons): retired persons, successors, the registry files as a zip

A retired person keeps its ids but derives no forms and needs no name; a successor must resolve. The files gain the
retired and successor columns, still read the phase 3 header, and read and write as a zip for import and export.

Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg"
```

---

### Task 4: The tables and their writer

**Files:**
- Modify: `dao/src/main/resources/life/catalogue/db/dbschema.sql`, `dbschema.md`, `dao/src/test/java/life/catalogue/junit/TestDataRule.java`
- Create: `dao/src/main/java/life/catalogue/db/mapper/PersonMapper.java`, `dao/src/main/resources/life/catalogue/db/mapper/PersonMapper.xml`
- Create: `dao/src/main/java/life/catalogue/matching/person/PersonTables.java`
- Test: `dao/src/test/java/life/catalogue/matching/person/PersonTablesTest.java`

**Interfaces:**
- Consumes: `PersonFiles.Content`, `PersonForms`, `PersonKeys`, `MemoryPersonStore.problems()`, `InitDbUtils.toPgConnection(Connection)`.
- Produces:
  - `PersonMapper` with nested `PersonRow` (public fields, `toPerson()`), `NameRow` (`toName()`), `RelationRow`
    (`toRelation()`), `KeyId`, and `List<PersonRow> list()`, `List<NameRow> listNames()` (no `DERIVED`),
    `List<RelationRow> listRelations()`.
  - `PersonTables.lock(SqlSession)`, `PersonTables.read(SqlSession)` → `Content`,
    `PersonTables.write(SqlSession, Content)` (caller locks and commits), `PersonTables.replace(SqlSessionFactory, Content)`
    (checks, locks, writes, commits; `IllegalArgumentException` for an inconsistent registry). All throw `SQLException`.

- [ ] **Step 1: Write the failing test**

`dao/src/test/java/life/catalogue/matching/person/PersonTablesTest.java`:

```java
package life.catalogue.matching.person;

import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonName;
import life.catalogue.api.model.PersonRelation;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static life.catalogue.api.vocab.PersonFormCode.*;
import static life.catalogue.api.vocab.PersonNameKind.*;
import static life.catalogue.api.vocab.PersonRelationType.PARENT;
import static life.catalogue.api.vocab.PersonSource.*;
import static org.junit.Assert.*;

public class PersonTablesTest {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.empty();

  static final Person SOWERBY1 = new Person("wd:Q1", "Q1", null, null, List.of(), "Sowerby", "George Brettingham", "I",
    1788, 1854, null, null, Set.of(), WIKIDATA);
  static final Person SOWERBY2 = new Person("wd:Q2", "Q2", "9936-1", null, List.of("ipni:9936-1"), "Sowerby",
    "George Brettingham", "II", 1812, 1884, null, null, Set.of(TaxGroup.Molluscs, TaxGroup.Angiosperms), WIKIDATA);
  static final Person DOE = new Person("clb:1", null, null, null, List.of(), "Doe", "Ann", null, null, null, null, null,
    Set.of(), CURATED, LocalDate.of(2026, 9, 24), null);

  static PersonFiles.Content content() {
    return new PersonFiles.Content(List.of(SOWERBY1, SOWERBY2, DOE),
      List.of(new PersonName("wd:Q1", "G.B.Sowerby I", CITATION, ZOO, WIKIDATA),
        // by a former id, written under the person's own
        new PersonName("ipni:9936-1", "G.B.Sowerby II", CITATION, ZOO, CURATED),
        new PersonName("clb:1", "Ann Doe", FULL, ANY, CURATED)),
      List.of(new PersonRelation("ipni:9936-1", PARENT, "wd:Q1", WIKIDATA)));
  }

  static SqlSessionFactory factory() {
    return SqlSessionFactoryRule.getSqlSessionFactory();
  }

  static PersonFiles.Content read() {
    try (SqlSession session = factory().openSession(true)) {
      return PersonTables.read(session);
    }
  }

  static List<String> column(String sql) throws SQLException {
    try (SqlSession session = factory().openSession(true);
         Statement st = session.getConnection().createStatement();
         ResultSet rs = st.executeQuery(sql)) {
      List<String> values = new ArrayList<>();
      while (rs.next()) {
        values.add(rs.getString(1));
      }
      return values;
    }
  }

  /** references by any id come back by the person's own id, no derived form comes back */
  @Test
  public void roundTrip() throws Exception {
    PersonTables.replace(factory(), content());
    var c = read();
    assertEquals(Set.of(SOWERBY1, SOWERBY2, DOE), Set.copyOf(c.persons()));
    assertEquals(Set.of(new PersonName("wd:Q1", "G.B.Sowerby I", CITATION, ZOO, WIKIDATA),
        new PersonName("wd:Q2", "G.B.Sowerby II", CITATION, ZOO, CURATED),
        new PersonName("clb:1", "Ann Doe", FULL, ANY, CURATED)),
      Set.copyOf(c.names()));
    assertEquals(List.of(new PersonRelation("wd:Q2", PARENT, "wd:Q1", WIKIDATA)), c.relations());
  }

  @Test
  public void derivedFormsAndIds() throws Exception {
    PersonTables.replace(factory(), content());
    assertEquals(List.of("G. B. Sowerby II", "Sowerby", "Sowerby II"),
      column("SELECT form FROM person_name WHERE person_id = 'wd:Q2' AND kind = 'DERIVED' ORDER BY form"));
    assertEquals(List.of(PersonKeys.key("G.B.Sowerby II")), column("SELECT key FROM person_name WHERE form = 'G.B.Sowerby II'"));
    assertEquals(List.of("ipni:9936-1", "wd:Q2"), column("SELECT any_id FROM person_id WHERE person_id = 'wd:Q2' ORDER BY any_id"));
    // a retired person derives nothing
    assertEquals(List.of(), column("SELECT form FROM person_name WHERE person_id = 'clb:1' AND kind = 'DERIVED'"));
  }

  /** derived forms are computed anew on every write: a changed given name changes the initials */
  @Test
  public void derivedFormsFollowThePerson() throws Exception {
    PersonTables.replace(factory(), content());
    var renamed = new Person("wd:Q2", "Q2", "9936-1", null, List.of("ipni:9936-1"), "Sowerby", "James", "II", 1812, 1884,
      null, null, Set.of(TaxGroup.Molluscs, TaxGroup.Angiosperms), WIKIDATA);
    PersonTables.replace(factory(), new PersonFiles.Content(List.of(SOWERBY1, renamed, DOE), content().names(),
      content().relations()));
    assertEquals(List.of("J. Sowerby II", "Sowerby", "Sowerby II"),
      column("SELECT form FROM person_name WHERE person_id = 'wd:Q2' AND kind = 'DERIVED' ORDER BY form"));
  }

  /** a rewrite keeps when a person was created, and when it was modified unless it changed */
  @Test
  public void timestamps() throws Exception {
    PersonTables.replace(factory(), content());
    var first = stamps();
    PersonTables.replace(factory(), content());
    assertEquals(first, stamps());

    var changed = new ArrayList<>(content().persons());
    changed.set(0, new Person("wd:Q1", "Q1", null, null, List.of(), "Sowerby", "George Brettingham", "I", 1788, 1855, null,
      null, Set.of(), WIKIDATA));
    PersonTables.replace(factory(), new PersonFiles.Content(changed, content().names(), content().relations()));
    var after = stamps();
    assertEquals(first.get("wd:Q1").get(0), after.get("wd:Q1").get(0));
    assertNotEquals(first.get("wd:Q1").get(1), after.get("wd:Q1").get(1));
    assertEquals(first.get("wd:Q2"), after.get("wd:Q2"));
  }

  private static Map<String, List<String>> stamps() throws SQLException {
    Map<String, List<String>> stamps = new HashMap<>();
    for (String row : column("SELECT id || '|' || created || '|' || modified FROM person")) {
      String[] parts = row.split("\\|");
      stamps.put(parts[0], List.of(parts[1], parts[2]));
    }
    return stamps;
  }

  /** an inconsistent registry is refused whole, the tables keep what they held */
  @Test
  public void inconsistentIsRefused() throws Exception {
    PersonTables.replace(factory(), content());
    var bad = new PersonFiles.Content(List.of(SOWERBY1),
      List.of(new PersonName("wd:Q404", "Nobody", FULL, ANY, CURATED)), List.of());
    var e = assertThrows(IllegalArgumentException.class, () -> PersonTables.replace(factory(), bad));
    assertTrue(e.getMessage(), e.getMessage().contains("unknown person wd:Q404"));
    assertEquals(3, read().persons().size());
  }

  /** quotes, commas, apostrophes and backslashes survive the copy and its array literals */
  @Test
  public void quoting() throws Exception {
    var odd = new Person("wd:Q5", "Q5", null, null, List.of("clb:\"5\\"), "O'Brien, Jr", "Ann \"Nan\"", null, null, null,
      null, null, Set.of(TaxGroup.Plants), CURATED);
    var c = new PersonFiles.Content(List.of(odd),
      List.of(new PersonName("wd:Q5", "d'Orb., \"x\" \\ y", VARIANT, ANY, CURATED)), List.of());
    PersonTables.replace(factory(), c);
    var read = read();
    assertEquals(List.of(odd), read.persons());
    assertEquals(c.names(), read.names());
  }
}
```

- [ ] **Step 2: Run it to see it fail**

```bash
mvn -o -pl dao test-compile > /tmp/t4.log 2>&1; grep ERROR /tmp/t4.log | head -5
```

Expected: compilation FAILS - `PersonTables` does not exist.

- [ ] **Step 3: The tables**

In `dbschema.sql` insert after `CREATE UNIQUE INDEX names_index_normalized_idx ON names_index (normalized);`:

```sql

-- the person registry of author matching, global: persons with Wikidata, IPNI and ZooBank ids
CREATE TABLE person (
  id TEXT PRIMARY KEY,
  wikidata TEXT,
  ipni TEXT,
  zoobank TEXT,
  former_ids TEXT[],
  family TEXT,
  given TEXT,
  suffix TEXT,
  born INTEGER,
  died INTEGER,
  active_from INTEGER,
  active_to INTEGER,
  groups TAXGROUP[],
  source PERSONSOURCE NOT NULL,
  retired DATE,
  successor TEXT,
  created TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
  modified TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now()
);

-- every id a person answers to: its own, its former ids and its prefixed authority ids
CREATE TABLE person_id (
  any_id TEXT PRIMARY KEY,
  person_id TEXT NOT NULL REFERENCES person ON DELETE CASCADE
);
CREATE INDEX ON person_id (person_id);

-- the key is the form folded as citations are, computed in java
CREATE TABLE person_name (
  person_id TEXT NOT NULL REFERENCES person ON DELETE CASCADE,
  form TEXT NOT NULL,
  kind PERSONNAMEKIND NOT NULL,
  code PERSONFORMCODE NOT NULL,
  source PERSONSOURCE NOT NULL,
  key TEXT
);
CREATE INDEX ON person_name (key);
CREATE INDEX ON person_name (person_id);

CREATE TABLE person_relation (
  person_id TEXT NOT NULL REFERENCES person ON DELETE CASCADE,
  relation PERSONRELATIONTYPE NOT NULL,
  other_id TEXT NOT NULL REFERENCES person ON DELETE CASCADE,
  source PERSONSOURCE NOT NULL,
  PRIMARY KEY (person_id, relation, other_id)
);
CREATE INDEX ON person_relation (other_id);
```

In `dbschema.md`, in the `2026-09-24 the person registry` section, append the same four `CREATE TABLE` and four
`CREATE INDEX` statements to its sql block, and replace the sentence under it by:

```markdown
The person registry of author matching moves from files in the code into the database, see
`docs/2026-09-24-person-registry-service.md`. The tables start empty: after the deploy an admin imports the registry
of the branch with `POST /admin/persons/import` (the zip of `core/src/test/resources/authorship/persons/`), then starts
a first `POST /admin/persons/harvest` and reads its report against the imported state.
```

In `TestDataRule.truncate`, after `st.execute("TRUNCATE names_index RESTART IDENTITY CASCADE");` add:

```java
      st.execute("TRUNCATE person CASCADE");
```

- [ ] **Step 4: The mapper**

`dao/src/main/java/life/catalogue/db/mapper/PersonMapper.java`:

```java
package life.catalogue.db.mapper;

import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonName;
import life.catalogue.api.model.PersonRelation;
import life.catalogue.api.vocab.PersonFormCode;
import life.catalogue.api.vocab.PersonNameKind;
import life.catalogue.api.vocab.PersonRelationType;
import life.catalogue.api.vocab.PersonSource;
import life.catalogue.api.vocab.TaxGroup;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * The person registry tables. Rows are plain classes as MyBatis maps no records; they are written by
 * {@link life.catalogue.matching.person.PersonTables} with COPY, never by this mapper.
 */
public interface PersonMapper {

  class PersonRow {
    /** the id the person was asked for by, set by the lookups by any id only */
    public String anyId;
    public String id;
    public String wikidata;
    public String ipni;
    public String zoobank;
    public List<String> formerIds;
    public String family;
    public String given;
    public String suffix;
    public Integer born;
    public Integer died;
    public Integer activeFrom;
    public Integer activeTo;
    public Set<TaxGroup> groups;
    public PersonSource source;
    public LocalDate retired;
    public String successor;
    public LocalDateTime created;
    public LocalDateTime modified;

    public Person toPerson() {
      return new Person(id, wikidata, ipni, zoobank, formerIds == null ? List.of() : List.copyOf(formerIds), family, given,
        suffix, born, died, activeFrom, activeTo, groups == null ? Set.of() : Set.copyOf(groups), source, retired, successor);
    }
  }

  class NameRow {
    public String personId;
    public String form;
    public PersonNameKind kind;
    public PersonFormCode code;
    public PersonSource source;

    public PersonName toName() {
      return new PersonName(personId, form, kind, code, source);
    }
  }

  class RelationRow {
    public String personId;
    public PersonRelationType relation;
    public String otherId;
    public PersonSource source;

    public PersonRelation toRelation() {
      return new PersonRelation(personId, relation, otherId, source);
    }
  }

  class KeyId {
    public String key;
    public String personId;
  }

  /**
   * @return every person
   */
  List<PersonRow> list();

  /**
   * @return every name form but the derived ones
   */
  List<NameRow> listNames();

  List<RelationRow> listRelations();
}
```

`dao/src/main/resources/life/catalogue/db/mapper/PersonMapper.xml`:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper
  PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
  "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="life.catalogue.db.mapper.PersonMapper">

  <sql id="PERSON_COLS">
    p.id, p.wikidata, p.ipni, p.zoobank, p.former_ids, p.family, p.given, p.suffix, p.born, p.died, p.active_from,
    p.active_to, p.groups, p.source, p.retired, p.successor, p.created, p.modified
  </sql>

  <resultMap id="personResultMap" type="life.catalogue.db.mapper.PersonMapper$PersonRow" autoMapping="true">
    <result property="formerIds" column="former_ids" typeHandler="life.catalogue.db.type.StringArrayTypeHandler"/>
    <result property="groups" column="groups" typeHandler="life.catalogue.db.type2.TaxGroupSetTypeHandler"/>
  </resultMap>

  <select id="list" resultMap="personResultMap">
    SELECT <include refid="PERSON_COLS"/>
    FROM person p
  </select>

  <select id="listNames" resultType="life.catalogue.db.mapper.PersonMapper$NameRow">
    SELECT person_id, form, kind, code, source
    FROM person_name
    WHERE kind != 'DERIVED'
  </select>

  <select id="listRelations" resultType="life.catalogue.db.mapper.PersonMapper$RelationRow">
    SELECT person_id, relation, other_id, source
    FROM person_relation
  </select>
</mapper>
```

- [ ] **Step 5: `PersonTables`**

```java
package life.catalogue.matching.person;

import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonName;
import life.catalogue.api.model.PersonRelation;
import life.catalogue.api.vocab.PersonFormCode;
import life.catalogue.api.vocab.PersonNameKind;
import life.catalogue.db.InitDbUtils;
import life.catalogue.db.mapper.PersonMapper;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.postgresql.copy.CopyIn;
import org.postgresql.copy.CopyManager;

/**
 * Reads and replaces the person registry in Postgres: the tables person, person_id, person_name and person_relation.
 * A write replaces every row in the caller's transaction, so readers see the old registry until it commits. The any id
 * index and the derived forms are computed here, from the persons and their names; names and relations may refer to a
 * person by any of its ids and are written with its own.
 */
public final class PersonTables {
  private static final List<String> TABLES = List.of("person_relation", "person_name", "person_id", "person");

  private PersonTables() {
  }

  /**
   * Keeps other writers out, not readers, until the transaction ends.
   */
  public static void lock(SqlSession session) throws SQLException {
    try (Statement st = session.getConnection().createStatement()) {
      st.execute("LOCK TABLE person, person_id, person_name, person_relation IN EXCLUSIVE MODE");
    }
  }

  /**
   * @return the registry as its files hold it: names and relations by the persons' own ids, no derived forms
   */
  public static PersonFiles.Content read(SqlSession session) {
    var mapper = session.getMapper(PersonMapper.class);
    return new PersonFiles.Content(
      mapper.list().stream().map(PersonMapper.PersonRow::toPerson).toList(),
      mapper.listNames().stream().map(PersonMapper.NameRow::toName).toList(),
      mapper.listRelations().stream().map(PersonMapper.RelationRow::toRelation).toList());
  }

  /**
   * Checks the registry as {@link MemoryPersonStore#problems()} does and replaces the tables by it in one transaction.
   *
   * @throws IllegalArgumentException for an inconsistent registry, nothing written
   */
  public static void replace(SqlSessionFactory factory, PersonFiles.Content c) throws SQLException {
    List<String> problems = new MemoryPersonStore(c).problems();
    if (!problems.isEmpty()) {
      throw new IllegalArgumentException("The person registry is inconsistent, nothing written: "
        + String.join("; ", problems.subList(0, Math.min(20, problems.size()))));
    }
    try (SqlSession session = factory.openSession(false)) {
      try {
        lock(session);
        write(session, c);
        // forced: the writes went past MyBatis, which would otherwise neither commit nor roll back
        session.commit(true);
      } catch (SQLException | RuntimeException e) {
        session.rollback(true);
        throw e;
      }
    }
  }

  /**
   * Replaces every row by the registry, its derived forms and every id of its persons. The caller holds the lock and
   * commits. A person keeps its created timestamp and, unless its row changed, its modified one.
   */
  public static void write(SqlSession session, PersonFiles.Content c) throws SQLException {
    Map<String, PersonMapper.PersonRow> before = new HashMap<>();
    session.getMapper(PersonMapper.class).list().forEach(r -> before.put(r.id, r));
    Map<String, String> own = new HashMap<>();
    c.persons().forEach(p -> p.allIds().forEach(id -> own.put(id, p.id())));
    try (Statement st = session.getConnection().createStatement()) {
      for (String table : TABLES) {
        st.executeUpdate("DELETE FROM " + table);
      }
    }
    CopyManager mgr = new CopyManager(InitDbUtils.toPgConnection(session.getConnection()));
    LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    try (Copy copy = new Copy(mgr, "person(id, wikidata, ipni, zoobank, former_ids, family, given, suffix, born, died, "
      + "active_from, active_to, groups, source, retired, successor, created, modified)")) {
      for (Person p : c.persons()) {
        var b = before.get(p.id());
        LocalDateTime created = b == null ? now : b.created;
        LocalDateTime modified = b != null && b.toPerson().equals(p) ? b.modified : now;
        copy.row(p.id(), p.wikidata(), p.ipni(), p.zoobank(), p.formerIds(), p.family(), p.given(), p.suffix(), p.born(),
          p.died(), p.activeFrom(), p.activeTo(), p.groups().stream().map(Enum::name).sorted().toList(), p.source(),
          p.retired(), p.successor(), created, modified);
      }
      copy.end();
    }
    try (Copy copy = new Copy(mgr, "person_id(any_id, person_id)")) {
      for (var e : own.entrySet()) {
        copy.row(e.getKey(), e.getValue());
      }
      copy.end();
    }
    Map<String, List<PersonName>> names = new HashMap<>();
    for (PersonName n : c.names()) {
      String id = own.get(n.person());
      if (id == null) {
        throw new IllegalArgumentException("name " + n.form() + " refers to unknown person " + n.person());
      }
      names.computeIfAbsent(id, k -> new ArrayList<>()).add(n);
    }
    try (Copy copy = new Copy(mgr, "person_name(person_id, form, kind, code, source, key)")) {
      for (Person p : c.persons()) {
        List<PersonName> forms = names.getOrDefault(p.id(), List.of());
        // the keys the person has a form of every code under, which no derived form needs to repeat
        Set<String> any = new HashSet<>();
        for (PersonName n : forms) {
          String key = PersonKeys.key(n.form());
          copy.row(p.id(), n.form(), n.kind(), n.code(), n.source(), key);
          if (key != null && n.code() == PersonFormCode.ANY) {
            any.add(key);
          }
        }
        List<String> derived = new ArrayList<>(PersonForms.of(p));
        for (PersonName n : forms) {
          String d = PersonForms.of(p, n);
          if (d != null) {
            derived.add(d);
          }
        }
        for (String form : derived) {
          String key = PersonKeys.key(form);
          if (key != null && any.add(key)) {
            copy.row(p.id(), form, PersonNameKind.DERIVED, PersonFormCode.ANY, p.source(), key);
          }
        }
      }
      copy.end();
    }
    Set<List<Object>> seen = new HashSet<>();
    try (Copy copy = new Copy(mgr, "person_relation(person_id, relation, other_id, source)")) {
      for (PersonRelation r : c.relations()) {
        String a = own.get(r.person());
        String b = own.get(r.other());
        if (a == null || b == null) {
          throw new IllegalArgumentException("relation " + r.person() + " " + r.relation() + " " + r.other()
            + " refers to unknown person " + (a == null ? r.person() : r.other()));
        }
        if (seen.add(List.of(a, r.relation(), b))) {
          copy.row(a, r.relation(), b, r.source());
        }
      }
      copy.end();
    }
  }

  /**
   * One COPY in csv, fed row by row. Closing it without {@link #end()} cancels it.
   */
  private static final class Copy implements AutoCloseable {
    private final CopyIn in;

    Copy(CopyManager mgr, String table) throws SQLException {
      in = mgr.copyIn("COPY " + table + " FROM STDIN WITH (FORMAT csv)");
    }

    void row(Object... cells) throws SQLException {
      byte[] line = csv(cells).getBytes(StandardCharsets.UTF_8);
      in.writeToCopy(line, 0, line.length);
    }

    void end() throws SQLException {
      in.endCopy();
    }

    @Override
    public void close() throws SQLException {
      if (in.isActive()) {
        in.cancelCopy();
      }
    }
  }

  /**
   * @return a csv line, every value quoted and an unquoted empty cell for null, which COPY reads as NULL
   */
  static String csv(Object[] cells) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < cells.length; i++) {
      if (i > 0) sb.append(',');
      Object x = cells[i];
      if (x == null) continue;
      String s = x instanceof Collection<?> values ? array(values) : x instanceof Enum<?> e ? e.name() : x.toString();
      sb.append('"').append(s.replace("\"", "\"\"")).append('"');
    }
    return sb.append('\n').toString();
  }

  /**
   * @return a Postgres array literal with every element quoted: {"wd:Q1","ipni:1-1"}
   */
  static String array(Collection<?> values) {
    return values.stream()
      .map(v -> '"' + v.toString().replace("\\", "\\\\").replace("\"", "\\\"") + '"')
      .collect(Collectors.joining(",", "{", "}"));
  }
}
```

- [ ] **Step 6: Run the tests**

```bash
mvn -o -pl dao test -Dtest='PersonTablesTest,PgSetupRuleTest' -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t4.log 2>&1; tail -30 /tmp/t4.log
```

Expected: all PASS.

- [ ] **Step 7: Commit**

```bash
git add -A api dao core webservice docs && git commit -m "feat(persons): person tables, mapper and a delete-and-copy writer

The registry lives in person, person_id, person_name and person_relation. PersonTables writes it in the caller's
transaction by COPY, deriving the DERIVED forms and the any id index, resolving references to own ids and keeping
created and modified timestamps; replace checks a registry before it locks and writes.

Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg"
```

---

### Task 5: `PgPersonStore`, its caches and the `PersonsChanged` event

**Files:**
- Create: `api/src/main/java/life/catalogue/api/event/PersonsChanged.java`, `PersonListener.java`
- Create: `dao/src/main/java/life/catalogue/config/PersonConfig.java`, `dao/src/main/java/life/catalogue/matching/person/PgPersonStore.java`
- Modify: `PersonMapper.java`, `PersonMapper.xml`, `dao/src/main/java/life/catalogue/event/EventBroker.java`, `EventKryoPool.java`
- Test: `dao/src/test/java/life/catalogue/matching/person/PgPersonStoreTest.java`, `dao/src/test/java/life/catalogue/event/EventKryoPoolTest.java`

**Interfaces:**
- Consumes: Task 4's `PersonTables.replace`, `PersonMapper` rows; Task 2's `PersonStore`.
- Produces:
  - `PersonsChanged(int user)` with a public field `user`, a no-arg constructor, `equals`/`hashCode`;
    `interface PersonListener extends Listener { void personsChanged(PersonsChanged event); }`
  - `PersonConfig` with public fields `int keyCacheSize = 100_000`, `int personCacheSize = 50_000`,
    `int cacheExpireMinutes = 60`, `File harvestDir = new File("/tmp/col/person-harvest")`, `int harvestIntervalDays = 0`
  - `PgPersonStore(SqlSessionFactory factory, PersonConfig cfg) implements PersonStore, PersonListener`
  - `PersonMapper.getByAnyIds(@Param("ids") Collection<String>)`, `idsByKeys(@Param("keys") Collection<String>, @Param("zoo") boolean)`,
    `keys(@Param("id") String, @Param("zoo") boolean)`, `relatives(@Param("id") String)`, `names(@Param("id") String)`,
    `relations(@Param("id") String)`

- [ ] **Step 1: Write the failing tests**

`dao/src/test/java/life/catalogue/matching/person/PgPersonStoreTest.java`:

```java
package life.catalogue.matching.person;

import life.catalogue.api.event.PersonsChanged;
import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonName;
import life.catalogue.api.model.PersonRelation;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.api.vocab.Users;
import life.catalogue.config.PersonConfig;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.TestDataRule;

import org.gbif.nameparser.api.NomCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static life.catalogue.api.vocab.PersonFormCode.*;
import static life.catalogue.api.vocab.PersonNameKind.*;
import static life.catalogue.api.vocab.PersonRelationType.PARENT;
import static life.catalogue.api.vocab.PersonSource.*;
import static life.catalogue.matching.person.PersonTablesTest.*;
import static org.junit.Assert.*;

public class PgPersonStoreTest {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.empty();

  static final Person SWARTZ = new Person("wd:Q3", "Q3", "10103-1", null, List.of(), "Swartz", "Olof", null, 1760, 1818, null,
    null, Set.of(TaxGroup.Angiosperms), WIKIDATA);

  static PersonFiles.Content content(Person swartz) {
    return new PersonFiles.Content(List.of(SOWERBY1, SOWERBY2, swartz, DOE),
      List.of(new PersonName("wd:Q1", "G.B.Sowerby I", CITATION, ZOO, WIKIDATA),
        new PersonName("wd:Q2", "G.B.Sowerby II", CITATION, ZOO, WIKIDATA),
        new PersonName("wd:Q3", "Sw.", STANDARD, BOT, IPNI),
        new PersonName("wd:Q3", "Olof Swartz", FULL, ANY, WIKIDATA),
        new PersonName("clb:1", "Ann Doe", FULL, ANY, CURATED)),
      List.of(new PersonRelation("wd:Q2", PARENT, "wd:Q1", WIKIDATA)));
  }

  private PgPersonStore store;

  @Before
  public void init() throws Exception {
    PersonTables.replace(factory(), content(SWARTZ));
    store = new PgPersonStore(factory(), new PersonConfig());
  }

  @Test
  public void byKeyAndCode() {
    assertEquals(Set.of(SWARTZ), store.byKey("sw", NomCode.BOTANICAL));
    assertEquals(Set.of(SWARTZ), store.byKey("sw", null));
    assertEquals(Set.of(), store.byKey("sw", NomCode.ZOOLOGICAL));
    assertEquals(Set.of(SOWERBY1, SOWERBY2), store.candidates("Sowerby", NomCode.ZOOLOGICAL));
    assertEquals(Set.of(SOWERBY2), store.candidates("G. B. Sowerby II", NomCode.BOTANICAL));
  }

  @Test
  public void byKeys() {
    assertEquals(Map.of("sw", Set.of(SWARTZ), "sowerby", Set.of(SOWERBY1, SOWERBY2)),
      store.byKeys(List.of("sw", "sowerby", "nobody"), NomCode.BOTANICAL));
    // no key, no select
    assertEquals(Map.of(), store.byKeys(List.of(), null));
    assertEquals(Set.of(), store.candidates(".", null));
  }

  /** any id finds the person, and a person reached by two ids is still one candidate */
  @Test
  public void anyId() {
    assertEquals(SOWERBY2, store.get("wd:Q2"));
    assertEquals(SOWERBY2, store.get("ipni:9936-1"));
    assertNull(store.get("wd:Q404"));
    assertEquals(1, store.candidates("G.B.Sowerby II", NomCode.ZOOLOGICAL).size());
  }

  @Test
  public void relativesAndKeys() {
    assertEquals(Set.of(SOWERBY1), store.relatives(SOWERBY2));
    assertEquals(Set.of(SOWERBY2), store.relatives(SOWERBY1));
    assertEquals(Set.of(), store.relatives(SWARTZ));
    assertEquals(Set.of("sw", "olof swartz", "o swartz", "swartz"), store.keys(SWARTZ, NomCode.BOTANICAL));
    assertEquals(Set.of("olof swartz", "o swartz", "swartz"), store.keys(SWARTZ, NomCode.ZOOLOGICAL));
  }

  @Test
  public void retiredIsFoundByIdAndCuratedForms() {
    assertEquals(DOE, store.get("clb:1"));
    assertEquals(Set.of(DOE), store.candidates("Ann Doe", null));
    assertEquals(Set.of(), store.candidates("Doe", null));
  }

  @Test
  public void infoWithoutDerivedForms() {
    var info = store.info("ipni:9936-1");
    assertEquals(SOWERBY2, info.person());
    assertEquals(List.of(new PersonName("wd:Q2", "G.B.Sowerby II", CITATION, ZOO, WIKIDATA)), info.names());
    assertEquals(List.of(new PersonRelation("wd:Q2", PARENT, "wd:Q1", WIKIDATA)), info.relations());
    assertNull(store.info("wd:Q404"));
  }

  /** the caches answer until the registry announces a change, whatever the tables hold meanwhile */
  @Test
  public void cachesClearedOnPersonsChanged() throws Exception {
    assertEquals(Set.of(SWARTZ), store.byKey("sw", NomCode.BOTANICAL));
    var olaf = new Person("wd:Q3", "Q3", "10103-1", null, List.of(), "Swartz", "Olaf", null, 1760, 1818, null, null,
      Set.of(TaxGroup.Angiosperms), WIKIDATA);
    PersonTables.replace(factory(), content(olaf));
    assertEquals(Set.of(SWARTZ), store.byKey("sw", NomCode.BOTANICAL));
    store.personsChanged(new PersonsChanged(Users.TESTER));
    assertEquals(Set.of(olaf), store.byKey("sw", NomCode.BOTANICAL));
  }
}
```

In `EventKryoPoolTest`, add to the first test: `assertSerde(kryo, new PersonsChanged(18));` (import
`life.catalogue.api.event.PersonsChanged` if the wildcard does not cover it).

- [ ] **Step 2: Run them to see them fail**

```bash
mvn -o -pl dao test-compile > /tmp/t5.log 2>&1; grep ERROR /tmp/t5.log | head -5
```

Expected: compilation FAILS - `PersonsChanged`, `PersonConfig`, `PgPersonStore` do not exist.

- [ ] **Step 3: The event and listener**

`api/src/main/java/life/catalogue/api/event/PersonsChanged.java`:

```java
package life.catalogue.api.event;

import java.util.Objects;

/**
 * The person registry was rewritten, by a harvest or an import: every cache of it is stale.
 */
public class PersonsChanged implements Event {
  public int user;

  public PersonsChanged() {
  }

  public PersonsChanged(int user) {
    this.user = user;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof PersonsChanged that && user == that.user;
  }

  @Override
  public int hashCode() {
    return Objects.hash(user);
  }

  @Override
  public String toString() {
    return "PersonsChanged{user=" + user + '}';
  }
}
```

`api/src/main/java/life/catalogue/api/event/PersonListener.java`:

```java
package life.catalogue.api.event;

public interface PersonListener extends Listener {

  void personsChanged(PersonsChanged event);

}
```

In `EventBroker`: add the field `private final List<PersonListener> personListeners = new ArrayList<>();`, in
`register` add

```java
    if (listener instanceof PersonListener) {
      personListeners.add((PersonListener) listener);
    }
```

and in `broker(Object obj)` insert before the final `} else {` with `LOG.error("Unknown event type: "`:

```java
      } else if (obj instanceof PersonsChanged) {
        PersonsChanged event = (PersonsChanged) obj;
        for (PersonListener l : personListeners) {
          try {
            l.personsChanged(event);
          } catch (Exception e) {
            LOG.error("Failed to broker persons change event: {}", event, e);
          }
        }
```

In `EventKryoPool`, after `kryo.register(JobStatus.class);` add `kryo.register(PersonsChanged.class);` - last, so the
ids of all prior classes stay stable.

- [ ] **Step 4: `PersonConfig`**

`dao/src/main/java/life/catalogue/config/PersonConfig.java`:

```java
package life.catalogue.config;

import java.io.File;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The person registry: its caches and its harvest.
 */
public class PersonConfig {

  /** citation keys whose person ids are kept in memory */
  @Min(0)
  public int keyCacheSize = 100_000;

  /** persons kept in memory */
  @Min(0)
  public int personCacheSize = 50_000;

  /** minutes a cached key or person is kept at most, in case a PersonsChanged event was missed */
  @Min(1)
  public int cacheExpireMinutes = 60;

  /** where a harvest caches the answers of its sources: kept after a failed run for the next to resume from */
  @NotNull
  public File harvestDir = new File("/tmp/col/person-harvest");

  /** days between harvests the cron executor starts, 0 for none */
  @Min(0)
  public int harvestIntervalDays = 0;
}
```

- [ ] **Step 5: The lookups of the mapper**

Add to `PersonMapper` (import `java.util.Collection`, `org.apache.ibatis.annotations.Param`):

```java
  /**
   * @param ids any ids, never empty
   * @return the persons with the id each was asked for by in {@link PersonRow#anyId}
   */
  List<PersonRow> getByAnyIds(@Param("ids") Collection<String> ids);

  /**
   * @param keys never empty
   * @param zoo  true for zoological names, which ZOO and ANY forms cite; BOT and ANY forms cite all others
   */
  List<KeyId> idsByKeys(@Param("keys") Collection<String> keys, @Param("zoo") boolean zoo);

  List<String> keys(@Param("id") String id, @Param("zoo") boolean zoo);

  /**
   * @return the ids of parents, children and siblings
   */
  List<String> relatives(@Param("id") String id);

  /**
   * @return the forms of a person, derived ones excluded
   */
  List<NameRow> names(@Param("id") String id);

  /**
   * @return the relations with the person on either end
   */
  List<RelationRow> relations(@Param("id") String id);
```

Add to `PersonMapper.xml` before `</mapper>`:

```xml
  <sql id="CODES">
    code IN ('ANY', <choose><when test="zoo">'ZOO'</when><otherwise>'BOT'</otherwise></choose>)
  </sql>

  <select id="getByAnyIds" resultMap="personResultMap">
    SELECT i.any_id, <include refid="PERSON_COLS"/>
    FROM person_id i JOIN person p ON p.id = i.person_id
    WHERE i.any_id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
  </select>

  <select id="idsByKeys" resultType="life.catalogue.db.mapper.PersonMapper$KeyId">
    SELECT DISTINCT key, person_id
    FROM person_name
    WHERE key IN <foreach collection="keys" item="k" open="(" separator="," close=")">#{k}</foreach>
      AND <include refid="CODES"/>
    ORDER BY key, person_id
  </select>

  <select id="keys" resultType="string">
    SELECT DISTINCT key
    FROM person_name
    WHERE person_id = #{id} AND key IS NOT NULL AND <include refid="CODES"/>
  </select>

  <select id="relatives" resultType="string">
    SELECT other_id FROM person_relation WHERE person_id = #{id}
    UNION
    SELECT person_id FROM person_relation WHERE other_id = #{id}
  </select>

  <select id="names" resultType="life.catalogue.db.mapper.PersonMapper$NameRow">
    SELECT person_id, form, kind, code, source
    FROM person_name
    WHERE person_id = #{id} AND kind != 'DERIVED'
    ORDER BY kind, code, form
  </select>

  <select id="relations" resultType="life.catalogue.db.mapper.PersonMapper$RelationRow">
    SELECT person_id, relation, other_id, source
    FROM person_relation
    WHERE person_id = #{id} OR other_id = #{id}
    ORDER BY person_id, relation, other_id
  </select>
```

- [ ] **Step 6: `PgPersonStore`**

```java
package life.catalogue.matching.person;

import life.catalogue.api.event.PersonListener;
import life.catalogue.api.event.PersonsChanged;
import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonInfo;
import life.catalogue.config.PersonConfig;
import life.catalogue.db.mapper.PersonMapper;

import org.gbif.nameparser.api.NomCode;

import java.util.*;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.collect.Lists;

/**
 * The person registry in Postgres. Two bounded caches keep what was asked for: the person ids of a key under a code, and
 * the person of any id. A miss is one indexed select. Both are cleared when the registry announces a change, and every
 * entry expires after a while in case an announcement was missed.
 */
public class PgPersonStore implements PersonStore, PersonListener {
  private static final int BATCH = 1000;
  private final SqlSessionFactory factory;
  private final Cache<String, List<String>> idsByKey;
  private final LoadingCache<String, Person> persons;

  public PgPersonStore(SqlSessionFactory factory, PersonConfig cfg) {
    this.factory = factory;
    idsByKey = Caffeine.newBuilder()
      .maximumSize(cfg.keyCacheSize)
      .expireAfterWrite(cfg.cacheExpireMinutes, TimeUnit.MINUTES)
      .build();
    persons = Caffeine.newBuilder()
      .maximumSize(cfg.personCacheSize)
      .expireAfterWrite(cfg.cacheExpireMinutes, TimeUnit.MINUTES)
      .build(new CacheLoader<>() {
        @Override
        public @Nullable Person load(String anyId) {
          return loadAll(Set.of(anyId)).get(anyId);
        }

        @Override
        public Map<String, Person> loadAll(Set<? extends String> anyIds) {
          Map<String, Person> map = new HashMap<>();
          try (SqlSession session = factory.openSession(true)) {
            var mapper = session.getMapper(PersonMapper.class);
            List<String> ids = new ArrayList<>(anyIds);
            for (List<String> batch : Lists.partition(ids, BATCH)) {
              for (var r : mapper.getByAnyIds(batch)) {
                map.put(r.anyId, r.toPerson());
              }
            }
          }
          return map;
        }
      });
  }

  @Override
  @Nullable
  public Person get(String anyId) {
    return persons.get(anyId);
  }

  @Override
  public Set<Person> byKey(String key, @Nullable NomCode code) {
    return byKeys(List.of(key), code).getOrDefault(key, Set.of());
  }

  @Override
  public Map<String, Set<Person>> byKeys(Collection<String> keys, @Nullable NomCode code) {
    boolean zoo = code == NomCode.ZOOLOGICAL;
    String prefix = zoo ? "Z|" : "B|";
    Map<String, List<String>> ids = new HashMap<>();
    List<String> missing = new ArrayList<>();
    for (String key : new LinkedHashSet<>(keys)) {
      List<String> cached = idsByKey.getIfPresent(prefix + key);
      if (cached == null) {
        missing.add(key);
      } else {
        ids.put(key, cached);
      }
    }
    if (!missing.isEmpty()) {
      Map<String, List<String>> loaded = new HashMap<>();
      missing.forEach(k -> loaded.put(k, new ArrayList<>(1)));
      try (SqlSession session = factory.openSession(true)) {
        var mapper = session.getMapper(PersonMapper.class);
        for (List<String> batch : Lists.partition(missing, BATCH)) {
          for (var r : mapper.idsByKeys(batch, zoo)) {
            loaded.get(r.key).add(r.personId);
          }
        }
      }
      // keys without persons are cached as well, a miss costs a select only once
      loaded.forEach((k, v) -> {
        idsByKey.put(prefix + k, List.copyOf(v));
        ids.put(k, v);
      });
    }
    Set<String> all = new HashSet<>();
    ids.values().forEach(all::addAll);
    Map<String, Person> byId = all.isEmpty() ? Map.of() : persons.getAll(all);
    Map<String, Set<Person>> result = new HashMap<>();
    ids.forEach((k, v) -> {
      Set<Person> ps = new LinkedHashSet<>();
      for (String id : v) {
        Person p = byId.get(id);
        if (p != null) {
          ps.add(p);
        }
      }
      if (!ps.isEmpty()) {
        result.put(k, ps);
      }
    });
    return result;
  }

  @Override
  public Set<Person> relatives(Person p) {
    List<String> ids;
    try (SqlSession session = factory.openSession(true)) {
      ids = session.getMapper(PersonMapper.class).relatives(p.id());
    }
    return ids.isEmpty() ? Set.of() : new LinkedHashSet<>(persons.getAll(ids).values());
  }

  @Override
  public Set<String> keys(Person p, @Nullable NomCode code) {
    try (SqlSession session = factory.openSession(true)) {
      return new LinkedHashSet<>(session.getMapper(PersonMapper.class).keys(p.id(), code == NomCode.ZOOLOGICAL));
    }
  }

  @Override
  @Nullable
  public PersonInfo info(String anyId) {
    Person p = get(anyId);
    if (p == null) return null;
    try (SqlSession session = factory.openSession(true)) {
      var mapper = session.getMapper(PersonMapper.class);
      return new PersonInfo(p,
        mapper.names(p.id()).stream().map(PersonMapper.NameRow::toName).toList(),
        mapper.relations(p.id()).stream().map(PersonMapper.RelationRow::toRelation).toList());
    }
  }

  @Override
  public void personsChanged(PersonsChanged event) {
    idsByKey.invalidateAll();
    persons.invalidateAll();
  }
}
```

- [ ] **Step 7: Run the tests**

```bash
mvn -o -q -pl api install -DskipTests
mvn -o -pl dao test -Dtest='PgPersonStoreTest,PersonTablesTest,EventKryoPoolTest' -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t5.log 2>&1; tail -30 /tmp/t5.log
```

Expected: all PASS.

- [ ] **Step 8: Commit**

```bash
git add -A api dao core webservice docs && git commit -m "feat(persons): PgPersonStore with bounded caches cleared by PersonsChanged

Key and code to person ids and any id to person are cached in Caffeine, 100,000 and 50,000 entries for at most an
hour, and cleared on the new PersonsChanged broker event, which reaches every live server.

Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg"
```

---
### Task 6: Matching citations and authorships

**Files:**
- Create: `api/src/main/java/life/catalogue/api/model/PersonMatch.java`, `AuthorshipPersonMatch.java`
- Create: `core/src/main/java/life/catalogue/matching/person/PersonMatchService.java`
- Test: `core/src/test/java/life/catalogue/matching/person/PersonMatchServiceTest.java`

**Interfaces:**
- Consumes: Task 2's `PersonResolver.candidates/narrow/year`, `PersonKeys.key`, `PersonStore`.
- Produces:
  - `record PersonMatch(String citation, @Nullable String key, Status status, List<Person> candidates)` with
    `enum Status { RESOLVED, AMBIGUOUS, UNKNOWN, RULED_OUT }`
  - `record AuthorshipPersonMatch(String authorship, Status status, List<PersonMatch> combination, List<PersonMatch> combinationEx,
    List<PersonMatch> basionym, List<PersonMatch> basionymEx, List<PersonMatch> sanctioning)` with `enum Status { PARSED, UNPARSABLE }`
  - `PersonMatchService(PersonStore, PersonResolver.Margins)` and `(PersonStore, Margins, AuthorshipParser)`;
    `match(String citation, NomCode code, Integer year, TaxGroup group)` → `PersonMatch`;
    `matchAuthorship(String authorship, NomCode code, TaxGroup group)` → `AuthorshipPersonMatch`;
    `interface AuthorshipParser { Optional<ParsedAuthorship> parse(String authorship, NomCode code); }`

- [ ] **Step 1: Write the failing test**

```java
package life.catalogue.matching.person;

import life.catalogue.api.model.AuthorshipPersonMatch;
import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonMatch;
import life.catalogue.api.model.PersonName;
import life.catalogue.api.vocab.PersonFormCode;
import life.catalogue.api.vocab.PersonNameKind;
import life.catalogue.api.vocab.PersonSource;
import life.catalogue.api.vocab.TaxGroup;

import org.gbif.nameparser.api.NomCode;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.Test;

import static life.catalogue.api.model.PersonMatch.Status.*;
import static org.junit.Assert.*;

public class PersonMatchServiceTest {
  static final NomCode BOT = NomCode.BOTANICAL;
  static final NomCode ZOO = NomCode.ZOOLOGICAL;

  static Person person(String q, String family, String given, Integer born, Integer died, Set<TaxGroup> groups) {
    return new Person("wd:" + q, q, null, null, List.of(), family, given, null, born, died, null, null, groups,
      PersonSource.WIKIDATA);
  }

  static PersonName name(String q, String form, PersonNameKind kind, PersonFormCode code) {
    return new PersonName("wd:" + q, form, kind, code, PersonSource.WIKIDATA);
  }

  static final Person LINNAEUS = person("Q1043", "Linnaeus", "Carl", 1707, 1778, Set.of(TaxGroup.Plants));
  static final Person MILLER = person("Q380", "Miller", "Philip", 1691, 1771, Set.of());
  static final Person CANDOLLE = person("Q312", "Candolle", "Augustin Pyramus de", 1778, 1841, Set.of());
  static final Person FRIES = person("Q364", "Fries", "Elias Magnus", 1794, 1878, Set.of(TaxGroup.Fungi));
  static final Person JAMES1 = person("Q1", "Sowerby", "James", 1757, 1822, Set.of());
  static final Person JAMES2 = person("Q2", "Sowerby", "James", 1815, 1834, Set.of());

  static final MemoryPersonStore STORE = new MemoryPersonStore(new PersonFiles.Content(
    List.of(LINNAEUS, MILLER, CANDOLLE, FRIES, JAMES1, JAMES2),
    List.of(name("Q1043", "L.", PersonNameKind.STANDARD, PersonFormCode.BOT),
      name("Q380", "Mill.", PersonNameKind.STANDARD, PersonFormCode.BOT),
      name("Q312", "DC.", PersonNameKind.STANDARD, PersonFormCode.BOT),
      name("Q364", "Fr.", PersonNameKind.STANDARD, PersonFormCode.BOT),
      name("Q1", "James Sowerby", PersonNameKind.FULL, PersonFormCode.ANY),
      name("Q2", "James Sowerby", PersonNameKind.FULL, PersonFormCode.ANY)),
    List.of()));

  // explicit margins: the tests must not move when the defaults do
  private final PersonMatchService service = new PersonMatchService(STORE, new PersonResolver.Margins(10, 20, 15));

  @Test
  public void resolved() {
    var m = service.match("L.", BOT, null, null);
    assertEquals(RESOLVED, m.status());
    assertEquals("L.", m.citation());
    assertEquals(PersonKeys.key("L."), m.key());
    assertEquals(List.of(LINNAEUS), m.candidates());
  }

  @Test
  public void ambiguous() {
    var m = service.match("J. Sowerby", ZOO, null, null);
    assertEquals(AMBIGUOUS, m.status());
    assertEquals(Set.of(JAMES1, JAMES2), Set.copyOf(m.candidates()));
  }

  /** nobody has the form, and a botanical standard form cites nobody in zoology */
  @Test
  public void unknown() {
    assertEquals(UNKNOWN, service.match("Nobody", BOT, null, null).status());
    var m = service.match("L.", ZOO, null, null);
    assertEquals(UNKNOWN, m.status());
    assertEquals(List.of(), m.candidates());
  }

  @Test
  public void punctuationOnly() {
    var m = service.match(".", null, null, null);
    assertEquals(UNKNOWN, m.status());
    assertNull(m.key());
  }

  /** both Jameses were dead for more than 20 years by 1900, and a mycologist named no mollusc */
  @Test
  public void ruledOut() {
    var m = service.match("J. Sowerby", ZOO, 1900, null);
    assertEquals(RULED_OUT, m.status());
    assertEquals(Set.of(JAMES1, JAMES2), Set.copyOf(m.candidates()));
    m = service.match("Fr.", BOT, null, TaxGroup.Molluscs);
    assertEquals(RULED_OUT, m.status());
    assertEquals(List.of(FRIES), m.candidates());
    // a year leaving one person resolves to it
    m = service.match("J. Sowerby", ZOO, 1850, null);
    assertEquals(RESOLVED, m.status());
    assertEquals(List.of(JAMES2), m.candidates());
  }

  @Test
  public void authorshipSlots() {
    var m = service.matchAuthorship("(L.) Mill. ex DC.", BOT, null);
    assertEquals(AuthorshipPersonMatch.Status.PARSED, m.status());
    assertEquals(List.of(CANDOLLE), one(m.combination()).candidates());
    assertEquals(List.of(MILLER), one(m.combinationEx()).candidates());
    assertEquals(List.of(LINNAEUS), one(m.basionym()).candidates());
    assertEquals(List.of(), m.basionymEx());
    assertEquals(List.of(), m.sanctioning());

    var s = service.matchAuthorship("L. : Fr.", BOT, null);
    assertEquals(List.of(LINNAEUS), one(s.combination()).candidates());
    assertEquals(List.of(FRIES), one(s.sanctioning()).candidates());
  }

  /** every slot is narrowed by its own year: the basionym of 1820 is the elder James, the combination of 1850 the younger */
  @Test
  public void slotsKeepTheirYears() {
    var m = service.matchAuthorship("(J. Sowerby, 1820) J. Sowerby, 1850", ZOO, null);
    assertEquals(List.of(JAMES1), one(m.basionym()).candidates());
    assertEquals(List.of(JAMES2), one(m.combination()).candidates());
  }

  @Test
  public void unparsable() {
    var unreadable = new PersonMatchService(STORE, PersonResolver.Margins.DEFAULT, (a, c) -> Optional.empty());
    var m = unreadable.matchAuthorship("L. & ???", BOT, null);
    assertEquals(AuthorshipPersonMatch.Status.UNPARSABLE, m.status());
    assertEquals(List.of(), m.combination());
    assertEquals(List.of(), m.sanctioning());
  }

  private static PersonMatch one(List<PersonMatch> matches) {
    assertEquals(matches.toString(), 1, matches.size());
    return matches.get(0);
  }
}
```

- [ ] **Step 2: Run it to see it fail**

```bash
mvn -o -pl core test-compile > /tmp/t6.log 2>&1; grep ERROR /tmp/t6.log | head -5
```

Expected: compilation FAILS - `PersonMatchService`, `PersonMatch`, `AuthorshipPersonMatch` do not exist.

- [ ] **Step 3: The answer models**

`api/src/main/java/life/catalogue/api/model/PersonMatch.java`:

```java
package life.catalogue.api.model;

import java.util.List;

import javax.annotation.Nullable;

/**
 * An author citation matched to the persons of the registry.
 *
 * @param key        the citation folded as it is looked up, null if nothing is left of it to look up
 * @param candidates the one person for RESOLVED, several for AMBIGUOUS, none for UNKNOWN, and for RULED_OUT the persons
 *                   the year or group of the name excluded
 */
public record PersonMatch(String citation, @Nullable String key, Status status, List<Person> candidates) {

  public enum Status {
    /** the citation names one person */
    RESOLVED,
    /** the citation may name several persons */
    AMBIGUOUS,
    /** no person has a form under the key */
    UNKNOWN,
    /** persons have a form under the key, but the year or the group of the name excludes every one of them */
    RULED_OUT
  }
}
```

`api/src/main/java/life/catalogue/api/model/AuthorshipPersonMatch.java`:

```java
package life.catalogue.api.model;

import java.util.List;

/**
 * An authorship split by the name parser, every author matched to the persons of the registry. Each slot holds one match
 * per author, in the order of the authorship; all are empty for an authorship the parser cannot read.
 */
public record AuthorshipPersonMatch(String authorship, Status status, List<PersonMatch> combination,
                                    List<PersonMatch> combinationEx, List<PersonMatch> basionym, List<PersonMatch> basionymEx,
                                    List<PersonMatch> sanctioning) {

  public enum Status {
    PARSED,
    UNPARSABLE
  }
}
```

- [ ] **Step 4: `PersonMatchService`**

```java
package life.catalogue.matching.person;

import life.catalogue.api.model.AuthorshipPersonMatch;
import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonMatch;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.ParsedAuthorship;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Standalone author matching: an author citation, or every author of a whole authorship, matched to the persons of the
 * registry and narrowed by the code, year and group of the name.
 */
public class PersonMatchService {

  /**
   * Splits an authorship into its authors: the name parser, but for tests.
   */
  @FunctionalInterface
  public interface AuthorshipParser {
    Optional<ParsedAuthorship> parse(String authorship, @Nullable NomCode code);
  }

  private final PersonResolver resolver;
  private final AuthorshipParser parser;

  public PersonMatchService(PersonStore store, PersonResolver.Margins margins) {
    this(store, margins, NameParser.PARSER::parseAuthorship);
  }

  public PersonMatchService(PersonStore store, PersonResolver.Margins margins, AuthorshipParser parser) {
    this.resolver = new PersonResolver(store, margins);
    this.parser = parser;
  }

  /**
   * @return RESOLVED with the one person the citation may name, AMBIGUOUS with several, UNKNOWN with none, or RULED_OUT
   *         with the persons it would name but for the year or group
   */
  public PersonMatch match(String citation, @Nullable NomCode code, @Nullable Integer year, @Nullable TaxGroup group) {
    String key = PersonKeys.key(citation);
    Set<Person> all = key == null ? Set.of() : resolver.candidates(citation, code);
    if (all.isEmpty()) {
      return new PersonMatch(citation, key, PersonMatch.Status.UNKNOWN, List.of());
    }
    Set<Person> possible = resolver.narrow(all, year, group);
    if (possible.isEmpty()) {
      return new PersonMatch(citation, key, PersonMatch.Status.RULED_OUT, List.copyOf(all));
    }
    var status = possible.size() == 1 ? PersonMatch.Status.RESOLVED : PersonMatch.Status.AMBIGUOUS;
    return new PersonMatch(citation, key, status, List.copyOf(possible));
  }

  /**
   * Splits an authorship with the name parser and matches every author: the combination and its ex authors by the year
   * of the combination, the basionym and its ex authors by the year of the basionym, the sanctioning author by none.
   */
  public AuthorshipPersonMatch matchAuthorship(String authorship, @Nullable NomCode code, @Nullable TaxGroup group) {
    Optional<ParsedAuthorship> parsed = parser.parse(authorship, code);
    if (parsed.isEmpty()) {
      return new AuthorshipPersonMatch(authorship, AuthorshipPersonMatch.Status.UNPARSABLE, List.of(), List.of(), List.of(),
        List.of(), List.of());
    }
    ParsedAuthorship pa = parsed.get();
    Authorship comb = pa.getCombinationAuthorship() == null ? new Authorship() : pa.getCombinationAuthorship();
    Authorship bas = pa.getBasionymAuthorship() == null ? new Authorship() : pa.getBasionymAuthorship();
    Integer combYear = PersonResolver.year(comb.getYear());
    Integer basYear = PersonResolver.year(bas.getYear());
    return new AuthorshipPersonMatch(authorship, AuthorshipPersonMatch.Status.PARSED,
      match(comb.getAuthors(), code, combYear, group),
      match(comb.getExAuthors(), code, combYear, group),
      match(bas.getAuthors(), code, basYear, group),
      match(bas.getExAuthors(), code, basYear, group),
      pa.getSanctioningAuthor() == null ? List.of() : List.of(match(pa.getSanctioningAuthor(), code, null, group)));
  }

  private List<PersonMatch> match(@Nullable List<String> authors, @Nullable NomCode code, @Nullable Integer year,
                                  @Nullable TaxGroup group) {
    return authors == null ? List.of() : authors.stream().map(a -> match(a, code, year, group)).toList();
  }
}
```

- [ ] **Step 5: Run the test**

```bash
mvn -o -q -pl dao -am install -DskipTests
mvn -o -pl core test -Dtest=PersonMatchServiceTest -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t6.log 2>&1; tail -30 /tmp/t6.log
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A api dao core webservice docs && git commit -m "feat(persons): match citations and whole authorships to persons

PersonMatchService answers RESOLVED, AMBIGUOUS, UNKNOWN or RULED_OUT for a citation, and splits an authorship with the
name parser into its five author slots, each narrowed by its own year.

Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg"
```

---

### Task 7: The read API on both servers

**Files:**
- Create: `webservice/src/main/java/life/catalogue/resources/PersonResource.java`
- Modify: `webservice/src/main/java/life/catalogue/WsServerConfig.java`, `WsROServer.java`, `WsServer.java`, `docs/AUTHOR-PERSONS.md`
- Test: `webservice/src/test/java/life/catalogue/resources/PersonResourceTest.java`

**Interfaces:**
- Consumes: `PersonStore`, `PgPersonStore(SqlSessionFactory, PersonConfig)`, `PersonMatchService`, `PersonTables.read`,
  `PersonFiles.writeZip`.
- Produces: `PersonResource(PersonStore store, SqlSessionFactory factory)` with `get(String id)` → `PersonInfo`,
  `match(String q, String code, String year, String group)` → `PersonMatch`,
  `matchAuthorship(String q, String code, String group)` → `AuthorshipPersonMatch`, `export()` → `Response`;
  `WsServerConfig.persons` (`PersonConfig`); `WsROServer.registerReadOnlyResources(..., AreaLabelLookup areaLookup, PersonStore persons)`.

- [ ] **Step 1: Write the failing test**

```java
package life.catalogue.resources;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonMatch;
import life.catalogue.api.model.PersonName;
import life.catalogue.api.vocab.PersonFormCode;
import life.catalogue.api.vocab.PersonNameKind;
import life.catalogue.api.vocab.PersonSource;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.matching.person.MemoryPersonStore;
import life.catalogue.matching.person.PersonFiles;

import java.util.List;
import java.util.Set;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class PersonResourceTest {
  static final Person SWARTZ = new Person("wd:Q3", "Q3", "10103-1", null, List.of("ipni:10103-2"), "Swartz", "Olof", null,
    1760, 1818, null, null, Set.of(TaxGroup.Angiosperms), PersonSource.WIKIDATA);
  static final Person CANDOLLE = new Person("wd:Q312", "Q312", null, null, List.of(), "Candolle", "Augustin Pyramus de", null,
    1778, 1841, null, null, Set.of(), PersonSource.WIKIDATA);

  private final PersonResource resource = new PersonResource(new MemoryPersonStore(new PersonFiles.Content(
    List.of(SWARTZ, CANDOLLE),
    List.of(new PersonName("wd:Q3", "Sw.", PersonNameKind.STANDARD, PersonFormCode.BOT, PersonSource.IPNI),
      new PersonName("wd:Q312", "DC.", PersonNameKind.STANDARD, PersonFormCode.BOT, PersonSource.IPNI)),
    List.of())), null);

  /** any id the person answers to, and a 404 for one nobody does */
  @Test
  public void anyId() {
    for (String id : List.of("wd:Q3", "ipni:10103-1", "ipni:10103-2")) {
      assertEquals(id, SWARTZ, resource.get(id).person());
    }
    assertThrows(NotFoundException.class, () -> resource.get("wd:Q404"));
  }

  @Test
  public void statuses() {
    assertEquals(PersonMatch.Status.RESOLVED, resource.match("Sw.", "BOTANICAL", null, null).status());
    assertEquals(PersonMatch.Status.UNKNOWN, resource.match("Sw.", "ZOOLOGICAL", null, null).status());
    assertEquals(PersonMatch.Status.RULED_OUT, resource.match("Sw.", null, "1900", null).status());
    assertEquals(PersonMatch.Status.RULED_OUT, resource.match("Sw.", null, null, "Molluscs").status());
  }

  @Test
  public void authorship() {
    var m = resource.matchAuthorship("Sw. ex DC.", "BOTANICAL", null);
    assertEquals(List.of(CANDOLLE), m.combination().get(0).candidates());
    assertEquals(List.of(SWARTZ), m.combinationEx().get(0).candidates());
  }

  /** parameters as the rest of the API takes them, and a 400 - an IllegalArgumentException - for anything else */
  @Test
  public void parameters() {
    assertEquals(PersonMatch.Status.RESOLVED, resource.match("Sw.", "botanical", "1800", "angiosperms").status());
    assertThrows(IllegalArgumentException.class, () -> resource.match("Sw.", "martian", null, null));
    assertThrows(IllegalArgumentException.class, () -> resource.match("Sw.", null, null, "Dragons"));
    assertThrows(IllegalArgumentException.class, () -> resource.match("Sw.", null, "18x", null));
    assertThrows(IllegalArgumentException.class, () -> resource.match(" ", null, null, null));
    assertThrows(IllegalArgumentException.class, () -> resource.matchAuthorship("Sw.", "martian", null));
  }
}
```

- [ ] **Step 2: Run it to see it fail**

```bash
mvn -o -q -pl core install -DskipTests
mvn -o -pl webservice test-compile > /tmp/t7.log 2>&1; grep ERROR /tmp/t7.log | head -5
```

Expected: compilation FAILS - `PersonResource` does not exist.

- [ ] **Step 3: `PersonResource`**

```java
package life.catalogue.resources;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.AuthorshipPersonMatch;
import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonInfo;
import life.catalogue.api.model.PersonMatch;
import life.catalogue.api.util.VocabularyUtils;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.matching.person.PersonFiles;
import life.catalogue.matching.person.PersonMatchService;
import life.catalogue.matching.person.PersonResolver;
import life.catalogue.matching.person.PersonStore;
import life.catalogue.matching.person.PersonTables;
import life.catalogue.parser.NomCodeParser;
import life.catalogue.parser.UnparsableException;

import org.gbif.nameparser.api.NomCode;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

/**
 * The person registry: persons by any id they answer to, and author citations or whole authorships matched to them.
 */
@Path("/person")
@Produces(MediaType.APPLICATION_JSON)
public class PersonResource {
  private final PersonStore store;
  private final SqlSessionFactory factory;
  private final PersonMatchService matcher;

  public PersonResource(PersonStore store, SqlSessionFactory factory) {
    this.store = store;
    this.factory = factory;
    this.matcher = new PersonMatchService(store, PersonResolver.Margins.DEFAULT);
  }

  /**
   * One author citation, narrowed by the code, year and group of the name it authored.
   */
  @GET
  @Path("match")
  public PersonMatch match(@QueryParam("q") String q, @QueryParam("code") String code, @QueryParam("year") String year,
                           @QueryParam("group") String group) {
    return matcher.match(required(q), code(code), year(year), group(group));
  }

  /**
   * A whole authorship, every author of every slot matched.
   */
  @GET
  @Path("match/authorship")
  public AuthorshipPersonMatch matchAuthorship(@QueryParam("q") String q, @QueryParam("code") String code,
                                               @QueryParam("group") String group) {
    return matcher.matchAuthorship(required(q), code(code), group(group));
  }

  /**
   * The registry as a zip of its three files, as the admin import takes it.
   */
  @GET
  @Path("export")
  @Produces(MoreMediaTypes.APP_ZIP)
  public Response export() {
    StreamingOutput stream = os -> {
      PersonFiles.Content c;
      try (SqlSession session = factory.openSession(true)) {
        c = PersonTables.read(session);
      }
      PersonFiles.writeZip(os, c);
    };
    return Response.ok(stream).header("Content-Disposition", "attachment; filename=\"persons.zip\"").build();
  }

  /**
   * @param id any id the person answers to: its own, a former one or a prefixed authority id such as ipni:12653-1
   */
  @GET
  @Path("{id}")
  public PersonInfo get(@PathParam("id") String id) {
    PersonInfo info = store.info(id);
    if (info == null) {
      throw NotFoundException.notFound(Person.class, id);
    }
    return info;
  }

  private static String required(@Nullable String q) {
    if (StringUtils.isBlank(q)) {
      throw new IllegalArgumentException("Parameter q is required");
    }
    return q;
  }

  // parameters are parsed here and not by a converter: Jersey answers a value it fails to convert with a 404,
  // an IllegalArgumentException of the resource is a 400
  @Nullable
  static NomCode code(@Nullable String code) {
    if (StringUtils.isBlank(code)) return null;
    try {
      return NomCodeParser.PARSER.parse(code).orElseThrow(() -> new IllegalArgumentException("Unknown code " + code));
    } catch (UnparsableException e) {
      throw new IllegalArgumentException("Unknown code " + code);
    }
  }

  @Nullable
  static TaxGroup group(@Nullable String group) {
    if (StringUtils.isBlank(group)) return null;
    return VocabularyUtils.lookup(group, TaxGroup.class).orElseThrow(() -> new IllegalArgumentException("Unknown group " + group));
  }

  @Nullable
  static Integer year(@Nullable String year) {
    if (StringUtils.isBlank(year)) return null;
    try {
      return Integer.valueOf(year.trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("The year " + year + " is no number");
    }
  }
}
```

- [ ] **Step 4: Configure and register on both servers**

`WsServerConfig`, after the `matching` field:

```java
  @Valid
  @NotNull
  public PersonConfig persons = new PersonConfig();
```

`WsROServer.registerReadOnlyResources` gains a last parameter `PersonStore persons` and registers, next to the other
global resources:

```java
    j.register(new PersonResource(persons, factory));
```

In `WsROServer.run`, after `env.lifecycle().manage(ManagedUtils.from(broker));`:

```java
    // the person registry, whose caches every server clears on a PersonsChanged event
    var persons = new PgPersonStore(getSqlSessionFactory(), cfg.persons);
    broker.register(persons);
```

and pass `persons` as the last argument of its `registerReadOnlyResources(...)` call (after `areaLookup`). In
`WsServer.run`, the same two lines after `env.lifecycle().manage(ManagedUtils.from(broker));`, and `persons` as the
last argument of `WsROServer.registerReadOnlyResources(...)`. Import `life.catalogue.config.PersonConfig`,
`life.catalogue.matching.person.PgPersonStore` and `life.catalogue.matching.person.PersonStore` where used.

- [ ] **Step 5: Document the API**

In `docs/AUTHOR-PERSONS.md` insert before `## Harvesting`:

```markdown
## The API

Read only, on both servers:

- `GET /person/{id}` - a person with its forms and relations. `id` is any id the person answers to: `wd:Q157501`,
  `ipni:4084-1` or a former id. The forms are those the sources and curators gave, not the derived ones. 404 for none.
- `GET /person/match?q=Hook.f.&code=botanical&year=1867&group=Angiosperms` - one author citation; everything but `q`
  is optional. The answer holds the citation, the key it was looked up by, a status and the candidates: `RESOLVED`
  with one person, `AMBIGUOUS` with several, `UNKNOWN` with none, and `RULED_OUT` with the persons the year or the
  group excluded, narrowed as `PersonResolver` narrows them.
- `GET /person/match/authorship?q=(L.) Mill. ex DC.&code=botanical` - a whole authorship, split by the name parser.
  The answer holds one citation match per author of `combination`, `combinationEx`, `basionym`, `basionymEx` and
  `sanctioning`; the combination and its ex authors are narrowed by the year of the combination, the basionym and its
  ex authors by the year of the basionym. An authorship the parser cannot read has the status `UNPARSABLE` and no
  matches.
- `GET /person/export` - the registry as a zip of its three files.

`code` takes what the rest of the API takes for a nomenclatural code, `group` a `TaxGroup`; an unknown value, or a
`year` that is no number, is a 400.

```

- [ ] **Step 6: Run the tests**

```bash
mvn -o -pl webservice test -Dtest='PersonResourceTest,VocabResourceTest' -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t7.log 2>&1; tail -30 /tmp/t7.log
```

Expected: PASS, and the module compiles with both servers wired.

- [ ] **Step 7: Commit**

```bash
git add -A api dao core webservice docs && git commit -m "feat(persons): the person API on both servers

GET /person/{id} by any id, /person/match for a citation, /person/match/authorship for a whole authorship and
/person/export for the registry as a zip. Both servers read through a PgPersonStore registered with the event broker.

Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg"
```

---

### Task 8: The harvest in core main, rebuilt without files

The harvest classes move from test to main unchanged but for logging; `PersonHarvest`, the hand run over files, becomes
`PersonRebuild`, the file-free steps the job of Task 10 calls. The merge rules do not change here.

**Files:**
- Move: `core/src/test/java/life/catalogue/matching/person/harvest/{CachingFetcher,Fetcher,Groups,HarvestSource,HttpFetcher,IpniPersonSource,Json,MergeReport,Names,PersonMerger,PersonRecord,RetryingFetcher,WikidataPersonSource,Years,ZooBankDumpSource}.java` → `core/src/main/java/life/catalogue/matching/person/harvest/`
- Move+rewrite: `.../harvest/PersonHarvest.java` → `core/src/main/java/life/catalogue/matching/person/harvest/PersonRebuild.java`
- Move+rewrite: `.../harvest/PersonHarvestTest.java` → `core/src/test/java/life/catalogue/matching/person/harvest/PersonRebuildTest.java`

**Interfaces:**
- Consumes: `MemoryPersonStore`, `PersonStore.candidates`, `PersonFiles.Content`, `HarvestSource`, `PersonMerger`.
- Produces: `PersonRebuild.Harvest(List<PersonRecord> records, String stats)`,
  `PersonRebuild.Outcome(PersonFiles.Content content, List<String> problems, String report)`,
  `static Harvest fetch(List<HarvestSource>) throws Exception`, `static List<String> gone(PersonFiles.Content, Harvest)`,
  `static Outcome rebuild(PersonFiles.Content existing, Harvest harvest, Map<String, String> redirects, LocalDate today)`.
  `PersonMerger` gains a `PersonMerger(LocalDate today)` constructor here already, unused until Task 9.

- [ ] **Step 1: Move**

```bash
H=core/src/test/java/life/catalogue/matching/person/harvest
M=core/src/main/java/life/catalogue/matching/person/harvest
mkdir -p $M
for c in CachingFetcher Fetcher Groups HarvestSource HttpFetcher IpniPersonSource Json MergeReport Names PersonMerger \
         PersonRecord RetryingFetcher WikidataPersonSource Years ZooBankDumpSource; do
  git mv $H/$c.java $M/$c.java
done
git mv $H/PersonHarvest.java $M/PersonRebuild.java
git mv $H/PersonHarvestTest.java $H/PersonRebuildTest.java
```

- [ ] **Step 2: Write the failing test**

Replace `PersonRebuildTest.java` by:

```java
package life.catalogue.matching.person.harvest;

import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonName;
import life.catalogue.api.vocab.PersonFormCode;
import life.catalogue.api.vocab.PersonNameKind;
import life.catalogue.api.vocab.PersonSource;
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
    var outcome = PersonRebuild.rebuild(PersonFiles.Content.empty(), harvest, Map.of(), DAY);
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
    var outcome = PersonRebuild.rebuild(existing, PersonRebuild.fetch(List.of()), Map.of(), DAY);
    assertEquals(List.of("clb:1 is local but has authority ids"), outcome.problems());
    assertTrue(outcome.report(), outcome.report().contains("## Inconsistent, nothing written: 1"));
  }

  /** without a reader for its format a dump must stop the harvest before anything is merged */
  @Test
  public void zooBankDumpNotReadYet() {
    var e = assertThrows(UnsupportedOperationException.class,
      () -> PersonRebuild.fetch(List.of(new ZooBankDumpSource(Path.of("dump.csv")))));
    assertTrue(e.getMessage(), e.getMessage().contains("ZooBank"));
  }
}
```

- [ ] **Step 3: Run it to see it fail**

```bash
mvn -o -pl core test-compile > /tmp/t8.log 2>&1; grep ERROR /tmp/t8.log | head -5
```

Expected: compilation FAILS - `PersonRebuild` has no `fetch`, `gone`, `rebuild` (it still holds the old `PersonHarvest`).

- [ ] **Step 4: Write `PersonRebuild`**

Replace the moved `PersonRebuild.java` by:

```java
package life.catalogue.matching.person.harvest;

import life.catalogue.api.model.Person;
import life.catalogue.api.vocab.PersonFormCode;
import life.catalogue.common.io.Resources;
import life.catalogue.matching.person.MemoryPersonStore;
import life.catalogue.matching.person.PersonFiles;
import life.catalogue.matching.person.PersonStore;

import org.gbif.nameparser.api.NomCode;

import java.time.LocalDate;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One harvest of the person registry, without its database: reading the sources, finding the Wikidata items that
 * disappeared, and merging the harvest into the registry by the rules of {@link PersonMerger}. The job around it keeps
 * the slow reading out of any transaction and the fast merge inside one.
 */
public final class PersonRebuild {
  private static final Logger LOG = LoggerFactory.getLogger(PersonRebuild.class);
  private static final String AUTHOR_MAP = "authorship/authormap.txt";
  private static final int LIST_LIMIT = 500;

  /**
   * @param stats what the sources could not map, for the report
   */
  public record Harvest(List<PersonRecord> records, String stats) {
  }

  /**
   * @param problems what makes the rebuilt registry inconsistent, empty if it can be written
   */
  public record Outcome(PersonFiles.Content content, List<String> problems, String report) {
  }

  private PersonRebuild() {
  }

  /**
   * Reads every source: the slow part, to run with no database session open.
   */
  public static Harvest fetch(List<HarvestSource> sources) throws Exception {
    List<PersonRecord> records = new ArrayList<>();
    StringBuilder stats = new StringBuilder();
    for (HarvestSource s : sources) {
      List<PersonRecord> read = s.read();
      LOG.info("{}: {} records", s.name(), read.size());
      records.addAll(read);
      stats.append(s.stats());
    }
    return new Harvest(records, stats.toString());
  }

  /**
   * @return the Wikidata items of the registry no record carries any more, to be asked for a redirect
   */
  public static List<String> gone(PersonFiles.Content existing, Harvest harvest) {
    Set<String> seen = new HashSet<>();
    harvest.records().forEach(r -> {
      if (r.wikidata() != null) seen.add(r.wikidata());
    });
    return existing.persons().stream()
      .map(Person::wikidata)
      .filter(q -> q != null && !seen.contains(q))
      .toList();
  }

  /**
   * Merges the harvest into the registry and checks the result, which is only fit to be written without problems.
   *
   * @param redirects Wikidata items of the registry that became a redirect, old Q-id to new Q-id
   * @param today     the day a person no source has any more is retired on
   */
  public static Outcome rebuild(PersonFiles.Content existing, Harvest harvest, Map<String, String> redirects, LocalDate today) {
    var result = new PersonMerger(today).merge(existing, harvest.records(), redirects);
    var store = new MemoryPersonStore(result.content());
    StringBuilder sb = new StringBuilder("# Person harvest\n\n");
    sb.append(String.format("persons %,d, names %,d, relations %,d%n", result.content().persons().size(),
      result.content().names().size(), result.content().relations().size()));
    if (!store.problems().isEmpty()) {
      sb.append(String.format("%n## Inconsistent, nothing written: %,d%n", store.problems().size()));
      store.problems().stream().limit(LIST_LIMIT).forEach(p -> sb.append("  ").append(p).append('\n'));
    }
    sb.append(result.report().render()).append('\n').append(harvest.stats());
    unresolvedAuthorMapRows(store, sb);
    return new Outcome(result.content(), store.problems(), sb.toString());
  }

  /**
   * Author map rows none of whose forms name a person: hand edits worth keeping become curated lines.
   */
  private static void unresolvedAuthorMapRows(PersonStore store, StringBuilder sb) {
    List<String> unresolved = new ArrayList<>();
    int rows = 0;
    for (String[] row : (Iterable<String[]>) Resources.tabRows(AUTHOR_MAP)::iterator) {
      if (row.length < 3) continue;
      rows++;
      PersonFormCode code = PersonFormCode.valueOf(row[1].trim().toUpperCase());
      List<NomCode> codes = switch (code) {
        case BOT -> List.of(NomCode.BOTANICAL);
        case ZOO -> List.of(NomCode.ZOOLOGICAL);
        case ANY -> List.of(NomCode.BOTANICAL, NomCode.ZOOLOGICAL);
      };
      boolean found = false;
      for (int i = 0; i < row.length && !found; i++) {
        if (i == 1) continue;
        for (NomCode c : codes) {
          if (!store.candidates(row[i], c).isEmpty()) {
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
    unresolved.stream().limit(LIST_LIMIT).forEach(r -> sb.append("  ").append(r).append('\n'));
  }
}
```

In `PersonMerger` add, without using it yet:

```java
  private final LocalDate today;

  public PersonMerger() {
    this(LocalDate.now());
  }

  /**
   * @param today the day a person no source has any more is retired on
   */
  public PersonMerger(LocalDate today) {
    this.today = today;
  }
```

- [ ] **Step 5: Log instead of printing**

Main code logs. Give `RetryingFetcher`, `IpniPersonSource` and `WikidataPersonSource` a
`private static final Logger LOG = LoggerFactory.getLogger(<Class>.class);` and replace:

- in `RetryingFetcher`: `System.err.println("  fetch attempt " + attempt + "/" + retries + " failed: " + StringUtils.abbreviate(last.getMessage(), 300));`
  by `LOG.warn("Fetch attempt {}/{} failed: {}", attempt, retries, StringUtils.abbreviate(last.getMessage(), 300));`
- in `IpniPersonSource`: `System.out.printf("  ipni %s: %d authors%n", prefix, page.path("totalResults").asInt());`
  by `LOG.info("ipni {}: {} authors", prefix, page.path("totalResults").asInt());`
- in `WikidataPersonSource`: `System.out.printf("  wikidata %s offset %d: %d rows%n", p, offset, rows);` by
  `LOG.info("wikidata {} offset {}: {} rows", p, offset, rows);` and `System.out.printf("  wikidata %s %d of %d%n", what, i, total);`
  by `LOG.info("wikidata {} {} of {}", what, i, total);`

Then `git grep -n 'System\.\(out\|err\)' -- core/src/main/java/life/catalogue/matching/person` must print nothing.

- [ ] **Step 6: Run the tests**

```bash
mvn -o -pl core test -Dtest='life/catalogue/matching/person/**/*Test.java' -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t8.log 2>&1; tail -30 /tmp/t8.log
```

Expected: all PASS, the moved harvest tests (`PersonMergerTest`, `WikidataPersonSourceTest`, `IpniPersonSourceTest`,
`CachingFetcherTest`, `RetryingFetcherTest`, `NamesTest`, `GroupsTest`, `YearsTest`) included.

- [ ] **Step 7: Commit**

```bash
git add -A api dao core webservice docs && git commit -m "refactor(persons): the harvest in core main, rebuilt without files

The harvest classes move to main and log instead of printing. PersonRebuild takes the place of the hand run
PersonHarvest: fetch, the Wikidata items gone, and a merge checked by MemoryPersonStore with its report.

Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg"
```

---

### Task 9: Follow the sources, curated lines win

**Files:**
- Modify: `core/src/main/java/life/catalogue/matching/person/harvest/PersonMerger.java`
- Rewrite: `core/src/main/java/life/catalogue/matching/person/harvest/MergeReport.java`
- Test: `core/src/test/java/life/catalogue/matching/person/harvest/PersonMergerTest.java`

**Interfaces:**
- Consumes: Task 3's `Person.retired()/successor()`, Task 8's `PersonMerger(LocalDate today)`.
- Produces: `MergeReport` with the lists `addedPersons`, `joined`, `retired`, `unretired`, `changed`, `curatedKept`,
  `formsAdded`, `formsRemoved`, `relationsAdded`, `relationsRemoved`, `conflicts`, `ambiguous` (the list `notSeen` is
  gone, `retired` takes its place). Line formats: changed `"<id> <field>: <old> -> <new>"`, curatedKept
  `"<id> <field>: curated <value>, sources <value>"`, forms `"<id> <form> <KIND> <CODE>"`, relations
  `"<id> <RELATION> <other id>"`, joined `"<old id> into <id>: <why>"`, retired `"<id>"` or `"<id>: no source names it"`.

- [ ] **Step 1: Write the failing tests**

In `PersonMergerTest`, add `static final LocalDate DAY = LocalDate.of(2026, 9, 24);`, let the `merge` helper use
`new PersonMerger(DAY)`, import `java.time.LocalDate`, `java.util.stream.Collectors`,
`life.catalogue.matching.person.MemoryPersonStore`. Replace the tests `fillsOnly`, `filesAndSourceDisagree` and
`notSeenIsKept` by the first three below, add the others, and change the existing tests as listed after them.

```java
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
```

Change the existing tests:

- `redirectOntoAnotherPerson`: merge with `new PersonMerger(DAY)`; replace its last two assertions (`born` 1800, `died`
  1870) by

```java
    // the old id finds the person it was joined into
    assertSame(p, new MemoryPersonStore(r.content()).get("wd:Q1"));
    // the values follow the item that stays, and it gives none
    assertNull(p.born());
    assertNull(p.died());
    assertEquals(1, r.report().joined.size());
    assertTrue(r.report().joined.get(0), r.report().joined.get(0).startsWith("wd:Q1 into wd:Q2"));
```

- `gainsABetterId`: the harvested IPNI line only stays if IPNI still gives it; add before the merge
  `var i = ipni("9-1"); i.name("Sowerby", PersonNameKind.STANDARD, PersonFormCode.BOT);` and merge
  `merge(existing, w.build(), i.build())`.
- `ownIdWins`: `r.report().notSeen` becomes `r.report().retired`.
- `secondIpniIdJoinsAPersonOfTheFiles`: the family follows the item now, add `w.family("Smith");` before the merge.
- `redirectedItem`: merge with `new PersonMerger(DAY)`.
- `bornAfterDiedIsLeftOut`: its second half now shows a curated line keeping its years; change the comment
  `// a year already in the files stays, only the filled one goes` to
  `// a curated line keeps its years, the source's are only reported`. The assertions stay.

- [ ] **Step 2: Run them to see them fail**

```bash
mvn -o -pl core test -Dtest=PersonMergerTest -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t9.log 2>&1; grep -E 'Tests run|FAIL|ERROR' /tmp/t9.log | head -20
```

Expected: compilation FAILS on `curatedKept`, `retired`, `formsRemoved`, `joined` of `MergeReport`.

- [ ] **Step 3: Rewrite `MergeReport`**

```java
package life.catalogue.matching.person.harvest;

import java.util.ArrayList;
import java.util.List;

/**
 * What a merge did - a diff of the registry - and everything a person should look at.
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
  public final List<String> addedPersons = new ArrayList<>();
  /** persons of before that became part of another: "wd:Q1 into wd:Q2: a Wikidata redirect of Q1 to Q2" */
  public final List<String> joined = new ArrayList<>();
  public final List<String> retired = new ArrayList<>();
  public final List<String> unretired = new ArrayList<>();
  /** a value of a person of before the sources give otherwise now: "wd:Q1 born: 1700 -> 1750" */
  public final List<String> changed = new ArrayList<>();
  /** a value of a curated person the sources give otherwise, which stays */
  public final List<String> curatedKept = new ArrayList<>();
  public final List<String> formsAdded = new ArrayList<>();
  public final List<String> formsRemoved = new ArrayList<>();
  public final List<String> relationsAdded = new ArrayList<>();
  public final List<String> relationsRemoved = new ArrayList<>();
  public final List<String> conflicts = new ArrayList<>();
  public final List<String> ambiguous = new ArrayList<>();

  public String render() {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("persons before %,d, added %,d, merged into another %,d, following a Wikidata redirect %,d%n",
      existing, added, merged, redirected));
    sb.append(String.format("dropped: records without a name %,d, records without an id %,d, relations to no person %,d%n",
      withoutName, withoutId, relationsDropped));
    list(sb, "Values changed", changed);
    list(sb, "Persons added", addedPersons);
    list(sb, "Persons joined", joined);
    list(sb, "Persons retired", retired);
    list(sb, "Persons no longer retired", unretired);
    list(sb, "Forms added", formsAdded);
    list(sb, "Forms removed", formsRemoved);
    list(sb, "Relations added", relationsAdded);
    list(sb, "Relations removed", relationsRemoved);
    list(sb, "Curated values the sources give otherwise", curatedKept);
    list(sb, "Sources disagree", conflicts);
    list(sb, "Authority ids claimed by two persons", ambiguous);
    return sb.toString();
  }

  private static void list(StringBuilder sb, String title, List<String> lines) {
    sb.append(String.format("%n## %s: %,d%n", title, lines.size()));
    lines.stream().limit(LIST_LIMIT).forEach(l -> sb.append("  ").append(l).append('\n'));
  }
}
```

- [ ] **Step 4: Change `PersonMerger`**

Imports: add `java.time.LocalDate`, `java.util.stream.Collectors`, `life.catalogue.api.vocab.PersonSource`,
`life.catalogue.api.vocab.PersonRelationType` (if the wildcard does not cover them).

Replace the class javadoc by:

```java
/**
 * Merges the records of a harvest into the registry, following the sources.
 * <ul>
 *   <li>Records and persons are joined on shared authority ids only, never on names.</li>
 *   <li>Every harvested value, form and relation is what its sources say now: a changed year, name or group is updated,
 *   a form or relation no source gives any more goes. The registry of before only carries the ids on: former ids,
 *   Wikidata redirects, joins of two persons.</li>
 *   <li>Curated persons, forms and relations win: a curated person keeps its values as they are, empty ones included,
 *   and what the sources say otherwise is only reported.</li>
 *   <li>A person no source has any more, or none names, is kept and retired: it keeps its ids and values and loses its
 *   harvested forms. One a source names again is no longer retired.</li>
 *   <li>Where the records of one run disagree, IPNI wins for a person with an IPNI and no ZooBank id, ZooBank for one
 *   with a ZooBank and no IPNI id, Wikidata otherwise, and the disagreement is reported.</li>
 *   <li>Two persons with different ids of one authority are never merged, and a curated person is never merged into
 *   another: a source joining it to another person is reported instead.</li>
 *   <li>An authority is always right about its own id: a record joins the person holding its own id, whatever else it
 *   links to.</li>
 * </ul>
 */
```

In `Draft` add the fields `LocalDate retired;` and `String successor;`, copy them in `Draft.of`
(`d.retired = p.retired(); d.successor = p.successor();`) and pass them in `toPerson()`:

```java
    Person toPerson() {
      return new Person(id, wikidata, ipni, zoobank, List.copyOf(formerIds), family, given, suffix, born, died, activeFrom,
        activeTo, Set.copyOf(groups), source, retired, successor);
    }
```

In `merge`, replace everything from `Map<String, String> rekeyed = assignIds(drafts);` to the end of the method by:

```java
    assignIds(drafts);
    for (Draft d : drafts) {
      fill(d);
    }
    // every id a draft answers to, for the lines that refer to a person by another than its own
    Map<String, String> own = new HashMap<>();
    for (Draft d : drafts) {
      d.formerIds.forEach(id -> own.put(id, d.id));
      d.authorityIds().forEach(id -> own.put(id, d.id));
      own.put(d.id, d.id);
    }
    Set<String> before = new HashSet<>();
    drafts.stream().filter(d -> d.existing).forEach(d -> before.add(d.id));
    List<PersonName> names = names(existing, drafts, own, before);
    Set<String> named = new HashSet<>();
    names.forEach(n -> named.add(own.getOrDefault(n.person(), n.person())));
    List<Draft> kept = new ArrayList<>();
    for (Draft d : drafts) {
      if (d.existing) {
        kept.add(d);
        retire(d, named.contains(d.id));
      } else if (named.contains(d.id)) {
        kept.add(d);
        report.added++;
        report.addedPersons.add(d.id);
      } else {
        report.withoutName++;
      }
    }
    List<PersonRelation> relations = relations(existing, kept, own, before);
    return new Result(new PersonFiles.Content(kept.stream().map(Draft::toPerson).toList(), names, relations), report);
```

`assignIds` no longer builds the old-to-new map:

```java
  private static void assignIds(List<Draft> drafts) {
    for (Draft d : drafts) {
      if (d.local()) continue;
      String id = Person.idFor(d.wikidata, d.ipni, d.zoobank);
      if (d.id != null && !d.id.equals(id) && !d.formerIds.contains(d.id)) {
        d.formerIds.add(d.id);
      }
      d.id = id;
      d.formerIds.remove(id);
    }
  }
```

In `join`, right after the curated check returns, add:

```java
    if (other.existing) {
      report.joined.add(describe(other) + " into " + describe(keep) + ": " + why);
    }
```

Replace both `fill` methods by:

```java
  /**
   * The values of a person are what its best ranked sources say now. A curated person keeps its own, and a person no
   * source has keeps the ones it had.
   */
  private void fill(Draft d) {
    if (d.records.isEmpty()) return;
    List<PersonRecord> recs = new ArrayList<>(d.records);
    recs.sort(Comparator.comparingInt(r -> rank(d, r.source())));
    BiPredicate<String, String> sameText = String::equalsIgnoreCase;
    BiPredicate<Integer, Integer> sameYear = (a, b) -> Math.abs(a - b) <= YEAR_TOLERANCE;
    String family = pick(d, "family", recs, PersonRecord::family, sameText);
    String given = pick(d, "given", recs, PersonRecord::given, sameText);
    String suffix = pick(d, "suffix", recs, PersonRecord::suffix, sameText);
    Integer born = pick(d, "born", recs, PersonRecord::born, sameYear);
    Integer died = pick(d, "died", recs, PersonRecord::died, sameYear);
    Integer activeFrom = pick(d, "activeFrom", recs, PersonRecord::activeFrom, sameYear);
    Integer activeTo = pick(d, "activeTo", recs, PersonRecord::activeTo, sameYear);
    Set<TaxGroup> groups = EnumSet.noneOf(TaxGroup.class);
    recs.forEach(r -> groups.addAll(r.groups()));
    if (d.source == CURATED) {
      // a curated line wins as a whole, what the sources say otherwise is only reported
      kept(d, "family", d.family, family);
      kept(d, "given", d.given, given);
      kept(d, "suffix", d.suffix, suffix);
      kept(d, "born", d.born, born);
      kept(d, "died", d.died, died);
      kept(d, "activeFrom", d.activeFrom, activeFrom);
      kept(d, "activeTo", d.activeTo, activeTo);
      kept(d, "groups", d.groups, groups.isEmpty() ? null : groups);
      return;
    }
    // years of two sources, or a source's own error, must not make an impossible person: unknown beats wrong
    String impossible = born != null && died != null && born > died ? "born " + born + " after died " + died
      : born != null && activeFrom != null && activeFrom < born ? "active from " + activeFrom + " before born " + born
      : null;
    if (impossible != null) {
      report.conflicts.add(d.id + " " + impossible + ": the years are left out");
      born = null;
      died = null;
      activeFrom = null;
      activeTo = null;
    }
    d.family = follow(d, "family", d.family, family);
    d.given = follow(d, "given", d.given, given);
    d.suffix = follow(d, "suffix", d.suffix, suffix);
    d.born = follow(d, "born", d.born, born);
    d.died = follow(d, "died", d.died, died);
    d.activeFrom = follow(d, "activeFrom", d.activeFrom, activeFrom);
    d.activeTo = follow(d, "activeTo", d.activeTo, activeTo);
    if (!groups.equals(d.groups)) {
      follow(d, "groups", Set.copyOf(d.groups), groups);
      d.groups.clear();
      d.groups.addAll(groups);
    }
    PersonSource source = recs.get(0).source();
    if (d.source != null && d.source != source) {
      follow(d, "source", d.source.value(), source.value());
    }
    d.source = source;
  }

  /**
   * @return the value of the best ranked record that has one; values other records give otherwise are reported
   */
  @Nullable
  private <T> T pick(Draft d, String field, List<PersonRecord> recs, Function<PersonRecord, T> getter, BiPredicate<T, T> same) {
    T chosen = null;
    PersonSource from = null;
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
    return chosen;
  }

  /**
   * @return the value the sources give now, a change to a person of before reported
   */
  @Nullable
  private <T> T follow(Draft d, String field, @Nullable T current, @Nullable T now) {
    if (d.existing && !Objects.equals(current, now)) {
      report.changed.add(d.id + " " + field + ": " + current + " -> " + now);
    }
    return now;
  }

  private void kept(Draft d, String field, @Nullable Object curated, @Nullable Object sources) {
    if (sources != null && !sources.equals(curated)) {
      report.curatedKept.add(d.id + " " + field + ": curated " + curated + ", sources " + sources);
    }
  }

  /**
   * A person no source has any more, or none names, is retired on this day: it keeps its row and ids and is found by
   * them only. One a source names again is no longer retired. Curated and local persons are never retired.
   */
  private void retire(Draft d, boolean named) {
    if (d.local() || d.source == CURATED) return;
    boolean gone = d.records.isEmpty() || !named;
    if (gone && d.retired == null) {
      d.retired = today;
      report.retired.add(d.records.isEmpty() ? d.id : d.id + ": no source names it");
    } else if (!gone && d.retired != null) {
      d.retired = null;
      report.unretired.add(d.id);
    }
  }
```

Replace `names` and `relations` by:

```java
  /**
   * Curated forms stay as written, harvested ones are what the records give now. What changed for the persons of before
   * is reported.
   */
  private List<PersonName> names(PersonFiles.Content existing, List<Draft> drafts, Map<String, String> own, Set<String> before) {
    List<PersonName> names = new ArrayList<>();
    Set<List<Object>> seen = new HashSet<>();
    Set<List<Object>> old = new LinkedHashSet<>();
    for (PersonName n : existing.names()) {
      List<Object> k = List.of(own.getOrDefault(n.person(), n.person()), n.form(), n.kind(), n.code());
      if (n.source() == CURATED) {
        if (seen.add(k)) {
          names.add(n);
        }
      } else {
        old.add(k);
      }
    }
    Set<List<Object>> harvested = new LinkedHashSet<>();
    for (Draft d : drafts) {
      for (PersonRecord r : d.records) {
        for (PersonRecord.Form f : r.names()) {
          List<Object> k = List.of(d.id, f.form(), f.kind(), f.code());
          if (seen.add(k)) {
            names.add(new PersonName(d.id, f.form(), f.kind(), f.code(), r.source()));
            harvested.add(k);
          }
        }
      }
    }
    diff(old, harvested, seen, before, report.formsAdded, report.formsRemoved);
    return names;
  }

  /**
   * Curated relations stay as written, harvested ones are what the records give now.
   */
  private List<PersonRelation> relations(PersonFiles.Content existing, List<Draft> kept, Map<String, String> own,
                                         Set<String> before) {
    Map<String, Draft> byAnyId = new HashMap<>();
    for (Draft d : kept) {
      byAnyId.put(d.id, d);
      d.formerIds.forEach(id -> byAnyId.put(id, d));
      d.authorityIds().forEach(id -> byAnyId.put(id, d));
    }
    List<PersonRelation> relations = new ArrayList<>();
    Set<List<Object>> seen = new HashSet<>();
    Set<List<Object>> old = new LinkedHashSet<>();
    for (PersonRelation r : existing.relations()) {
      List<Object> k = key(own.getOrDefault(r.person(), r.person()), r.relation(), own.getOrDefault(r.other(), r.other()));
      if (r.source() == CURATED) {
        if (seen.add(k)) {
          relations.add(r);
        }
      } else {
        old.add(k);
      }
    }
    Set<List<Object>> harvested = new LinkedHashSet<>();
    for (Draft d : kept) {
      for (PersonRecord r : d.records) {
        for (PersonRecord.Link l : r.relations()) {
          Draft other = byAnyId.get(l.other());
          if (other == null || other == d) {
            report.relationsDropped++;
            continue;
          }
          List<Object> k = key(d.id, l.relation(), other.id);
          boolean fresh = seen.add(k);
          if (l.relation() == PersonRelationType.SIBLING) {
            fresh &= seen.add(key(other.id, l.relation(), d.id));
          }
          if (fresh) {
            relations.add(new PersonRelation(d.id, l.relation(), other.id, r.source()));
            harvested.add(k);
          }
        }
      }
    }
    diff(old, harvested, seen, before, report.relationsAdded, report.relationsRemoved);
    return relations;
  }

  /**
   * Reports the harvested lines of persons of before that are new, and the old harvested lines no source and no curated
   * line gives any more. A line is its key, whose first element is the person.
   */
  private static void diff(Set<List<Object>> old, Set<List<Object>> harvested, Set<List<Object>> seen, Set<String> before,
                           List<String> added, List<String> removed) {
    for (List<Object> k : harvested) {
      if (before.contains((String) k.get(0)) && !old.contains(k)) {
        added.add(line(k));
      }
    }
    for (List<Object> k : old) {
      if (!harvested.contains(k) && !seen.contains(k)) {
        removed.add(line(k));
      }
    }
  }

  private static String line(List<Object> k) {
    return k.stream().map(Object::toString).collect(Collectors.joining(" "));
  }
```

- [ ] **Step 5: Run the tests**

```bash
mvn -o -pl core test -Dtest='life/catalogue/matching/person/**/*Test.java' -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t9.log 2>&1; tail -30 /tmp/t9.log
```

Expected: all PASS, `PersonRebuildTest` included.

- [ ] **Step 6: Commit**

```bash
git add -A api dao core webservice docs && git commit -m "feat(persons): a harvest follows the sources, curated lines win

Harvested values, forms and relations are what the sources say now; the registry of before only carries ids on.
Curated persons keep their line as a whole, persons no source has or names are retired and come back when named
again, and the report is a diff of the registry.

Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg"
```

---

### Task 10: The harvest job, the admin endpoints and the docs

**Files:**
- Create: `core/src/main/java/life/catalogue/matching/person/harvest/PersonHarvestJob.java`
- Create: `core/src/main/java/life/catalogue/jobs/cron/PersonHarvestCron.java`
- Create: `webservice/src/main/java/life/catalogue/resources/PersonAdminResource.java`
- Modify: `webservice/src/main/java/life/catalogue/WsServer.java`, `docs/AUTHOR-PERSONS.md`, `docs/2026-09-24-person-registry-service.md`, `CLAUDE.md`, any doc naming the old classes
- Test: `core/src/test/java/life/catalogue/matching/person/harvest/PersonHarvestJobTest.java`, `core/src/test/java/life/catalogue/jobs/cron/PersonHarvestCronTest.java`, `webservice/src/test/java/life/catalogue/resources/PersonAdminResourceTest.java`

**Interfaces:**
- Consumes: `PersonRebuild.fetch/gone/rebuild`, `PersonTables.lock/read/write/replace`, `PersonFiles.readZip`,
  `PersonsChanged`, `EventBroker.publish`, `PersonConfig.harvestDir/harvestIntervalDays`, `JobExecutor.submit`.
- Produces:
  - `PersonHarvestJob(int userKey, SqlSessionFactory factory, EventBroker broker, Path runDir, Sources sources)`,
    `static PersonHarvestJob live(int userKey, SqlSessionFactory, EventBroker, PersonConfig)`,
    `record Sources(List<HarvestSource> list, RedirectLookup redirects)` with `static Sources live(Path cache)`,
    `interface RedirectLookup { Map<String, String> redirects(Collection<String> qids) throws Exception; }`,
    `static final String REPORT = "report.md"`
  - `PersonHarvestCron(JobExecutor exec, Supplier<? extends BackgroundJob> job, int days)`
  - `PersonAdminResource(SqlSessionFactory, JobExecutor, EventBroker, PersonConfig)` with `harvest(User)` → `JobInfo`
    and `importZip(InputStream, User)` → `Counts(int persons, int names, int relations)`

- [ ] **Step 1: Write the failing tests**

`core/src/test/java/life/catalogue/matching/person/harvest/PersonHarvestJobTest.java`:

```java
package life.catalogue.matching.person.harvest;

import life.catalogue.api.event.PersonsChanged;
import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonName;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.api.vocab.PersonFormCode;
import life.catalogue.api.vocab.PersonNameKind;
import life.catalogue.api.vocab.PersonSource;
import life.catalogue.api.vocab.Users;
import life.catalogue.event.EventBroker;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;
import life.catalogue.matching.person.PersonFiles;
import life.catalogue.matching.person.PersonTables;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PersonHarvestJobTest {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.empty();

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private final EventBroker broker = mock(EventBroker.class);
  private Path run;

  @Before
  public void init() throws IOException {
    run = tmp.newFolder("run").toPath();
    // an answer an earlier, failed run cached
    Files.writeString(run.resolve("cached.json"), "{}");
  }

  private static SqlSessionFactory factory() {
    return SqlSessionFactoryRule.getSqlSessionFactory();
  }

  private PersonHarvestJob job(HarvestSource... sources) {
    return new PersonHarvestJob(Users.TESTER, factory(), broker, run, new PersonHarvestJob.Sources(List.of(sources), qids -> Map.of()));
  }

  private static HarvestSource source(PersonRecord... records) {
    return new HarvestSource() {
      public String name() {
        return "test";
      }

      public List<PersonRecord> read() {
        return List.of(records);
      }
    };
  }

  private static PersonRecord wd(String q, String label) {
    var w = new PersonRecord.Builder(PersonSource.WIKIDATA);
    w.wikidata = q;
    w.label(label);
    return w.build();
  }

  private static List<String> ids() {
    try (SqlSession session = factory().openSession(true)) {
      return PersonTables.read(session).persons().stream().map(Person::id).sorted().toList();
    }
  }

  private static String report(PersonHarvestJob job) throws IOException {
    try (var zip = new ZipFile(job.getResult().getFile())) {
      return new String(zip.getInputStream(zip.getEntry(PersonHarvestJob.REPORT)).readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  public void writesAndAnnounces() throws Exception {
    var job = job(source(wd("Q1", "Carl Linnaeus")));
    job.run();
    assertEquals(String.valueOf(job.getError()), JobStatus.FINISHED, job.getStatus());
    assertEquals(List.of("wd:Q1"), ids());
    verify(broker).publish(new PersonsChanged(Users.TESTER));
    assertFalse("a successful run leaves no cache behind", Files.exists(run));
    assertTrue(report(job), report(job).contains("## Persons added: 1"));
  }

  /** a local person with an authority id is inconsistent, and a harvest keeps local persons as they are */
  @Test
  public void inconsistentWritesNothing() throws Exception {
    var bad = new Person("clb:1", "Q9", null, null, List.of(), "Doe", null, null, null, null, null, null, Set.of(),
      PersonSource.CURATED);
    try (SqlSession session = factory().openSession(false)) {
      PersonTables.write(session, new PersonFiles.Content(List.of(bad),
        List.of(new PersonName("clb:1", "Ann Doe", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.CURATED)), List.of()));
      session.commit(true);
    }
    var job = job(source(wd("Q2", "Ann Roe")));
    job.run();
    assertEquals(JobStatus.FAILED, job.getStatus());
    assertEquals(List.of("clb:1"), ids());
    verifyNoInteractions(broker);
    assertTrue("a failed run keeps its cache for the next", Files.exists(run.resolve("cached.json")));
    assertTrue(report(job), report(job).contains("clb:1 is local but has authority ids"));
  }

  @Test
  public void failingSourceWritesNothing() {
    HarvestSource broken = new HarvestSource() {
      public String name() {
        return "broken";
      }

      public List<PersonRecord> read() {
        throw new IllegalStateException("HTTP 503");
      }
    };
    var job = job(broken);
    job.run();
    assertEquals(JobStatus.FAILED, job.getStatus());
    assertEquals(List.of(), ids());
    verifyNoInteractions(broker);
    assertTrue(Files.exists(run.resolve("cached.json")));
  }
}
```

`core/src/test/java/life/catalogue/jobs/cron/PersonHarvestCronTest.java`:

```java
package life.catalogue.jobs.cron;

import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.JobExecutor;

import org.junit.Test;

import static org.mockito.Mockito.*;

public class PersonHarvestCronTest {

  /** a cron job that throws is unscheduled, so a refused submission must not end the harvests */
  @Test
  public void submitsAndSurvivesARefusal() {
    var exec = mock(JobExecutor.class);
    var job = mock(BackgroundJob.class);
    var cron = new PersonHarvestCron(exec, () -> job, 7);
    cron.run();
    verify(exec).submit(job);
    doThrow(new IllegalArgumentException("An identical job is queued already")).when(exec).submit(job);
    cron.run();
    verify(exec, times(2)).submit(job);
  }
}
```

`webservice/src/test/java/life/catalogue/resources/PersonAdminResourceTest.java`:

```java
package life.catalogue.resources;

import life.catalogue.api.model.PersonName;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.PersonFormCode;
import life.catalogue.api.vocab.PersonNameKind;
import life.catalogue.api.vocab.PersonSource;
import life.catalogue.config.PersonConfig;
import life.catalogue.event.EventBroker;
import life.catalogue.matching.person.PersonFiles;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

public class PersonAdminResourceTest {

  /** an inconsistent registry is a 400 before anything touches the database, and announces nothing */
  @Test
  public void inconsistentImportIsRefused() throws Exception {
    var broker = mock(EventBroker.class);
    var resource = new PersonAdminResource(null, null, broker, new PersonConfig());
    var out = new ByteArrayOutputStream();
    PersonFiles.writeZip(out, new PersonFiles.Content(List.of(),
      List.of(new PersonName("wd:Q404", "Nobody", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.CURATED)), List.of()));
    var user = new User();
    user.setKey(1);
    var e = assertThrows(IllegalArgumentException.class, () -> resource.importZip(new ByteArrayInputStream(out.toByteArray()), user));
    assertTrue(e.getMessage(), e.getMessage().contains("unknown person wd:Q404"));
    verifyNoInteractions(broker);
  }
}
```

- [ ] **Step 2: Run them to see them fail**

```bash
mvn -o -pl core test-compile > /tmp/t10.log 2>&1; grep ERROR /tmp/t10.log | head -5
```

Expected: compilation FAILS - `PersonHarvestJob`, `PersonHarvestCron` do not exist.

- [ ] **Step 3: `PersonHarvestJob`**

```java
package life.catalogue.matching.person.harvest;

import life.catalogue.api.event.PersonsChanged;
import life.catalogue.api.model.JobResult;
import life.catalogue.api.vocab.JobPriority;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.config.PersonConfig;
import life.catalogue.event.EventBroker;
import life.catalogue.matching.person.PersonFiles;
import life.catalogue.matching.person.PersonTables;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Harvests the person registry from Wikidata and IPNI and rewrites it in Postgres, following what the sources say now
 * while curated lines win, see {@link PersonMerger}. The sources are read with no database session open - the idle in
 * transaction timeout would end a long transaction - and every answer is cached in the run directory, which a failed run
 * leaves for the next to resume from and a successful one deletes. The merge, its checks and the write happen in one
 * short transaction that keeps other writers out; an inconsistent result writes nothing and fails the job. Either way
 * the review report is the job's download.
 */
public class PersonHarvestJob extends BackgroundJob {
  static final String REPORT = "report.md";
  private final SqlSessionFactory factory;
  private final EventBroker broker;
  private final Path runDir;
  private final Sources sources;
  private final JobResult result;

  @FunctionalInterface
  public interface RedirectLookup {
    /**
     * @return old Q-id to new Q-id of the items that became a redirect
     */
    Map<String, String> redirects(Collection<String> qids) throws Exception;
  }

  /**
   * The authorities to read, and how to ask Wikidata for the redirects of vanished items.
   */
  public record Sources(List<HarvestSource> list, RedirectLookup redirects) {

    /**
     * Wikidata and IPNI over HTTP, serially, 1 s and 250 ms apart, every complete answer cached in the directory.
     */
    public static Sources live(Path cache) {
      var wikidata = new WikidataPersonSource(new CachingFetcher(cache.resolve("wikidata"),
        new RetryingFetcher(new HttpFetcher("application/json"), Duration.ofSeconds(1), Json::complete), Json::complete));
      var ipni = new IpniPersonSource(new CachingFetcher(cache.resolve("ipni"),
        new RetryingFetcher(new HttpFetcher("application/json"), Duration.ofMillis(250), Json::complete), Json::complete));
      return new Sources(List.of(wikidata, ipni), wikidata::redirects);
    }
  }

  /**
   * @param runDir the directory the answers of the sources are cached in, deleted when the run succeeds
   */
  public PersonHarvestJob(int userKey, SqlSessionFactory factory, EventBroker broker, Path runDir, Sources sources) {
    super(JobPriority.LOW, userKey);
    this.factory = factory;
    this.broker = broker;
    this.runDir = runDir;
    this.sources = sources;
    this.result = new JobResult(getKey());
  }

  /**
   * @return the harvest of Wikidata and IPNI, cached in the harvest directory of the config
   */
  public static PersonHarvestJob live(int userKey, SqlSessionFactory factory, EventBroker broker, PersonConfig cfg) {
    Path run = cfg.harvestDir.toPath();
    return new PersonHarvestJob(userKey, factory, broker, run, Sources.live(run.resolve("cache")));
  }

  @Override
  public Object getSerialBy() {
    return PersonHarvestJob.class.getSimpleName();
  }

  @Override
  public boolean isDuplicate(BackgroundJob other) {
    return other instanceof PersonHarvestJob;
  }

  @Override
  public JobResult getResult() {
    return result.getFile().exists() ? result : null;
  }

  @Override
  public void execute() throws Exception {
    setStep("reading the sources");
    PersonRebuild.Harvest harvest = PersonRebuild.fetch(sources.list());
    checkIfCancelled();
    PersonFiles.Content before;
    try (SqlSession session = factory.openSession(true)) {
      before = PersonTables.read(session);
    }
    setStep("asking Wikidata for redirects");
    List<String> gone = PersonRebuild.gone(before, harvest);
    Map<String, String> redirects = gone.isEmpty() ? Map.of() : sources.redirects().redirects(gone);
    checkIfCancelled();
    setStep("rebuilding the registry");
    try (SqlSession session = factory.openSession(false)) {
      try {
        PersonTables.lock(session);
        var outcome = PersonRebuild.rebuild(PersonTables.read(session), harvest, redirects, LocalDate.now());
        writeReport(outcome.report());
        if (!outcome.problems().isEmpty()) {
          throw new IllegalStateException("The harvested registry is inconsistent, nothing written: "
            + String.join("; ", outcome.problems().subList(0, Math.min(10, outcome.problems().size()))));
        }
        PersonTables.write(session, outcome.content());
        // forced: the writes went past MyBatis, which would otherwise neither commit nor roll back
        session.commit(true);
      } catch (Exception e) {
        session.rollback(true);
        throw e;
      }
    }
    broker.publish(new PersonsChanged(getUserKey()));
    FileUtils.deleteDirectory(runDir.toFile());
  }

  private void writeReport(String report) throws IOException {
    File zip = result.getFile();
    FileUtils.forceMkdirParent(zip);
    try (var out = new ZipOutputStream(new FileOutputStream(zip))) {
      out.putNextEntry(new ZipEntry(REPORT));
      out.write(report.getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
    }
    result.calculateSizeAndMd5();
  }
}
```

- [ ] **Step 4: `PersonHarvestCron`**

```java
package life.catalogue.jobs.cron;

import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.JobExecutor;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts a harvest of the person registry every few days. Only scheduled when persons.harvestIntervalDays is set.
 */
public class PersonHarvestCron extends CronJob {
  private static final Logger LOG = LoggerFactory.getLogger(PersonHarvestCron.class);
  private final JobExecutor exec;
  private final Supplier<? extends BackgroundJob> job;

  public PersonHarvestCron(JobExecutor exec, Supplier<? extends BackgroundJob> job, int days) {
    super(days, days, TimeUnit.DAYS);
    this.exec = exec;
    this.job = job;
  }

  @Override
  public void run() {
    try {
      exec.submit(job.get());
    } catch (RuntimeException e) {
      // a cron job that throws is unscheduled by the executor, and this one must survive to run next time
      LOG.error("Failed to submit a person harvest", e);
    }
  }
}
```

- [ ] **Step 5: `PersonAdminResource` and the wiring**

```java
package life.catalogue.resources;

import life.catalogue.api.event.PersonsChanged;
import life.catalogue.api.model.JobInfo;
import life.catalogue.api.model.User;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.config.PersonConfig;
import life.catalogue.dao.JobDao;
import life.catalogue.dw.auth.Roles;
import life.catalogue.event.EventBroker;
import life.catalogue.matching.person.PersonFiles;
import life.catalogue.matching.person.PersonTables;
import life.catalogue.matching.person.harvest.PersonHarvestJob;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

import org.apache.ibatis.session.SqlSessionFactory;

import io.dropwizard.auth.Auth;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/**
 * Writes the person registry: a harvest from its authorities, or an import of its files.
 */
@Path("/admin/persons")
@Hidden
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({Roles.ADMIN})
public class PersonAdminResource {
  private final SqlSessionFactory factory;
  private final JobExecutor exec;
  private final EventBroker broker;
  private final PersonConfig cfg;

  public record Counts(int persons, int names, int relations) {
  }

  public PersonAdminResource(SqlSessionFactory factory, JobExecutor exec, EventBroker broker, PersonConfig cfg) {
    this.factory = factory;
    this.exec = exec;
    this.broker = broker;
    this.cfg = cfg;
  }

  @POST
  @Path("harvest")
  public JobInfo harvest(@Auth User user) {
    var job = PersonHarvestJob.live(user.getKey(), factory, broker, cfg);
    exec.submit(job);
    return JobDao.buildInfo(job);
  }

  /**
   * Replaces the registry by a zip of its three files as /person/export writes them. An inconsistent registry is a 400
   * and writes nothing.
   */
  @POST
  @Path("import")
  @Consumes({MoreMediaTypes.APP_ZIP, MoreMediaTypes.APP_ZIP_ALT1, MoreMediaTypes.APP_ZIP_ALT2, MoreMediaTypes.APP_ZIP_ALT3})
  public Counts importZip(InputStream zip, @Auth User user) throws IOException, SQLException {
    PersonFiles.Content c = PersonFiles.readZip(zip);
    PersonTables.replace(factory, c);
    broker.publish(new PersonsChanged(user.getKey()));
    return new Counts(c.persons().size(), c.names().size(), c.relations().size());
  }
}
```

In `WsServer.run` register it after the `AdminResource`:

```java
    j.register(new PersonAdminResource(getSqlSessionFactory(), executor, broker, cfg.persons));
```

and replace the `var cron = CronExecutor.startWith(...)` statement by:

```java
    List<CronJob> cronJobs = new ArrayList<>(List.of(
      new TempDatasetCleanup(ddao),
      new ProjectCounterUpdate(getSqlSessionFactory()),
      new JobCleanup(getSqlSessionFactory(), cfg.job),
      new MatcherReconcile(matcherFactory)
    ));
    if (cfg.persons.harvestIntervalDays > 0) {
      cronJobs.add(new PersonHarvestCron(executor,
        () -> PersonHarvestJob.live(Users.IMPORTER, getSqlSessionFactory(), broker, cfg.persons), cfg.persons.harvestIntervalDays));
    }
    var cron = CronExecutor.startWith(cronJobs.toArray(CronJob[]::new));
```

Import `life.catalogue.jobs.cron.CronJob`, `life.catalogue.jobs.cron.PersonHarvestCron`,
`life.catalogue.matching.person.harvest.PersonHarvestJob`, `life.catalogue.api.vocab.Users` and `java.util.ArrayList`
/ `java.util.List` if missing.

- [ ] **Step 6: Run the tests**

```bash
mvn -o -q -pl core install -DskipTests
mvn -o -pl core test -Dtest='PersonHarvestJobTest,PersonHarvestCronTest' -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t10.log 2>&1; tail -30 /tmp/t10.log
mvn -o -pl webservice test -Dtest='PersonAdminResourceTest,PersonResourceTest' -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t10w.log 2>&1; tail -30 /tmp/t10w.log
```

Expected: all PASS.

- [ ] **Step 7: Update the docs**

In `docs/AUTHOR-PERSONS.md`:

1. Replace everything above `## The files` by:

```markdown
# The person registry

Authors of scientific names as persons, keyed by the identifiers authorities give them: Wikidata Q-ids, IPNI author
ids and ZooBank author ids. The registry lives in Postgres and is served by the API: a person by any of its ids, and
author citations or whole authorships matched to persons. The person based author comparison that reads it is measured
against the string comparison and not used in production. Design records:
[2026-09-23-person-author-comparison.md](2026-09-23-person-author-comparison.md) and
[2026-09-24-person-registry-service.md](2026-09-24-person-registry-service.md).

The model and its vocabularies are in `api` (`Person`, `PersonName`, `PersonRelation`, `PersonNameKind`,
`PersonFormCode`, `PersonRelationType`, `PersonSource`), storage and lookup in `dao` (package
`life.catalogue.matching.person`), resolving, comparing, matching and the harvest in `core`, the resources in
`webservice`.

```

2. Replace the heading `## The files` and the paragraph under it, up to and including the table, by:

```markdown
## The registry

Four tables, global rather than per dataset: `person`, `person_id` (every id a person answers to - its own, its former
ids and its prefixed authority ids - with its own id), `person_name` (its forms, each with the key it is looked up by)
and `person_relation`. `GET /person/export` writes them as three files in a zip, the format
`POST /admin/persons/import` reads and the corpus tools load (`PersonFiles`). The files are tab delimited with a header
that is verified on reading, lists are pipe separated, an empty cell is null, and every file is written sorted so two
exports diff line by line. The persons file of phase 3, without the last two columns, still reads.

| file | columns |
|---|---|
| `persons.tsv` | `id wikidata ipni zoobank formerIds family given suffix born died activeFrom activeTo groups source retired successor` |
| `names.tsv` | `person form kind code source` |
| `relations.tsv` | `person relation other source` |
```

3. In the bullet `**kind**`, append: "`DERIVED` rows exist only in the table: the forms `PersonForms` derives, see below."
4. Replace the paragraph starting `` `PersonRegistryFilesTest` guards the committed files`` by:

```markdown
- **`retired`**: the day a harvest found no source that has the person, or none that names it, any more. A retired
  person keeps its row and its ids and is found by them; its harvested forms went with its source and it derives no
  forms, so a citation only finds it through a curated form. A source naming it again ends the retirement.
- **`successor`**: the id of the person a retired one was joined into, where a curator knows it. A harvest sets none: a
  person it joins into another disappears into it, its id a former id of the other, so the old id finds the survivor.

Every registry is checked before it is written, by the harvest and the import alike (`MemoryPersonStore.problems()`):
every reference resolves, ids and former ids are unique, every id matches the authority ids of its person, nobody is
born after they died or active before they were born, every successor exists, and every person that is not retired has
a name form. `PersonRegistryFilesTest` runs the same checks on the files in `core/src/test/resources/authorship/persons/`,
which the tests and the corpus tools read.
```

5. In `## Looking up a citation`, replace the text from `` `PersonRegistry.get()` loads the files once`` up to and
   including `Forms are derived per person with a family name when loading:` by:

```markdown
Everything resolves through a `PersonStore`: `PgPersonStore` on the servers, a `MemoryPersonStore` built from the
files in tests and the corpus tools. `candidates(citation, code)` folds the citation to its key and returns every person
with a form under that key whose code applies. The key (`PersonKeys.key`) is `AuthorshipNormalizer.normalize`, the key
citations are compared by everywhere, repeated until it no longer changes: normalizing twice can change a key
(`McQueen`, `Saeed`, `Đinh`), and the comparator hands over authors normalized already. It is computed in Java when the
registry is written and when it is read, never in SQL. `PersonForms` derives forms per person with a family name, which
the writer stores as `DERIVED` rows:
```

   and replace the paragraph's last line, `` `get(anyId)` resolves any id of a person and `relatives(person)` gives
   parents, children and siblings.``, by:

```markdown
`get(anyId)` resolves any id of a person, `byKeys` many keys at once and `relatives(person)` gives parents, children
and siblings.

`PgPersonStore` keeps two bounded caches, key and code to person ids and any id to person, 100,000 and 50,000 entries,
each kept an hour at most (`persons` in the config: `keyCacheSize`, `personCacheSize`, `cacheExpireMinutes`). A miss is
one indexed select. A harvest or an import publishes `PersonsChanged` through the event broker, which reaches every
live server, and each clears its caches.
```

6. In `## Comparing authors as persons`, replace `candidates under the name's code, cached per citation and code.` by
   `candidates under the name's code.`
7. Replace the whole `## Harvesting` section, up to `## Curating by hand`, by the text below, keeping the two
   paragraphs that start with `**Wikidata** gives everyone` and `**IPNI** has no bulk download` word for word where
   marked:

```markdown
## Harvesting

`POST /admin/persons/harvest` starts a `PersonHarvestJob`; with `persons.harvestIntervalDays` set, the cron executor
starts one every so many days. It runs in the default lane, one at a time.

1. **Fetch.** It reads Wikidata and IPNI with no database session open, serially, pausing 1 s between Wikidata and
   250 ms between IPNI requests and retrying with a growing pause. An answer that is no complete JSON object, or that
   is an API error, counts as a failed request: the query service sometimes answers 200 with a body cut off. Such an
   answer is retried and never cached. Every other answer is cached in `persons.harvestDir`: a run that fails leaves
   the cache for the next to resume from, a successful run deletes it, so a later harvest never reads an earlier one's
   answers. Items of the registry that no record carries any more are asked the query service for a redirect.
2. **Rebuild.** In one short transaction that keeps other writers out - readers see the old registry until it
   commits - it reads the registry, merges the harvest into it and checks the result.
3. **Write.** A consistent result replaces every row by COPY, derived forms and the any id index included, and
   `PersonsChanged` is published. An inconsistent one writes nothing and fails the job.
4. **Report.** Either way the job's download is a zip holding `report.md`.

(the `**Wikidata** gives everyone ...` paragraph, unchanged)

(the `**IPNI** has no bulk download ...` paragraph, unchanged)

**Merging** follows the sources. Records and persons are joined on shared authority ids only, never on names;
Wikidata's P586 and P2006 are what link a Wikidata item to an IPNI or ZooBank author. An authority is always right about
its own id: a record joins the person holding its own id, whatever else it links to, and a linked id another person
holds is reported, not taken. Every harvested value, form and relation is what the sources say now: a changed year,
name or group is updated, a form or relation no source gives any more goes. Where the records of one run disagree,
IPNI wins for a person with an IPNI and no ZooBank id, ZooBank for one with a ZooBank and no IPNI id, Wikidata
otherwise; years within 2 of each other agree. Years that would make a person impossible - born after death, active
before birth - are all left out and reported: unknown beats wrong. Two items of one authority that claim the same id of
another stay two persons. A new person without any name form is not written. The registry of before only carries ids
on: a person no source has any more, or that no source names, is kept and retired, and a retired person a source names
again is no longer retired.

Two persons become one when a record links them or a Wikidata redirect moves one onto the item of the other: the
person holding the record's own id, or the redirect's target, stays, and every differing value of the other is
reported as dropped. The other's id becomes a former id of the one that stays, so it still finds it.

**The report** is a diff against the registry of before - every value changed old to new, every person added, joined,
retired or back, every form and relation added or removed - next to what needs a person: the curated values the
sources give otherwise, the disagreements between sources, the ids claimed twice, what the sources could not map
(fields of work, IPNI taxon groups, dates) and the rows of `authormap.txt` no person resolves. An inconsistent result
lists its problems at the top.

```

8. Replace the `## Curating by hand` section by:

```markdown
## Curating by hand

A line with `source` `curated` is the way to add what no authority has: an alias the report shows missing, a relation
the authorities lack, or a person with no authority id at all, who gets a `clb:N` id, N one above the highest in use.
Curated lines win: a harvest never removes a curated form or relation, keeps a curated person's values as they are -
empty ones included - and reports what the sources say otherwise, and never joins a curated person with another. Its
ids still follow the authorities: a Wikidata redirect or a newly linked authority id changes `wikidata` and `id`, the
old id going to `formerIds`, and curated lines keep resolving through it.

Until curator tooling exists, curating is a round trip: `GET /person/export`, edit the files, zip them and
`POST /admin/persons/import` the zip. The import runs the checks above, writes nothing for an inconsistent registry (a
400 listing its problems), and publishes `PersonsChanged` once it has written.

```

9. In `## ZooBank`, replace `the harvest takes `--zoobank <dump>` and refuses to run with it until the reader for the
   dump's format is written` by `reading it throws until the reader for the dump's format is written, and the harvest
   job does not read it yet`.

Then find the remaining mentions of the old names outside the dated design records and update them:

```bash
git grep -n 'PersonRegistry\b\|PersonHarvest\b\|NameKind\b\|FormCode\b\|Provenance\b\|src/main/resources/authorship/persons' -- docs CLAUDE.md \
  | grep -v 'docs/20[0-9][0-9]-' | grep -v 'docs/superpowers/'
```

Expected after editing: no output. `docs/AUTHOR-CORPUS.md` names `PersonCorpusReport`, which stays.

In `CLAUDE.md`, under **Key Architectural Patterns**, add after the **Usage Matcher Store** block:

```markdown
**Person registry:**
Authors of names as persons with Wikidata, IPNI and ZooBank ids, in the global tables `person`, `person_id`,
`person_name` and `person_relation`. Everything resolves through a `PersonStore` - `PgPersonStore` with two bounded
Caffeine caches that every server clears on a `PersonsChanged` event, `MemoryPersonStore` from TSV files in tests and the
corpus tools - by the key `PersonKeys.key` folds a form or citation to, computed in Java and never in SQL. `PersonTables`
rewrites the whole registry in one transaction by delete and COPY, storing derived forms as `DERIVED` rows.
`PersonHarvestJob` (`POST /admin/persons/harvest`) reads Wikidata and IPNI with no database session open, then rebuilds
under a table lock following the sources, curated lines winning; a person no source has any more is retired, never
deleted. See [`docs/AUTHOR-PERSONS.md`](docs/AUTHOR-PERSONS.md).
```

In `docs/2026-09-24-person-registry-service.md`, replace the `Status:` line by
`Status: implemented on branch feat/person-author-comparison on 2026-09-24, not merged or deployed. First of several
sub-projects, see "Scope".` and append:

```markdown
## Outcome

Implemented as designed, with these decisions the design left open:

- Joins keep working through former ids: a person a harvest joins into another disappears into it and its id becomes a
  former id of the survivor, so the old id answers the survivor directly. `successor` is carried by files, tables and
  import and checked to resolve, but only curator tooling will set it.
- A curated person wins as a whole line: its empty cells are no longer filled from the sources, what they say
  otherwise is reported.
- A person a source still lists but no source names is retired too, or a single odd record would fail the
  consistency check of a whole harvest.
- Impossible years chosen from the sources leave all four years out.
- `/person/{id}` shows the forms sources and curators gave, not the derived ones; `RULED_OUT` lists the candidates the
  year or group excluded.
- The lookups select with `IN (...)` lists rather than `= ANY(?)`, as the other mappers do.
- `created` and `modified` survive the delete-and-copy rewrite: a person keeps `created`, and `modified` unless its row
  changed.
- The TSVs moved to `core/src/test/resources/authorship/persons/`, read by tests and the corpus tools only; removing
  them from the repository waits for the prod import.
- The harvest caches in `persons.harvestDir`, kept after a failed run and deleted after a successful one. The import
  writes synchronously in its request.
```

- [ ] **Step 8: Run the whole suite**

```bash
mvn -o install -DskipTests -q
mvn -o test > /tmp/full.log 2>&1; grep -E 'Tests run:|BUILD|FAIL' /tmp/full.log | tail -20
```

Expected: `BUILD SUCCESS`. A failure outside the person code is reported by name, not skipped.

- [ ] **Step 9: Commit and push**

```bash
git add -A api dao core webservice docs CLAUDE.md && git commit -m "feat(persons): the harvest job, admin endpoints and docs

PersonHarvestJob reads Wikidata and IPNI with no session open, then merges, checks and rewrites the registry under a
table lock and publishes PersonsChanged; its report is the job download. POST /admin/persons/harvest and
/admin/persons/import, an optional cron, and the docs for the registry in Postgres.

Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg"
git push origin feat/person-author-comparison
```

Expected: the push succeeds; no PR is opened.
