# Slim Reusable Modules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Slim the reusable modules (`api`, `reader`, `metadata`, `coldp`) so external
projects can consume them without inheriting jackson-databind, httpclient, mapdb, kryo,
citeproc, or jbibtex.

**Architecture:** Reshape the fat `api` module into a slim POJO model (jackson-annotations
only) and push each heavy concern into a downstream module: `api-jackson` (databind serde),
`reference` (citeproc + jbibtex citation formatting), `kryo` (kryo + mapdb binary serde),
and `DownloadUtil` (httpclient) folded into `dao`. Model classes keep their
`life.catalogue.*` package names so only Maven dependency edges change, not imports.

**Tech Stack:** Java 21, Maven multi-module (parent `org.catalogueoflife:motherpom`),
Jackson 2 (annotations + databind), citeproc-java, jbibtex, kryo, mapdb, httpclient5.

## Global Constraints

- Java 21; 2-space indent, 1TBS, 140-column limit; CamelCase types, camelCase vars.
- Dependency **versions are managed by the parent `motherpom`** — never add `<version>`
  to a `<dependency>` unless the existing pom already does (e.g. api's `jena.version`).
- **No `module-info.java`** anywhere — split packages across artifacts are intentional and
  only work on the classic classpath.
- **The JSON API output must not change.** Any change to serialization is a defect.
- Moved classes **keep their existing package names** (`life.catalogue.common.kryo.*`,
  `life.catalogue.common.io.*`, `life.catalogue.common.csl.*`, `life.catalogue.api.jackson.*`).
  Only the Maven module they live in changes, so `import` statements in consumers stay valid.
- Build from this worktree with `-Dmaven.gitcommitid.skip=true -Dgit.skip=true` appended to
  every `mvn` command (git-commit-id plugin breaks in worktrees).
- Reactor order is computed by Maven from the dependency graph; the order of `<module>`
  entries in the root `pom.xml` does not matter.

**Reference build command (used throughout):**
```bash
MVN="mvn -q -Dmaven.gitcommitid.skip=true -Dgit.skip=true"
```

---

## File / module structure after this plan

New modules (each a directory with `pom.xml` + moved `src/`):
- `api-jackson/` — `api/jackson/**` + 4 mix-ins. Deps: api, jackson-databind/yaml/jsr310/blackbird.
- `reference/` — `common/csl/**` + CSL converters + citation formatter impl. Deps: api, citeproc-java, jbibtex, locales.
- `kryo/` — `common/kryo/**`. Deps: api, kryo, mapdb, fastutil.

Reshaped existing modules:
- `api/` — slim POJO model + jackson-annotations. Loses databind, kryo, mapdb, httpclient,
  citeproc, jbibtex, locales, antlr, metrics-core, jsoup, univocity (test-only jena stays out).
- `dao/` — gains `common/io/DownloadUtil.java` + httpclient5/httpcore5.
- `reader/`, `reader-xls/` — depend on slim `api` only (no `api-jackson`).
- `metadata/` — depends on slim `api` + `api-jackson` + `reference` + `parser`.

New source files created (with real logic):
- `api/src/main/java/life/catalogue/api/model/CSLType.java` — COL-owned enum.
- `api/src/main/java/life/catalogue/api/model/CitationFormatter.java` — hook interface + registry.
- `reference/src/main/java/life/catalogue/common/csl/CslTypeConverter.java` — COL↔citeproc enum map.
- `reference/src/main/java/life/catalogue/common/csl/CitationConverter.java` — model → `CSLItemData`.
- `reference/src/main/java/life/catalogue/common/csl/CslCitationFormatter.java` — `CitationFormatter` impl.
- `api-jackson/src/main/java/life/catalogue/api/jackson/IdentifierMixin.java`, `DOIMixin.java`,
  `DatasetSettingsMixin.java`, `CitationCSLMixin.java` — databind mix-ins.

---

## Task 1: Introduce COL-owned `CSLType` enum and swap all references

Severs the only *structural* citeproc coupling in the model. Everything stays in `api`
(citeproc still on the classpath) so this task is self-contained and compiles.

**Files:**
- Create: `api/src/main/java/life/catalogue/api/model/CSLType.java`
- Create: `api/src/test/java/life/catalogue/api/model/CSLTypeTest.java`
- Modify: `api/src/main/java/life/catalogue/api/model/CslData.java` (field type + import)
- Modify: `api/src/main/java/life/catalogue/api/model/Citation.java` (field type, `create`, `isUnparsed`, `toCSL`, equals uses)
- Modify: `api/src/main/java/life/catalogue/api/jackson/CSLTypeSerde.java` (target COL enum)
- Modify: `api/src/main/java/life/catalogue/api/jackson/ApiModule.java` (import + `addSerializer/addDeserializer` type)
- Modify: `api/src/main/java/life/catalogue/api/jackson/PermissiveEnumSerde.java` (import)
- Modify: `api/src/main/java/life/catalogue/common/datapackage/DataPackageBuilder.java` (import + usage)
- Modify: `api/src/main/java/life/catalogue/common/kryo/ApiKryoPool.java` (register COL enum)

**Interfaces:**
- Produces: `life.catalogue.api.model.CSLType` — enum whose `name()` matches citeproc
  `de.undercouch.citeproc.csl.CSLType` constant names 1:1 and whose `toString()` returns the
  identical CSL id string. Method `CSLType.fromString(String)` parses a CSL id (nullable).

- [ ] **Step 1: Write the failing round-trip test**

`api/src/test/java/life/catalogue/api/model/CSLTypeTest.java`:
```java
package life.catalogue.api.model;

import de.undercouch.citeproc.csl.CSLType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Guards that the COL-owned CSLType mirrors citeproc's enum 1:1 in name and JSON id. */
public class CSLTypeTest {

  @Test
  public void mirrorsCiteproc() {
    for (CSLType cp : CSLType.values()) {
      life.catalogue.api.model.CSLType col =
        assertDoesNotThrow(() -> life.catalogue.api.model.CSLType.valueOf(cp.name()),
          "COL CSLType is missing citeproc value " + cp.name());
      assertEquals(cp.toString(), col.toString(),
        "JSON id mismatch for " + cp.name());
    }
    // and every COL value maps back to a citeproc value
    for (var col : life.catalogue.api.model.CSLType.values()) {
      assertDoesNotThrow(() -> CSLType.valueOf(col.name()),
        "citeproc has no value for COL " + col.name());
    }
  }

  @Test
  public void fromStringRoundTrip() {
    assertEquals(life.catalogue.api.model.CSLType.ARTICLE_JOURNAL,
      life.catalogue.api.model.CSLType.fromString("article-journal"));
    assertEquals(life.catalogue.api.model.CSLType.DATASET,
      life.catalogue.api.model.CSLType.fromString("dataset"));
    assertNull(life.catalogue.api.model.CSLType.fromString("not-a-type"));
  }
}
```

- [ ] **Step 2: Run it to confirm it fails to compile (COL CSLType absent)**

```bash
$MVN -pl api test -Dtest=CSLTypeTest
```
Expected: compilation failure — `cannot find symbol life.catalogue.api.model.CSLType`.

- [ ] **Step 3: Create the COL-owned enum**

`api/src/main/java/life/catalogue/api/model/CSLType.java`. Each constant carries the CSL id
string; `toString()` returns it (matching citeproc so JSON is unchanged). The value list below
mirrors citeproc-java; the test in Step 1 fails loudly if the build's citeproc version has
values not listed here — add any it reports.
```java
package life.catalogue.api.model;

/**
 * COL-owned copy of the CSL citation type vocabulary, replacing the compile dependency on
 * de.undercouch.citeproc.csl.CSLType in the slim api model. Constant names and toString() ids
 * mirror citeproc exactly so JSON serialisation is unchanged. See
 * http://docs.citationstyles.org/en/stable/specification.html#appendix-iii-types
 */
public enum CSLType {
  ARTICLE("article"),
  ARTICLE_MAGAZINE("article-magazine"),
  ARTICLE_NEWSPAPER("article-newspaper"),
  ARTICLE_JOURNAL("article-journal"),
  BILL("bill"),
  BOOK("book"),
  BROADCAST("broadcast"),
  CHAPTER("chapter"),
  DATASET("dataset"),
  ENTRY("entry"),
  ENTRY_DICTIONARY("entry-dictionary"),
  ENTRY_ENCYCLOPEDIA("entry-encyclopedia"),
  FIGURE("figure"),
  GRAPHIC("graphic"),
  INTERVIEW("interview"),
  LEGISLATION("legislation"),
  LEGAL_CASE("legal_case"),
  MANUSCRIPT("manuscript"),
  MAP("map"),
  MOTION_PICTURE("motion_picture"),
  MUSICAL_SCORE("musical_score"),
  PAMPHLET("pamphlet"),
  PAPER_CONFERENCE("paper-conference"),
  PATENT("patent"),
  POST("post"),
  POST_WEBLOG("post-weblog"),
  PERSONAL_COMMUNICATION("personal_communication"),
  REPORT("report"),
  REVIEW("review"),
  REVIEW_BOOK("review-book"),
  SONG("song"),
  SPEECH("speech"),
  THESIS("thesis"),
  TREATY("treaty"),
  WEBPAGE("webpage");

  private final String id;

  CSLType(String id) {
    this.id = id;
  }

  @Override
  public String toString() {
    return id;
  }

  /** Parses a CSL id string (e.g. "article-journal") into the enum, or null if unknown. */
  public static CSLType fromString(String value) {
    if (value == null) return null;
    String norm = value.trim().toUpperCase().replaceAll("[_ -]+", "_");
    try {
      return valueOf(norm);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
```

- [ ] **Step 4: Swap `CSLType` references from citeproc to COL in the model + serde + kryo + datapackage**

In each modified file, replace `import de.undercouch.citeproc.csl.CSLType;` with
`import life.catalogue.api.model.CSLType;` (or a fully-qualified reference where both were
imported). Specifics:

- `CslData.java`: change the `private CSLType type;` import to the COL enum. No other change.
- `Citation.java`: change the import; `create()` uses `CSLType.BOOK` (unchanged token);
  `isUnparsed()` uses `CSLType.BOOK` (unchanged); the getter/setter/field now use COL enum.
  In `toCSL()`, the builder needs a citeproc type — convert inline for now:
  `builder.type(type == null ? null : de.undercouch.citeproc.csl.CSLType.valueOf(type.name()))`.
  (This inline conversion moves into `reference` in Task 2.)
- `CSLTypeSerde.java`: retarget to the COL enum. Replace `de.undercouch.citeproc.csl.CSLType`
  with `life.catalogue.api.model.CSLType`, and replace the body of `parse()` with a call to
  `CSLType.fromString(value)`:
```java
import life.catalogue.api.model.CSLType;
// ...
public static CSLType parse(String value) {
  CSLType t = CSLType.fromString(value);
  if (t == null) {
    LOG.info("Invalid CSLType: {}", value);
  }
  return t;
}
```
  The `Serializer` still calls `value.toString()` (now the COL enum's id) — JSON unchanged.
- `ApiModule.java`: change `import de.undercouch.citeproc.csl.CSLType;` to the COL enum; the
  `addDeserializer(CSLType.class, new CSLTypeSerde.Deserializer())` and
  `addSerializer(CSLType.class, new CSLTypeSerde.Serializer())` lines now bind the COL type.
- `PermissiveEnumSerde.java`: change the `CSLType` import to the COL enum (used to exclude
  CSLType from the generic permissive-enum handling).
- `DataPackageBuilder.java`: change the `CSLType` import to the COL enum; the usage
  (a `CSLType` reference) compiles unchanged.
- `ApiKryoPool.java`: change the `CSLType` import to the COL enum so kryo registers the COL
  enum (kryo registers enums by class; registering the COL enum is correct going forward).

- [ ] **Step 5: Run the CSLType test — expect PASS**

```bash
$MVN -pl api test -Dtest=CSLTypeTest
```
Expected: PASS. If it reports a missing citeproc value, add that constant (with its
`cp.toString()` id) to `CSLType.java` and rerun.

- [ ] **Step 6: Build api + its dependents to confirm no other CSLType references remain**

```bash
$MVN -pl api -am install
grep -rn 'de.undercouch.citeproc.csl.CSLType' api/src/main/java
```
Expected: build SUCCESS; the grep prints only the intentional inline conversion in
`Citation.java` and `Dataset.java` (`CSLType.valueOf(type.name())` / `.type(CSLType.X)` inside
`toCSL`), which Task 2 relocates. No `CSLType` *field* declarations use citeproc.

- [ ] **Step 7: Full-model serialization regression check**

```bash
$MVN -pl api test
```
Expected: PASS — existing `api` serde tests (e.g. `ApiModuleTest`, dataset/citation JSON
fixtures) still pass, proving JSON output is unchanged by the enum swap.

- [ ] **Step 8: Commit**

```bash
git add api/src/main/java/life/catalogue/api/model/CSLType.java \
        api/src/test/java/life/catalogue/api/model/CSLTypeTest.java \
        api/src/main/java/life/catalogue/api/model/CslData.java \
        api/src/main/java/life/catalogue/api/model/Citation.java \
        api/src/main/java/life/catalogue/api/jackson/CSLTypeSerde.java \
        api/src/main/java/life/catalogue/api/jackson/ApiModule.java \
        api/src/main/java/life/catalogue/api/jackson/PermissiveEnumSerde.java \
        api/src/main/java/life/catalogue/common/datapackage/DataPackageBuilder.java \
        api/src/main/java/life/catalogue/common/kryo/ApiKryoPool.java
git commit -m "refactor(api): introduce COL-owned CSLType enum, sever citeproc from model"
```

---

## Task 2: Extract `reference` module (citeproc + jbibtex) with citation hook

Moves the CSL formatting layer and the `toCSL*()` converters out of `api` into a new
downstream `reference` module, and replaces the model's self-formatting with a hook. After
this task **`api` has no citeproc or jbibtex dependency**.

**Files:**
- Create: `reference/pom.xml`
- Modify: root `pom.xml` (add `<module>reference</module>`)
- Create: `api/src/main/java/life/catalogue/api/model/CitationFormatter.java`
- Move: `api/src/main/java/life/catalogue/common/csl/**` → `reference/src/main/java/life/catalogue/common/csl/**`
- Create: `reference/src/main/java/life/catalogue/common/csl/CslTypeConverter.java`
- Create: `reference/src/main/java/life/catalogue/common/csl/CitationConverter.java`
- Create: `reference/src/main/java/life/catalogue/common/csl/DatasetCitationConverter.java`
- Create: `reference/src/main/java/life/catalogue/common/csl/CslCitationFormatter.java`
- Modify: `api/.../model/Citation.java`, `Dataset.java`, `Agent.java`, `CslName.java`,
  `common/date/FuzzyDate.java` (remove citeproc: delete `toCSL*()`/`toCSLDate()` bodies, delegate
  getters to hook)
- Modify: `api/pom.xml` (remove citeproc-java, jbibtex, locales, antlr4-runtime)
- Modify: `webservice/.../WsServer.java` (register the formatter at startup)
- Move CSL tests from `api/src/test/.../csl` to `reference/src/test/...`

**Interfaces:**
- Consumes: `life.catalogue.api.model.CSLType` (Task 1).
- Produces:
  - `life.catalogue.api.model.CitationFormatter` — interface with
    `String citationHtml(Citation)`, `String citationText(Citation)`,
    `String citationHtml(Dataset)`, `String citationText(Dataset)`; static
    `register(CitationFormatter)` / `get()` via an `AtomicReference` holder.
  - `life.catalogue.common.csl.CslTypeConverter` — `static de.undercouch.citeproc.csl.CSLType
    toCiteproc(CSLType)` and `static CSLType fromCiteproc(de.undercouch.citeproc.csl.CSLType)`.
  - `life.catalogue.common.csl.CitationConverter.toCSL(Citation)` → `CSLItemData`.
  - `life.catalogue.common.csl.DatasetCitationConverter.toCSL(Dataset)` → `CSLItemData`.
  - `life.catalogue.common.csl.CslCitationFormatter implements CitationFormatter`.

- [ ] **Step 1: Write the failing hook + citation-output regression test**

`api/src/test/java/life/catalogue/api/model/CitationFormatterTest.java`:
```java
package life.catalogue.api.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CitationFormatterTest {

  @Test
  public void fallbackWhenUnregistered() {
    CitationFormatter.register(null);
    Citation c = Citation.create("Some title");
    assertNull(c.getCitation());       // no formatter -> null, no exception
    assertNull(c.getCitationText());
  }

  @Test
  public void delegatesToRegisteredFormatter() {
    CitationFormatter.register(new CitationFormatter() {
      public String citationHtml(Citation c) { return "H:" + c.getTitle(); }
      public String citationText(Citation c) { return "T:" + c.getTitle(); }
      public String citationHtml(Dataset d) { return "DH"; }
      public String citationText(Dataset d) { return "DT"; }
    });
    try {
      Citation c = Citation.create("Title");
      assertEquals("H:Title", c.getCitation());
      assertEquals("T:Title", c.getCitationText());
    } finally {
      CitationFormatter.register(null);
    }
  }
}
```

- [ ] **Step 2: Run it — expect compile failure (CitationFormatter absent)**

```bash
$MVN -pl api test -Dtest=CitationFormatterTest
```
Expected: compilation failure — `cannot find symbol CitationFormatter`.

- [ ] **Step 3: Create the hook interface in slim api**

`api/src/main/java/life/catalogue/api/model/CitationFormatter.java`:
```java
package life.catalogue.api.model;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Hook that renders formatted citation strings for {@link Citation} and {@link Dataset}.
 * The citeproc-backed implementation lives in the reference module and is registered once at
 * application startup, keeping the api model free of citeproc. When no formatter is registered
 * (e.g. plain unit tests) the model's citation getters return null.
 */
public interface CitationFormatter {
  String citationHtml(Citation citation);
  String citationText(Citation citation);
  String citationHtml(Dataset dataset);
  String citationText(Dataset dataset);

  AtomicReference<CitationFormatter> INSTANCE = new AtomicReference<>();

  static void register(CitationFormatter formatter) {
    INSTANCE.set(formatter);
  }

  static CitationFormatter get() {
    return INSTANCE.get();
  }
}
```

- [ ] **Step 4: Rewire `Citation` getters to the hook and drop citeproc**

In `Citation.java`: delete imports `de.undercouch.citeproc.*`, `life.catalogue.common.csl.CslUtil`,
`FuzzyDateCSLSerde` stays (moved to mix-in in Task 5 — for now keep the `@JsonSerialize`
annotations; they still compile because api still has databind until Task 5). Delete the
`toCSL()` and `toNames()` methods (relocated in Step 7). Replace the citation getters:
```java
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  public String getCitation() {
    if (_citation == null) {
      CitationFormatter f = CitationFormatter.get();
      if (f != null) _citation = f.citationHtml(this);
    }
    return _citation;
  }

  @JsonIgnore
  public String getCitationText() {
    if (_citationText == null) {
      CitationFormatter f = CitationFormatter.get();
      if (f != null) _citationText = f.citationText(this);
    }
    return _citationText;
  }
```

- [ ] **Step 5: Rewire `Dataset`, `Agent`, `CslName`, `FuzzyDate` to drop citeproc**

- `Dataset.java`: delete `import de.undercouch.citeproc.*`; delete `toCSL()`,
  `toCSLBuilder()`, `toNamesArray()` (relocated to `DatasetCitationConverter`); replace the
  `getCitation()`/`getCitationText()` (lines ~1011/1019) bodies with the same hook-delegation
  pattern as Step 4 but calling `f.citationHtml(this)` / `f.citationText(this)` (the `Dataset`
  overloads).
- `Agent.java`: delete `import de.undercouch.citeproc.csl.CSLName*`; delete its `toCSL()`
  method (relocated into `CitationConverter`/`DatasetCitationConverter` as a private helper).
- `CslName.java`: delete `import de.undercouch.citeproc.csl.CSLName`; delete its `toCSL()`
  method (relocated to `CitationConverter.toName`).
- `FuzzyDate.java`: delete `import de.undercouch.citeproc.csl.CSLDate*`; delete `toCSLDate()`
  (relocated to `CitationConverter.toDate`).

- [ ] **Step 6: Run the hook test — expect PASS**

```bash
$MVN -pl api test -Dtest=CitationFormatterTest
```
Expected: PASS. (`api` still has citeproc on the classpath at this moment via `common/csl`,
removed in Step 11.)

- [ ] **Step 7: Create the `reference` module skeleton**

`reference/pom.xml`:
```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.catalogueoflife</groupId>
    <artifactId>motherpom</artifactId>
    <version>1.2.3-SNAPSHOT</version>
  </parent>

  <artifactId>reference</artifactId>
  <name>CLB reference</name>
  <description>Citation formatting (CSL/citeproc, BibTeX) for the COL API model</description>

  <dependencies>
    <dependency>
      <groupId>org.catalogueoflife</groupId>
      <artifactId>api</artifactId>
    </dependency>
    <dependency>
      <groupId>de.undercouch</groupId>
      <artifactId>citeproc-java</artifactId>
    </dependency>
    <dependency>
      <groupId>org.jbibtex</groupId>
      <artifactId>jbibtex</artifactId>
    </dependency>
    <dependency>
      <groupId>org.citationstyles</groupId>
      <artifactId>locales</artifactId>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-api</artifactId>
    </dependency>
    <!-- TEST SCOPE -->
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter-api</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>ch.qos.logback</groupId>
      <artifactId>logback-classic</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.catalogueoflife</groupId>
      <artifactId>api</artifactId>
      <classifier>tests</classifier>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```
Add `<module>reference</module>` to the root `pom.xml` `<modules>` list.

- [ ] **Step 8: Move the CSL package into `reference` (packages unchanged)**

```bash
mkdir -p reference/src/main/java/life/catalogue/common
git mv api/src/main/java/life/catalogue/common/csl reference/src/main/java/life/catalogue/common/csl
# move any CSL tests too
mkdir -p reference/src/test/java/life/catalogue/common
git mv api/src/test/java/life/catalogue/common/csl reference/src/test/java/life/catalogue/common/csl 2>/dev/null || true
```

- [ ] **Step 9: Add the converters + formatter in `reference`**

`CslTypeConverter.java` maps COL↔citeproc by `name()`:
```java
package life.catalogue.common.csl;

import life.catalogue.api.model.CSLType;

public class CslTypeConverter {
  public static de.undercouch.citeproc.csl.CSLType toCiteproc(CSLType t) {
    return t == null ? null : de.undercouch.citeproc.csl.CSLType.valueOf(t.name());
  }
  public static CSLType fromCiteproc(de.undercouch.citeproc.csl.CSLType t) {
    return t == null ? null : CSLType.valueOf(t.name());
  }
}
```
`CitationConverter.java` holds the body of the old `Citation.toCSL()` (from
`api/.../Citation.java` before this task), reading the `Citation` via getters and using
`CslTypeConverter.toCiteproc(c.getType())` for the type, plus the relocated `toNames`/`toName`/
`toDate` helpers (bodies of the old `Citation.toNames`, `CslName.toCSL`, `FuzzyDate.toCSLDate`).
`DatasetCitationConverter.java` holds the body of the old `Dataset.toCSLBuilder()/toCSL()` and
`toNamesArray()` (`Agent.toCSL` becomes a private helper here). `CslCitationFormatter.java`:
```java
package life.catalogue.common.csl;

import life.catalogue.api.model.Citation;
import life.catalogue.api.model.CitationFormatter;
import life.catalogue.api.model.Dataset;

public class CslCitationFormatter implements CitationFormatter {
  public String citationHtml(Citation c) { return CslUtil.buildCitationHtml(CitationConverter.toCSL(c)); }
  public String citationText(Citation c) { return CslUtil.buildCitation(CitationConverter.toCSL(c)); }
  public String citationHtml(Dataset d) { return CslUtil.buildCitationHtml(DatasetCitationConverter.toCSL(d)); }
  public String citationText(Dataset d) { return CslUtil.buildCitation(DatasetCitationConverter.toCSL(d)); }
}
```

- [ ] **Step 10: Register the formatter at application startup**

In `webservice/.../WsServer.java`, in the same area that touches `ApiModule`/pools during
`run(...)` (or a static init), add:
```java
life.catalogue.api.model.CitationFormatter.register(new life.catalogue.common.csl.CslCitationFormatter());
```
Add a `reference` dependency to `webservice/pom.xml`. Also register it in any test base that
asserts citation output (search: `getCitation()`/`getCitationText()` assertions) — the
`reference` module's own tests register it in a `@BeforeAll`.

- [ ] **Step 11: Remove citeproc/jbibtex/locales/antlr from `api/pom.xml`**

Delete these `<dependency>` blocks from `api/pom.xml`: `jbibtex`, `citeproc-java`, `locales`,
`antlr4-runtime` (antlr was a transitive citeproc need declared explicitly).

- [ ] **Step 12: Build reference + api and verify citeproc is gone from api**

```bash
$MVN -pl api,reference -am install
$MVN -pl api dependency:tree | grep -iE 'citeproc|jbibtex|citationstyles' || echo "OK: api has no citeproc/jbibtex"
```
Expected: build SUCCESS; the grep prints the "OK" line.

- [ ] **Step 13: Repoint callers of the deleted model `toCSL()` methods**

Deleting `Citation.toCSL()` / `Dataset.toCSL()` / `FuzzyDate.toCSLDate()` breaks these
call sites — repoint each to the new converters (all in `reference`):

| File | Old | New |
|---|---|---|
| `dao/.../dao/ReferenceFactory.java:207` | `CslDataConverter.toCslData(c.toCSL())` | `CslDataConverter.toCslData(CitationConverter.toCSL(c))` |
| `webservice/.../writers/CitationCslBodyWriter.java:37` | `cit.toCSL()` | `CitationConverter.toCSL(cit)` |
| `webservice/.../writers/DatasetCslBodyWriter.java:37` | `dataset.toCSL()` | `DatasetCitationConverter.toCSL(dataset)` |
| `webservice/.../writers/CitationBibtexBodyWriter.java:37` | `CslUtil.toBibTexString(cit.toCSL())` | `CslUtil.toBibTexString(CitationConverter.toCSL(cit))` |
| `webservice/.../writers/DatasetBibtexBodyWriter.java:37` | `CslUtil.toBibTexString(dataset.toCSL())` | `CslUtil.toBibTexString(DatasetCitationConverter.toCSL(dataset))` |
| `api/.../model/CitationTest.java:44` | `c.toCSL()` | move test to `reference`, use `CitationConverter.toCSL(c)` |
| `api/.../model/DatasetTest.java:251,268` | `d.toCSL()` | move those assertions to `reference`, use `DatasetCitationConverter.toCSL(d)` |
| `metadata/.../coldp/MetadataParserTest.java:296` | `CslUtil.buildCitation(c.toCSL())` | `CslUtil.buildCitation(CitationConverter.toCSL(c))` |

Add the `import life.catalogue.common.csl.CitationConverter;` /
`DatasetCitationConverter;` to each edited file.

- [ ] **Step 14: Repoint former in-`api` CSL consumers and rebuild the reactor**

Modules that imported `life.catalogue.common.csl.*` (core, dao, importer, webservice — and
metadata which already declares citeproc) now need a `reference` dependency. Add
```xml
<dependency>
  <groupId>org.catalogueoflife</groupId>
  <artifactId>reference</artifactId>
</dependency>
```
to each pom the build reports as missing `life.catalogue.common.csl` symbols. Then:
```bash
$MVN -DskipTests install
```
Expected: build SUCCESS across all modules.

- [ ] **Step 15: Citation-output regression across the app**

```bash
$MVN -pl webservice,metadata,core test
```
Expected: PASS — citation/dataset JSON and formatting tests still pass with the formatter
registered at startup.

- [ ] **Step 16: Commit**

```bash
git add -A
git commit -m "refactor: extract reference module (citeproc+jbibtex), citation via hook; api is citeproc-free"
```

---

## Task 3: Extract `kryo` module (kryo + mapdb)

`common/kryo/**` is self-contained and only references the model (now citeproc-free). After
this task **`api` has no kryo or mapdb dependency**.

**Files:**
- Create: `kryo/pom.xml`; Modify root `pom.xml` (add module)
- Move: `api/src/main/java/life/catalogue/common/kryo/**` → `kryo/src/main/java/life/catalogue/common/kryo/**`
- Move: `api/src/test/java/life/catalogue/common/kryo/**` → `kryo/src/test/...` (if present)
- Modify: `api/pom.xml` (remove mapdb, kryo)
- Modify: `core/pom.xml`, `dao/pom.xml`, `importer/pom.xml`, `parser/pom.xml` (add `kryo` dep)

**Interfaces:**
- Consumes: `life.catalogue.api.model.*`, `life.catalogue.api.model.CSLType`.
- Produces: `life.catalogue.common.kryo.*` (unchanged package) in a new artifact.

- [ ] **Step 1: Create `kryo/pom.xml`**
```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.catalogueoflife</groupId>
    <artifactId>motherpom</artifactId>
    <version>1.2.3-SNAPSHOT</version>
  </parent>

  <artifactId>kryo</artifactId>
  <name>CLB kryo</name>
  <description>Kryo + MapDB binary serialisation for COL API model classes</description>

  <dependencies>
    <dependency>
      <groupId>org.catalogueoflife</groupId>
      <artifactId>api</artifactId>
    </dependency>
    <dependency>
      <groupId>org.mapdb</groupId>
      <artifactId>mapdb</artifactId>
    </dependency>
    <dependency>
      <groupId>com.esotericsoftware</groupId>
      <artifactId>kryo</artifactId>
    </dependency>
    <dependency>
      <groupId>it.unimi.dsi</groupId>
      <artifactId>fastutil</artifactId>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-api</artifactId>
    </dependency>
    <!-- TEST SCOPE -->
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter-api</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>ch.qos.logback</groupId>
      <artifactId>logback-classic</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.catalogueoflife</groupId>
      <artifactId>api</artifactId>
      <classifier>tests</classifier>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```
Add `<module>kryo</module>` to root `pom.xml`.

- [ ] **Step 2: Move the package (unchanged package names)**
```bash
mkdir -p kryo/src/main/java/life/catalogue/common
git mv api/src/main/java/life/catalogue/common/kryo kryo/src/main/java/life/catalogue/common/kryo
mkdir -p kryo/src/test/java/life/catalogue/common
git mv api/src/test/java/life/catalogue/common/kryo kryo/src/test/java/life/catalogue/common/kryo 2>/dev/null || true
```

- [ ] **Step 3: Remove kryo + mapdb from `api/pom.xml`**

Delete the `mapdb` and `kryo` `<dependency>` blocks from `api/pom.xml`. (fastutil stays — the
model uses it; it now also lives in `kryo`.)

- [ ] **Step 4: Add `kryo` dependency to consumers**

Add the `kryo` dependency block (groupId `org.catalogueoflife`, artifactId `kryo`) to
`core/pom.xml`, `dao/pom.xml`, `importer/pom.xml`, `parser/pom.xml`.

- [ ] **Step 5: Build and verify api is kryo/mapdb-free**
```bash
$MVN -pl kryo -am install
$MVN -pl api dependency:tree | grep -iE 'mapdb|esotericsoftware' || echo "OK: api has no kryo/mapdb"
$MVN -DskipTests install
```
Expected: build SUCCESS; grep prints "OK".

- [ ] **Step 6: Run kryo + dependent tests**
```bash
$MVN -pl kryo,dao test
```
Expected: PASS (kryo round-trip tests, incl. the ones moved from `api`).

- [ ] **Step 7: Commit**
```bash
git add -A
git commit -m "refactor: extract kryo module (kryo+mapdb); api no longer depends on them"
```

---

## Task 4: Move `DownloadUtil` (httpclient) into `dao`

`api` has zero real uses of `DownloadUtil`; its consumers (dao, importer, webservice) are all
`dao`-or-downstream. Package stays `life.catalogue.common.io`, so no import changes.

**Files:**
- Move: `api/src/main/java/life/catalogue/common/io/DownloadUtil.java` → `dao/src/main/java/life/catalogue/common/io/DownloadUtil.java`
- Move any `DownloadUtil` test similarly.
- Modify: `api/pom.xml` (remove httpclient5, httpcore5)
- Modify: `dao/pom.xml` (add httpclient5, httpcore5)

- [ ] **Step 1: Move the file (unchanged package)**
```bash
mkdir -p dao/src/main/java/life/catalogue/common/io
git mv api/src/main/java/life/catalogue/common/io/DownloadUtil.java dao/src/main/java/life/catalogue/common/io/DownloadUtil.java
# move its test if one exists
git mv api/src/test/java/life/catalogue/common/io/DownloadUtilTest.java dao/src/test/java/life/catalogue/common/io/DownloadUtilTest.java 2>/dev/null || true
```

- [ ] **Step 2: Move httpclient deps from api to dao**

Remove the `httpclient5` and `httpcore5` `<dependency>` blocks from `api/pom.xml`; add both to
`dao/pom.xml`.

- [ ] **Step 3: Build and verify api is httpclient-free**
```bash
$MVN -pl api dependency:tree | grep -iE 'httpclient5|httpcore5' || echo "OK: api has no httpclient"
$MVN -pl dao -am install -DskipTests
```
Expected: grep prints "OK"; build SUCCESS. If any module fails to find `DownloadUtil`, it was
a consumer that lacked a `dao` dependency — add it (importer/webservice already depend on dao).

- [ ] **Step 4: Commit**
```bash
git add -A
git commit -m "refactor: move DownloadUtil+httpclient from api into dao"
```

---

## Task 5: Extract `api-jackson` module (databind serde) and slim `api` to annotations-only

Moves `api/jackson/**` into a new `api-jackson` module and replaces the 4 model files'
`@JsonSerialize/@JsonDeserialize(using=…)` with mix-ins registered in `ApiModule`. After this
task **`api` depends only on jackson-annotations** (no databind).

**Files:**
- Create: `api-jackson/pom.xml`; Modify root `pom.xml`
- Move: `api/src/main/java/life/catalogue/api/jackson/**` → `api-jackson/src/main/java/life/catalogue/api/jackson/**`
- Move: `api/src/test/java/life/catalogue/api/jackson/**` → `api-jackson/src/test/...`
- Create mix-ins: `api-jackson/.../jackson/IdentifierMixin.java`, `DOIMixin.java`,
  `DatasetSettingsMixin.java`, `CitationCSLMixin.java`
- Modify: `ApiModule.java` (register the 4 mix-ins)
- Modify: `api/.../model/Identifier.java`, `DOI.java`, `DatasetSettings.java`, `Citation.java`
  (strip databind annotations + imports)
- Modify: `api/pom.xml` (remove jackson-databind, -dataformat-yaml, -datatype-jsr310,
  -module-blackbird; keep jackson-annotations)
- Modify: serde-consuming poms (dao, core, importer, metadata, webservice, doi, +any the build
  flags) to add `api-jackson`

**Interfaces:**
- Consumes: model classes from slim `api`.
- Produces: `life.catalogue.api.jackson.ApiModule` (unchanged FQN + `MAPPER`) in `api-jackson`.

- [ ] **Step 1: Create `api-jackson/pom.xml`**
```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.catalogueoflife</groupId>
    <artifactId>motherpom</artifactId>
    <version>1.2.3-SNAPSHOT</version>
  </parent>

  <artifactId>api-jackson</artifactId>
  <name>CLB API Jackson</name>
  <description>Jackson databind serde (ApiModule) for the COL API model</description>

  <dependencies>
    <dependency>
      <groupId>org.catalogueoflife</groupId>
      <artifactId>api</artifactId>
    </dependency>
    <dependency>
      <groupId>com.fasterxml.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId>
    </dependency>
    <dependency>
      <groupId>com.fasterxml.jackson.dataformat</groupId>
      <artifactId>jackson-dataformat-yaml</artifactId>
    </dependency>
    <dependency>
      <groupId>com.fasterxml.jackson.datatype</groupId>
      <artifactId>jackson-datatype-jsr310</artifactId>
    </dependency>
    <dependency>
      <groupId>com.fasterxml.jackson.module</groupId>
      <artifactId>jackson-module-blackbird</artifactId>
    </dependency>
    <dependency>
      <groupId>com.google.guava</groupId>
      <artifactId>guava</artifactId>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-api</artifactId>
    </dependency>
    <!-- TEST SCOPE -->
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter-api</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.junit.vintage</groupId>
      <artifactId>junit-vintage-engine</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>ch.qos.logback</groupId>
      <artifactId>logback-classic</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.catalogueoflife</groupId>
      <artifactId>api</artifactId>
      <classifier>tests</classifier>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```
Add `<module>api-jackson</module>` to root `pom.xml`.

- [ ] **Step 2: Move the jackson package (unchanged package names)**
```bash
mkdir -p api-jackson/src/main/java/life/catalogue/api
git mv api/src/main/java/life/catalogue/api/jackson api-jackson/src/main/java/life/catalogue/api/jackson
mkdir -p api-jackson/src/test/java/life/catalogue/api
git mv api/src/test/java/life/catalogue/api/jackson api-jackson/src/test/java/life/catalogue/api/jackson 2>/dev/null || true
```

- [ ] **Step 3: Create the 4 mix-ins in `api-jackson`**

`IdentifierMixin.java` (class-level, mirrors the annotations removed from `Identifier`):
```java
package life.catalogue.api.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(using = IdentifierSerde.Serializer.class)
@JsonDeserialize(using = IdentifierSerde.Deserializer.class)
abstract class IdentifierMixin { }
```
`DOIMixin.java`:
```java
package life.catalogue.api.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(using = DOISerde.Serializer.class)
@JsonDeserialize(using = DOISerde.Deserializer.class)
abstract class DOIMixin { }
```
`DatasetSettingsMixin.java`:
```java
package life.catalogue.api.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = SettingsDeserializer.class)
abstract class DatasetSettingsMixin { }
```
`CitationCSLMixin.java` (field-level; annotate the `issued`/`accessed` accessors):
```java
package life.catalogue.api.jackson;

import life.catalogue.common.date.FuzzyDate;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

abstract class CitationCSLMixin {
  @JsonSerialize(using = FuzzyDateCSLSerde.Serializer.class)
  @JsonDeserialize(using = FuzzyDateCSLSerde.Deserializer.class)
  abstract FuzzyDate getIssued();

  @JsonSerialize(using = FuzzyDateCSLSerde.Serializer.class)
  @JsonDeserialize(using = FuzzyDateCSLSerde.Deserializer.class)
  abstract FuzzyDate getAccessed();
}
```

- [ ] **Step 4: Register the mix-ins in `ApiModule.setupModule`**

In `api-jackson/.../ApiModule.java`, extend the existing `setMixInAnnotations` block:
```java
    ctxt.setMixInAnnotations(Authorship.class, AuthorshipMixIn.class);
    ctxt.setMixInAnnotations(Term.class, TermMixIn.class);
    ctxt.setMixInAnnotations(life.catalogue.api.model.Identifier.class, IdentifierMixin.class);
    ctxt.setMixInAnnotations(life.catalogue.api.model.DOI.class, DOIMixin.class);
    ctxt.setMixInAnnotations(life.catalogue.api.model.DatasetSettings.class, DatasetSettingsMixin.class);
    ctxt.setMixInAnnotations(life.catalogue.api.model.Citation.class, CitationCSLMixin.class);
```

- [ ] **Step 5: Strip databind annotations from the 4 model files**

- `Identifier.java`: delete the two `import com.fasterxml.jackson.databind.annotation.*` lines
  and the class-level `@JsonSerialize/@JsonDeserialize` annotations.
- `DOI.java`: same (delete both imports + both class annotations).
- `DatasetSettings.java`: delete the `import …databind.annotation.JsonDeserialize` line and the
  class-level `@JsonDeserialize` annotation.
- `Citation.java`: delete the `import …databind.annotation.JsonSerialize/JsonDeserialize` lines
  and the four field annotations on `issued`/`accessed`. Confirm `Citation` now has **no**
  `com.fasterxml.jackson.databind` import (only `com.fasterxml.jackson.annotation.*` remain).

- [ ] **Step 6: Remove databind deps from `api/pom.xml`**

Delete these blocks from `api/pom.xml`: `jackson-databind`, `jackson-dataformat-yaml`,
`jackson-datatype-jsr310`, `jackson-module-blackbird`. **Keep** `jackson-core` only if the model
still references it directly (grep `com.fasterxml.jackson.core` in `api/src/main`; if empty,
remove it too) and **keep** `jackson-annotations`.

- [ ] **Step 7: Add `api-jackson` to serde consumers and build**

Add the `api-jackson` dependency (groupId `org.catalogueoflife`, artifactId `api-jackson`) to
every module the build reports as missing `life.catalogue.api.jackson.*` — start with the known
databind consumers (dao, core, importer, metadata, webservice) and `doi`. Iterate:
```bash
$MVN -pl api-jackson -am install -DskipTests
$MVN -DskipTests install   # add api-jackson to whatever module fails, repeat until green
```
Expected: eventual build SUCCESS.

- [ ] **Step 8: Verify `api` is databind-free**
```bash
$MVN -pl api dependency:tree | grep -iE 'jackson-databind|jackson-dataformat|jsr310|blackbird' \
  || echo "OK: api has only jackson-annotations"
grep -rn 'com.fasterxml.jackson.databind' api/src/main/java || echo "OK: no databind refs in api"
```
Expected: both "OK" lines.

- [ ] **Step 9: Serde regression — the moved serde tests must pass**
```bash
$MVN -pl api-jackson test
```
Expected: PASS — `ApiModuleTest` and JSON fixtures (Identifier, DOI, DatasetSettings, Citation
round-trips) prove the mix-ins reproduce the previous serialization exactly.

- [ ] **Step 10: Commit**
```bash
git add -A
git commit -m "refactor: extract api-jackson (databind via mix-ins); api is annotations-only"
```

---

## Task 6: Repoint `reader`, `reader-xls`, `metadata` at the slim tier

**Files:**
- Modify: `reader/pom.xml`, `reader-xls/pom.xml` (confirm no `api-jackson` needed)
- Modify: `metadata/pom.xml` (add `api-jackson` + `reference`; drop nothing else structural)

- [ ] **Step 1: Confirm reader needs no serde/heavy deps**
```bash
grep -rn 'com.fasterxml.jackson\|common.kryo\|DownloadUtil\|common.csl' reader/src/main reader-xls/src/main || echo "OK: reader/-xls use none"
```
Expected: "OK". If any hit appears, add the corresponding module dep to that pom.

- [ ] **Step 2: Ensure metadata declares its now-explicit deps**

`metadata` already declares `jackson-databind` and `citeproc-java` directly — replace those
with module deps: add `api-jackson` and `reference` to `metadata/pom.xml`, and remove
metadata's direct `citeproc-java` (now provided by `reference`). Keep its direct
`jackson-databind` only if metadata uses `ObjectMapper` without `ApiModule` (it does, 7 files) —
`api-jackson` brings databind transitively, so the direct declaration can be removed unless a
specific version pin is needed.

- [ ] **Step 3: Verify the slim dependency trees**
```bash
echo "== reader ==";   $MVN -pl reader   dependency:tree | grep -iE 'jackson-databind|httpclient|mapdb|esotericsoftware|citeproc|jbibtex' || echo "reader CLEAN"
echo "== metadata =="; $MVN -pl metadata dependency:tree | grep -iE 'mapdb|esotericsoftware' || echo "metadata has no kryo/mapdb"
echo "== api ==";      $MVN -pl api      dependency:tree | grep -iE 'jackson-databind|httpclient|mapdb|esotericsoftware|citeproc|jbibtex' || echo "api CLEAN"
echo "== coldp ==";    $MVN -pl coldp    dependency:tree | grep -iE 'jackson|httpclient|mapdb|esotericsoftware|citeproc' || echo "coldp CLEAN"
```
Expected: `reader CLEAN`, `metadata has no kryo/mapdb`, `api CLEAN`, `coldp CLEAN`.

- [ ] **Step 4: Commit**
```bash
git add -A
git commit -m "refactor: repoint reader/reader-xls/metadata at the slim api tier"
```

---

## Task 7: Full-build verification

- [ ] **Step 1: Clean build + all tests**
```bash
$MVN clean install
```
Expected: BUILD SUCCESS, all modules, all tests green.

- [ ] **Step 2: Assert the four slim guarantees one more time**
```bash
$MVN -pl reader dependency:tree   | grep -iE 'jackson-databind|httpclient|mapdb|esotericsoftware|citeproc|jbibtex' && echo "FAIL reader" || echo "reader OK"
$MVN -pl metadata dependency:tree | grep -iE 'mapdb|esotericsoftware' && echo "FAIL metadata" || echo "metadata OK"
$MVN -pl api dependency:tree      | grep -iE 'jackson-databind|httpclient|mapdb|esotericsoftware|citeproc|jbibtex' && echo "FAIL api" || echo "api OK"
```
Expected: `reader OK`, `metadata OK`, `api OK`.

- [ ] **Step 3: Final commit (if any pom tidy-ups remain)**
```bash
git add -A
git commit -m "chore: finalise slim module split" --allow-empty
```

---

## Notes on risk areas (from the design spec)

- **CitationFormatter registration**: the citeproc formatter is registered only at
  `WsServer` startup and in `reference`/webservice test bases. Any test outside those that
  asserts `getCitation()` output must register `new CslCitationFormatter()` in setup, else it
  returns null. The `fallbackWhenUnregistered` test (Task 2) documents this contract.
- **`CSLType` drift**: `CSLTypeTest` (Task 1) fails if the build's citeproc version carries a
  value not in the COL enum — add it (name + `toString()` id) when the test reports it.
- **Missed `api-jackson` edges**: surfaced by `mvn install` in Task 5 Step 7 — add the dep to
  each failing module until green.
- **`kryo`/`reference` needing databind**: if `JsonObjSerializer` (in `kryo`) or a CSL
  converter references `com.fasterxml.jackson.databind`, add an `api-jackson` dependency to that
  module's pom (both are internal-tier, so the slim guarantees are unaffected).
