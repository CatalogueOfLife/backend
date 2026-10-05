# Sector Profiles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Manage the sync settings of arbitrarily large groups of sectors in one place. Project-scoped,
rule-selected sector profiles replace the `SECTOR_*` dataset settings.

**Architecture:**
- Sync settings get one contract, `SyncSettings`, implemented by both `Sector` and the new `SectorSettings` bundle.
- A project holds `SectorProfile`s. Each has a `SectorSelector`, matched live in SQL, and a `SectorSettings`.
- `SectorSettingsResolver` (pure Java) layers built-in defaults, then the matching profiles in position order,
  then the sector itself. The nearest level wins, except blocklists, which are unioned.
- `SectorRunnable` writes the resolved values back onto the in-memory sector, so the sync handlers keep reading
  `sector.getX()`.

**Tech Stack:** Java 25, MyBatis XML mappers, Postgres 17 (TestContainers in tests), Jackson via `ApiModule`, JUnit 4,
Dropwizard/Jersey resources.

**Spec:** [`docs/2026-10-05-sector-profiles.md`](2026-10-05-sector-profiles.md). Read it first; this plan argues from it.

## Global Constraints

- Work only in the worktree `.claude/worktrees/sector-profiles` on branch `feat/sector-profiles`.
- Java 25: `export JAVA_HOME=$HOME/.sdkman/candidates/java/25.0.3-librca PATH=$JAVA_HOME/bin:$PATH` before any `mvn`.
- Code style: 2-space indent, 1TBS, max 140 columns. Match the comment density of the surrounding code.
- Never add `Co-Authored-By`, `Claude-Session` or any other Claude attribution to commits.
- Schema: edit `dao/src/main/resources/life/catalogue/db/dbschema.sql`. Log the prod DDL in the single section
  `#### 2026-10-05 sector profiles` at the top of the PROD changes list in `dbschema.md`. Task 1 creates that section
  and later tasks extend it.
- MyBatis `SELECT`/`COLS`/`PROPS` fragments are paired by position. Keep them aligned.
- Building a downstream module after changing an upstream one needs `-am` (or `mvn -pl <mods> -am install -DskipTests`
  first). Otherwise the stale jar from `~/.m2` is tested.
- Running a single test class with `-am` needs `-Dsurefire.failIfNoSpecifiedTests=false`. `*IT.java` run in failsafe:
  `mvn -pl core verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=<Class>`.
- Field names and JSON property names of the settings are fixed:
  - `ranks`, `entities`, `nameTypes`, `nameStatusExclusion`, `nameFilter`, `extinctFilter`
  - `copyAccordingTo`, `removeOrdinals`, `createImplicitNames`, `code`, `authorshipUpdate`
  - `issueExclusion`, `blockedNames`, `blockedNamePatterns`
- Nearest wins for scalars and allow-lists. Union for `nameStatusExclusion`, `issueExclusion`, `blockedNames` and
  `blockedNamePatterns`. Null, and an empty set, mean "not set at this level".

## Review Focus

1. **A dataset still holding a removed `sector …` settings key** (a dev db, or a migration run late). Settings must
   still load, ignoring the stale key with a warning, instead of failing every reader of that dataset. Pinned in
   Task 6.
2. **A profile's settings jsonb with a property this code does not know** (written by an older or newer version).
   The profile must still load and the unknown property be ignored. Pinned in Task 3.
3. **Profiles of one dataset must never apply to a sector of another**, e.g. a release's sectors and its project's
   profiles. Pinned in Task 3.
4. **Two matching profiles with the same `position`** must cascade in a stable order, by id. Pinned in Task 3.
5. **A profile with an invalid regex** in `nameFilter` or `blockedNamePatterns` must be rejected when saved. It
   must not let every sector it selects fail, or silently skip the pattern, at the next sync. Pinned in Task 4.

---

### Task 1: One settings contract, fully persisted on the sector

**Files:**
- Create: `api/src/main/java/life/catalogue/api/model/SyncSettings.java`
- Create: `api/src/main/java/life/catalogue/api/model/SectorSettings.java`
- Modify: `api/src/main/java/life/catalogue/api/model/Sector.java`
- Modify: `dao/src/main/resources/life/catalogue/db/dbschema.sql` (table `sector`), `dbschema.md`
- Modify: `dao/src/main/resources/life/catalogue/db/mapper/SectorMapper.xml`
- Modify: `core/src/main/java/life/catalogue/assembly/TreeBaseHandler.java:130,182,186,359`
- Modify: `core/src/main/java/life/catalogue/assembly/TreeMergeHandler.java:397`
- Test: `api/src/test/java/life/catalogue/api/model/SectorTest.java` (new)
- Test: `dao/src/test/java/life/catalogue/db/mapper/SectorMapperTest.java`

**Interfaces:**
- Produces: `interface SyncSettings` with a getter and a setter for each of the 14 settings listed in Global
  Constraints. Types:
  - `Set<Rank> ranks`, `Set<EntityType> entities`, `Set<NameType> nameTypes`, `Set<NomStatus> nameStatusExclusion`
  - `String nameFilter`, `Boolean extinctFilter`, `Boolean copyAccordingTo`, `Boolean removeOrdinals`,
    `Boolean createImplicitNames`
  - `NomCode code`, `Sector.AuthorshipUpdate authorshipUpdate`
  - `Set<Issue> issueExclusion`, `Set<String> blockedNames`, `Set<String> blockedNamePatterns`
- Produces: `static void SyncSettings.copy(SyncSettings from, SyncSettings to)`, which deep-copies sets.
- Produces: `class SectorSettings implements SyncSettings`, a bean with `equals`/`hashCode` and
  `static SectorSettings of(SyncSettings src)`.
- Produces: `Sector implements SyncSettings`. The three flags are now `Boolean getCopyAccordingTo()`,
  `Boolean getRemoveOrdinals()` and `Boolean getCreateImplicitNames()`, replacing the `boolean isX()` methods.
  `authorshipUpdate` defaults to null.

> Deviation from the spec, recorded in Task 8: the spec said `@JsonUnwrapped SectorSettings` inside `Sector`. Jackson
> drops every input property a parent marks `@JsonIgnore` before an unwrapped child sees it
> (`BeanDeserializer.deserializeWithUnwrapped`). Delegating getters on `Sector` would therefore break deserialization.
> A shared interface keeps the JSON flat, the MyBatis property paths unchanged and every call site compiling.

- [ ] **Step 1: Write the failing api test** `api/src/test/java/life/catalogue/api/model/SectorTest.java`

```java
package life.catalogue.api.model;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.NomStatus;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.EnumSet;
import java.util.Set;

import org.junit.Test;

import static org.junit.Assert.*;

public class SectorTest {

  static Sector full() {
    Sector s = new Sector();
    s.setDatasetKey(3);
    s.setId(17);
    s.setSubjectDatasetKey(1010);
    s.setMode(Sector.Mode.MERGE);
    s.setRanks(EnumSet.of(Rank.GENUS, Rank.SPECIES));
    s.setEntities(EnumSet.of(EntityType.NAME_USAGE, EntityType.VERNACULAR));
    s.setNameTypes(EnumSet.of(NameType.SCIENTIFIC));
    s.setNameStatusExclusion(EnumSet.of(NomStatus.CHRESONYM));
    s.setNameFilter("BOLD:.*");
    s.setExtinctFilter(false);
    s.setCopyAccordingTo(true);
    s.setRemoveOrdinals(true);
    s.setCreateImplicitNames(false);
    s.setCode(NomCode.ZOOLOGICAL);
    s.setAuthorshipUpdate(Sector.AuthorshipUpdate.MISSING);
    s.setIssueExclusion(EnumSet.of(Issue.DOUBTFUL_NAME));
    s.setBlockedNames(Set.of("Aus bus"));
    s.setBlockedNamePatterns(Set.of("^Incertae"));
    return s;
  }

  @Test
  public void copyConstructorKeepsEverySetting() {
    var s = full();
    var copy = new Sector(s);
    assertEquals(s, copy);
    assertEquals(SectorSettings.of(s), SectorSettings.of(copy));
    // a deep copy: changing the copy leaves the original alone
    copy.getRanks().add(Rank.FAMILY);
    assertFalse(s.getRanks().contains(Rank.FAMILY));
  }

  @Test
  public void equalsSeesEverySetting() {
    var a = full();
    var b = full();
    b.setCopyAccordingTo(false);
    assertNotEquals(a, b);
    b = full();
    b.setRemoveOrdinals(null);
    assertNotEquals(a, b);
    b = full();
    b.setBlockedNames(Set.of("Cus dus"));
    assertNotEquals(a, b);
    b = full();
    b.setIssueExclusion(null);
    assertNotEquals(a, b);
  }

  @Test
  public void unsetFlagsStayNull() {
    var s = new Sector();
    assertNull(s.getCopyAccordingTo());
    assertNull(s.getRemoveOrdinals());
    assertNull(s.getCreateImplicitNames());
    assertNull(s.getAuthorshipUpdate());
    var built = Sector.newBuilder().build();
    assertNull(built.getCreateImplicitNames());
    assertNull(built.getAuthorshipUpdate());
  }

  @Test
  public void jsonStaysFlat() throws Exception {
    String json = ApiModule.MAPPER.writeValueAsString(full());
    var tree = ApiModule.MAPPER.readTree(json);
    assertTrue(tree.has("copyAccordingTo"));
    assertTrue(tree.has("blockedNamePatterns"));
    assertFalse(tree.has("settings"));
    assertEquals(full(), ApiModule.MAPPER.readValue(json, Sector.class));
  }

  @Test
  public void settingsJsonIgnoresUnknownProperties() throws Exception {
    var s = ApiModule.MAPPER.readValue("{\"ranks\":[\"genus\"],\"someFutureSetting\":true}", SectorSettings.class);
    assertEquals(EnumSet.of(Rank.GENUS), s.getRanks());
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -pl api -am test -Dtest=SectorTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure, because `SectorSettings`, `getCopyAccordingTo()` and `setIssueExclusion(...)` do not
exist.

- [ ] **Step 3: Create `SyncSettings`**

```java
package life.catalogue.api.model;

import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.NomStatus;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * The settings that steer how a sector is synced into its project.
 * Implemented by {@link Sector} itself and by {@link SectorSettings}, the bundle a {@link SectorProfile} carries.
 * Null - and for sets an empty one - means "not set at this level", so whatever a lower level says shows through.
 * SectorSettingsResolver in the dao module layers built-in defaults, the matching profiles and the sector.
 */
public interface SyncSettings {

  Set<Rank> getRanks();
  void setRanks(Set<Rank> ranks);

  Set<EntityType> getEntities();
  void setEntities(Set<EntityType> entities);

  Set<NameType> getNameTypes();
  void setNameTypes(Set<NameType> nameTypes);

  /** A blocklist: unioned over all levels. */
  Set<NomStatus> getNameStatusExclusion();
  void setNameStatusExclusion(Set<NomStatus> nameStatusExclusion);

  /** An optional regex. If given, only usages whose scientific name fully matches are synced. */
  String getNameFilter();
  void setNameFilter(String nameFilter);

  /** True only syncs extinct, false only extant taxa. */
  Boolean getExtinctFilter();
  void setExtinctFilter(Boolean extinctFilter);

  Boolean getCopyAccordingTo();
  void setCopyAccordingTo(Boolean copyAccordingTo);

  Boolean getRemoveOrdinals();
  void setRemoveOrdinals(Boolean removeOrdinals);

  Boolean getCreateImplicitNames();
  void setCreateImplicitNames(Boolean createImplicitNames);

  /** The nomenclatural code forced onto every synced name. */
  NomCode getCode();
  void setCode(NomCode code);

  /** HIERARCHY sectors only. */
  Sector.AuthorshipUpdate getAuthorshipUpdate();
  void setAuthorshipUpdate(Sector.AuthorshipUpdate authorshipUpdate);

  /** MERGE sectors only. A blocklist: unioned over all levels and with the XRelease config. */
  Set<Issue> getIssueExclusion();
  void setIssueExclusion(Set<Issue> issueExclusion);

  /** MERGE sectors only. A blocklist: unioned over all levels and with the XRelease config. */
  Set<String> getBlockedNames();
  void setBlockedNames(Set<String> blockedNames);

  /** MERGE sectors only. A blocklist: unioned over all levels and with the XRelease config. */
  Set<String> getBlockedNamePatterns();
  void setBlockedNamePatterns(Set<String> blockedNamePatterns);

  /**
   * Copies every setting from one holder onto another. Sets are copied, never shared.
   */
  static void copy(SyncSettings from, SyncSettings to) {
    to.setRanks(copy(from.getRanks(), Rank.class));
    to.setEntities(copy(from.getEntities(), EntityType.class));
    to.setNameTypes(copy(from.getNameTypes(), NameType.class));
    to.setNameStatusExclusion(copy(from.getNameStatusExclusion(), NomStatus.class));
    to.setNameFilter(from.getNameFilter());
    to.setExtinctFilter(from.getExtinctFilter());
    to.setCopyAccordingTo(from.getCopyAccordingTo());
    to.setRemoveOrdinals(from.getRemoveOrdinals());
    to.setCreateImplicitNames(from.getCreateImplicitNames());
    to.setCode(from.getCode());
    to.setAuthorshipUpdate(from.getAuthorshipUpdate());
    to.setIssueExclusion(copy(from.getIssueExclusion(), Issue.class));
    to.setBlockedNames(from.getBlockedNames() == null ? null : new HashSet<>(from.getBlockedNames()));
    to.setBlockedNamePatterns(from.getBlockedNamePatterns() == null ? null : new HashSet<>(from.getBlockedNamePatterns()));
  }

  // EnumSet.copyOf fails on an empty collection that is not an EnumSet itself
  private static <E extends Enum<E>> Set<E> copy(Set<E> set, Class<E> clazz) {
    if (set == null) return null;
    var copy = EnumSet.noneOf(clazz);
    copy.addAll(set);
    return copy;
  }
}
```

- [ ] **Step 4: Create `SectorSettings`**

The class has a private field for each of the 14 settings, with the types listed in Interfaces. Give every field a
plain getter and setter implementing `SyncSettings` (`@Override`). Then add:

```java
  public SectorSettings() {
  }

  public static SectorSettings of(SyncSettings src) {
    var s = new SectorSettings();
    SyncSettings.copy(src, s);
    return s;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SectorSettings)) return false;
    SectorSettings that = (SectorSettings) o;
    return Objects.equals(ranks, that.ranks)
           && Objects.equals(entities, that.entities)
           && Objects.equals(nameTypes, that.nameTypes)
           && Objects.equals(nameStatusExclusion, that.nameStatusExclusion)
           && Objects.equals(nameFilter, that.nameFilter)
           && Objects.equals(extinctFilter, that.extinctFilter)
           && Objects.equals(copyAccordingTo, that.copyAccordingTo)
           && Objects.equals(removeOrdinals, that.removeOrdinals)
           && Objects.equals(createImplicitNames, that.createImplicitNames)
           && code == that.code
           && authorshipUpdate == that.authorshipUpdate
           && Objects.equals(issueExclusion, that.issueExclusion)
           && Objects.equals(blockedNames, that.blockedNames)
           && Objects.equals(blockedNamePatterns, that.blockedNamePatterns);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ranks, entities, nameTypes, nameStatusExclusion, nameFilter, extinctFilter, copyAccordingTo,
      removeOrdinals, createImplicitNames, code, authorshipUpdate, issueExclusion, blockedNames, blockedNamePatterns);
  }
```

Class javadoc: "A bundle of sector sync settings as held by a {@link SectorProfile}. Every value is optional, see
{@link SyncSettings}." Unknown JSON properties are already ignored globally (`ApiModule` disables
`FAIL_ON_UNKNOWN_PROPERTIES`). The last test of Step 1 pins that.

- [ ] **Step 5: Make `Sector` implement `SyncSettings`**

In `Sector.java`:
- **Class declaration**: `public class Sector extends DatasetScopedEntity<Integer> implements SyncSettings`.
- **Fields**:
  - Replace `private boolean copyAccordingTo = false;`, `private boolean removeOrdinals = false;` and
    `private boolean createImplicitNames = true;` with `private Boolean copyAccordingTo;`,
    `private Boolean removeOrdinals;` and `private Boolean createImplicitNames;`.
  - Replace `private AuthorshipUpdate authorshipUpdate = AuthorshipUpdate.NONE;` with
    `private AuthorshipUpdate authorshipUpdate;`.
  - Add `private Set<Issue> issueExclusion;`, `private Set<String> blockedNames;` and
    `private Set<String> blockedNamePatterns;`, each with a getter and setter. Import `life.catalogue.api.vocab.Issue`.
- **Flag accessors**: replace `isCopyAccordingTo()`/`setCopyAccordingTo(boolean)` with
  `public Boolean getCopyAccordingTo()`/`public void setCopyAccordingTo(Boolean)`. Do the same for `removeOrdinals`
  and `createImplicitNames`. Add `@Override` to every getter and setter that `SyncSettings` declares.
- **Copy constructor**: remove the settings lines. These are the assignments of `code`, `ranks`, `entities`,
  `nameTypes`, `nameStatusExclusion`, `nameFilter`, `extinctFilter`, `createImplicitNames` and `authorshipUpdate`.
  End the constructor with `SyncSettings.copy(other, this);`. That also fixes the missing `copyAccordingTo` and
  `removeOrdinals`.
- **`equals`**: replace the body after the cast with:

```java
    return Objects.equals(target, sector.target)
           && Objects.equals(subjectDatasetKey, sector.subjectDatasetKey)
           && Objects.equals(subject, sector.subject)
           && Objects.equals(originalSubjectId, sector.originalSubjectId)
           && placeholderRank == sector.placeholderRank
           && mode == sector.mode
           && useXRelease == sector.useXRelease
           && Objects.equals(priority, sector.priority)
           && Objects.equals(syncAttempt, sector.syncAttempt)
           && Objects.equals(datasetAttempt, sector.datasetAttempt)
           && code == sector.code
           && Objects.equals(copyAccordingTo, sector.copyAccordingTo)
           && Objects.equals(removeOrdinals, sector.removeOrdinals)
           && Objects.equals(createImplicitNames, sector.createImplicitNames)
           && authorshipUpdate == sector.authorshipUpdate
           && Objects.equals(ranks, sector.ranks)
           && Objects.equals(entities, sector.entities)
           && Objects.equals(nameTypes, sector.nameTypes)
           && Objects.equals(nameStatusExclusion, sector.nameStatusExclusion)
           && Objects.equals(nameFilter, sector.nameFilter)
           && Objects.equals(extinctFilter, sector.extinctFilter)
           && Objects.equals(issueExclusion, sector.issueExclusion)
           && Objects.equals(blockedNames, sector.blockedNames)
           && Objects.equals(blockedNamePatterns, sector.blockedNamePatterns)
           && Objects.equals(note, sector.note);
```

- **`hashCode`**:

```java
    return Objects.hash(super.hashCode(), target, subjectDatasetKey, subject, originalSubjectId, placeholderRank, mode,
      useXRelease, priority, syncAttempt, datasetAttempt, code, copyAccordingTo, removeOrdinals, createImplicitNames,
      authorshipUpdate, ranks, entities, nameTypes, nameStatusExclusion, nameFilter, extinctFilter, issueExclusion,
      blockedNames, blockedNamePatterns, note);
```

- **`Builder`**:
  - Change the fields to `private Boolean copyAccordingTo;`, `private Boolean removeOrdinals;`,
    `private Boolean createImplicitNames;` and `private AuthorshipUpdate authorshipUpdate;`. The old `false` default
    for `createImplicitNames` contradicted the field default of `true`, and the old `NONE` default goes too.
  - Change the matching builder methods to take `Boolean`.

- [ ] **Step 6: Make the sync handlers null safe**

Until Task 6 these flags can be null at sync time (no project setting, no sector value). The defaults below are
exactly the old field defaults.
- `TreeBaseHandler.java:130` and `:359`: `sector.isCreateImplicitNames()` → `!Boolean.FALSE.equals(sector.getCreateImplicitNames())`
- `TreeBaseHandler.java:182`: `!sector.isCopyAccordingTo()` → `!Boolean.TRUE.equals(sector.getCopyAccordingTo())`
- `TreeBaseHandler.java:186`: `sector.isRemoveOrdinals()` → `Boolean.TRUE.equals(sector.getRemoveOrdinals())`
- `TreeMergeHandler.java:397`: `sector.isCreateImplicitNames()` → `!Boolean.FALSE.equals(sector.getCreateImplicitNames())`
- `HierarchySync.enrichAuthorship` already treats a null `authorshipUpdate` as `NONE`. Leave it.

- [ ] **Step 7: Run the api test to verify it passes**

Run: `mvn -pl api -am test -Dtest=SectorTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, 5 tests.

- [ ] **Step 8: Persist the settings. Write the failing mapper test.**

In `SectorMapperTest.create(DSID, DSID)`, after `d.setNameFilter("BOLD:.*");`, add:

```java
    d.setCopyAccordingTo(true);
    d.setRemoveOrdinals(false);
    d.setCreateImplicitNames(false);
    d.setAuthorshipUpdate(Sector.AuthorshipUpdate.MISSING);
    d.setIssueExclusion(EnumSet.of(Issue.DOUBTFUL_NAME, Issue.UNPARSABLE_NAME));
    d.setBlockedNames(Set.of("Aus bus", "Cus"));
    d.setBlockedNamePatterns(Set.of("^Incertae"));
```

Then add this test. Import `java.util.EnumSet`; `Issue` comes with `life.catalogue.api.vocab.*`.

```java
  /**
   * A setting the sector does not set must come back unset, so it can be inherited from a profile.
   */
  @Test
  public void unsetSettingsRoundTripAsNull() {
    Sector s = create();
    s.setCopyAccordingTo(null);
    s.setRemoveOrdinals(null);
    s.setCreateImplicitNames(null);
    s.setAuthorshipUpdate(null);
    mapper().create(s);
    commit();

    Sector s2 = mapper().get(s.getKey());
    assertNull(s2.getCopyAccordingTo());
    assertNull(s2.getRemoveOrdinals());
    assertNull(s2.getCreateImplicitNames());
    assertNull(s2.getAuthorshipUpdate());
  }
```

- [ ] **Step 9: Run it to verify it fails**

Run: `mvn -pl dao -am test -Dtest=SectorMapperTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL.
- `roundtrip`/`update` fail on `copyAccordingTo`, `issueExclusion` etc., because they are not persisted.
- `unsetSettingsRoundTripAsNull` fails with a NOT NULL violation on `authorship_update`.

- [ ] **Step 10: Update the DDL**

In `dbschema.sql`, table `sector`:
- Change `authorship_update SECTOR_AUTHORSHIP_UPDATE NOT NULL DEFAULT 'NONE',` to `authorship_update SECTOR_AUTHORSHIP_UPDATE,`.
- Insert directly before `note TEXT,`:

```sql
  copy_according_to BOOLEAN,
  remove_ordinals BOOLEAN,
  create_implicit_names BOOLEAN,
  issue_exclusion ISSUE[],
  blocked_names TEXT[],
  blocked_name_patterns TEXT[],
```

At the top of the PROD changes list in `dbschema.md`, insert the new section:

````markdown
#### 2026-10-05 sector profiles
Sector settings for large groups of sectors, see `docs/2026-10-05-sector-profiles.md`. Run the whole section right
before the deploy, with no sync or release running in between: the new app reads the settings from profiles only.

```sql
ALTER TABLE sector
  ALTER COLUMN authorship_update DROP NOT NULL,
  ALTER COLUMN authorship_update DROP DEFAULT,
  ADD COLUMN copy_according_to BOOLEAN,
  ADD COLUMN remove_ordinals BOOLEAN,
  ADD COLUMN create_implicit_names BOOLEAN,
  ADD COLUMN issue_exclusion ISSUE[],
  ADD COLUMN blocked_names TEXT[],
  ADD COLUMN blocked_name_patterns TEXT[];

-- NONE was only ever the column default. NULL inherits it, and lets a profile set something else.
-- Releases are immutable copies and keep theirs.
UPDATE sector s SET authorship_update = NULL
FROM dataset d
WHERE d.key = s.dataset_key AND d.origin = 'PROJECT' AND s.authorship_update = 'NONE';
```
The three flag columns start NULL. That loses nothing: their sector values were never stored.
````

- [ ] **Step 11: Update `SectorMapper.xml`**

- **`SELECT_NO_BROKEN`**, after `s.name_filter,`:
  `s.copy_according_to, s.remove_ordinals, s.create_implicit_names, s.issue_exclusion, s.blocked_names, s.blocked_name_patterns,`
- **`COLS_NO_SUBJECT`**, after `name_filter,`:
  `copy_according_to, remove_ordinals, create_implicit_names, issue_exclusion, blocked_names, blocked_name_patterns,`
- **`PROPS`**, after `#{nameFilter},`:

```xml
    #{copyAccordingTo},
    #{removeOrdinals},
    #{createImplicitNames},
    #{issueExclusion, typeHandler=life.catalogue.db.type.IssueSetTypeHandler},
    #{blockedNames, typeHandler=life.catalogue.db.type2.StringSetTypeHandler},
    #{blockedNamePatterns, typeHandler=life.catalogue.db.type2.StringSetTypeHandler},
```

- **`sectorResultMap`**, after the `nameStatusExclusion` result:

```xml
    <result property="issueExclusion" column="issue_exclusion" typeHandler="life.catalogue.db.type.IssueSetTypeHandler"/>
    <result property="blockedNames" column="blocked_names" typeHandler="life.catalogue.db.type2.StringSetTypeHandler"/>
    <result property="blockedNamePatterns" column="blocked_name_patterns" typeHandler="life.catalogue.db.type2.StringSetTypeHandler"/>
```

`copyDataset` uses `COLS_NO_SUBJECT` on both sides, so releases copy the new columns without further change.

- [ ] **Step 12: Run the mapper test to verify it passes**

Run: `mvn -pl dao -am test -Dtest=SectorMapperTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 13: Build everything that depends on `Sector`, and run the sync unit tests**

Run: `mvn -pl api,dao,core,webservice -am install -DskipTests && mvn -pl core test -Dtest='TreeBaseHandler*Test,SectorSyncTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: BUILD SUCCESS and the tests pass.

A `cannot find symbol isCopyAccordingTo/isRemoveOrdinals/isCreateImplicitNames` anywhere else (a test, kryo,
webservice) is fixed the same way as Step 6. Test fixtures setting the flags with `boolean` literals compile
unchanged through autoboxing.

A test that compares a hand-built `Sector` with one loaded from the db can now fail on `null` vs empty set for the
new set fields. The type handlers read NULL as an empty set, exactly as they already do for `ranks`. Set the field
to an empty set in that fixture.

- [ ] **Step 14: Commit**

```bash
git add api/src/main/java/life/catalogue/api/model/SyncSettings.java api/src/main/java/life/catalogue/api/model/SectorSettings.java \
  api/src/main/java/life/catalogue/api/model/Sector.java api/src/test/java/life/catalogue/api/model/SectorTest.java \
  dao/src/main/resources/life/catalogue/db/dbschema.sql dao/src/main/resources/life/catalogue/db/dbschema.md \
  dao/src/main/resources/life/catalogue/db/mapper/SectorMapper.xml dao/src/test/java/life/catalogue/db/mapper/SectorMapperTest.java \
  core/src/main/java/life/catalogue/assembly/TreeBaseHandler.java core/src/main/java/life/catalogue/assembly/TreeMergeHandler.java
git commit -m "Sector settings get one contract and are all persisted

copyAccordingTo, removeOrdinals and createImplicitNames had no column, so a sector value was silently dropped.
Sector and the new SectorSettings bundle share the SyncSettings interface; unset flags stay null so they can be
inherited."
```

---

### Task 2: Profile model and the settings resolver

**Files:**
- Create: `api/src/main/java/life/catalogue/api/model/SectorSelector.java`
- Create: `api/src/main/java/life/catalogue/api/model/SectorProfile.java`
- Create: `api/src/main/java/life/catalogue/api/model/EffectiveSectorSettings.java`
- Create: `dao/src/main/java/life/catalogue/dao/SectorSettingsResolver.java`
- Test: `dao/src/test/java/life/catalogue/dao/SectorSettingsResolverTest.java` (plain unit test, no db)

**Interfaces:**
- Consumes: `SyncSettings`, `SyncSettings.copy`, `SectorSettings` (Task 1).
- Produces: `SectorSelector`, a bean that never returns null sets:
  - `Set<Sector.Mode> modes`, `Set<DatasetType> datasetTypes`, `Set<UUID> publisherKeys`;
  - `boolean anySectorPublisher` (getter `isAnySectorPublisher()`);
  - `Set<Integer> subjectDatasetKeys`, `Set<Integer> sectorKeys`;
  - `equals`/`hashCode`.
- Produces: `SectorProfile extends DatasetScopedEntity<Integer>` with `String title` (`@NotBlank`),
  `String description`, `int position`, `SectorSelector selector` and `SectorSettings settings`. Both of the last two
  are never null, `@NotNull @Valid`.
- Produces: `EffectiveSectorSettings(SectorSettings settings, Map<String, String> sources)` with getters
  `getSettings()` and `getSources()`.
- Produces: `SectorSettingsResolver`:
  - `static EffectiveSectorSettings resolve(Sector sector, List<SectorProfile> profiles)`
  - `static SectorSettings defaults(Sector.Mode mode)`
  - `static Set<String> fieldNames()`
  - constants `DEFAULT = "default"`, `SECTOR = "sector"`, `MERGE_RANKS_DEFAULT`
  - `static String source(SectorProfile p)`, which returns `"profile:" + p.getId()`

- [ ] **Step 1: Write the failing resolver test** `dao/src/test/java/life/catalogue/dao/SectorSettingsResolverTest.java`

```java
package life.catalogue.dao;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.Issue;

import org.gbif.nameparser.api.Rank;

import java.beans.Introspector;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.junit.Test;

import static org.junit.Assert.*;

public class SectorSettingsResolverTest {

  static Sector sector(Sector.Mode mode) {
    var s = new Sector();
    s.setDatasetKey(3);
    s.setId(1);
    s.setMode(mode);
    return s;
  }

  static SectorProfile profile(int id, Consumer<SectorSettings> settings) {
    var p = new SectorProfile();
    p.setDatasetKey(3);
    p.setId(id);
    p.setTitle("p" + id);
    p.setPosition(id);
    settings.accept(p.getSettings());
    return p;
  }

  @Test
  public void builtInDefaultsPerMode() {
    var merge = SectorSettingsResolver.resolve(sector(Sector.Mode.MERGE), List.of());
    assertEquals(SectorSettingsResolver.MERGE_RANKS_DEFAULT, merge.getSettings().getRanks());
    assertEquals(EnumSet.allOf(EntityType.class), merge.getSettings().getEntities());
    assertEquals(Boolean.TRUE, merge.getSettings().getCreateImplicitNames());
    assertEquals(Boolean.FALSE, merge.getSettings().getCopyAccordingTo());
    assertEquals(Boolean.FALSE, merge.getSettings().getRemoveOrdinals());
    assertEquals(Sector.AuthorshipUpdate.NONE, merge.getSettings().getAuthorshipUpdate());
    assertNull(merge.getSettings().getNameFilter());
    assertNull(merge.getSettings().getCode());
    assertTrue(merge.getSettings().getIssueExclusion().isEmpty());
    assertEquals(SectorSettingsResolver.DEFAULT, merge.getSources().get("ranks"));
    assertEquals(SectorSettingsResolver.DEFAULT, merge.getSources().get("nameFilter"));

    var attach = SectorSettingsResolver.resolve(sector(Sector.Mode.ATTACH), List.of());
    assertEquals(EnumSet.allOf(Rank.class), attach.getSettings().getRanks());
  }

  @Test
  public void nearestLevelWins() {
    var p1 = profile(1, s -> s.setRanks(EnumSet.of(Rank.GENUS)));
    var p2 = profile(2, s -> s.setRanks(EnumSet.of(Rank.SPECIES)));
    var sec = sector(Sector.Mode.MERGE);

    var eff = SectorSettingsResolver.resolve(sec, List.of(p1, p2));
    assertEquals(EnumSet.of(Rank.SPECIES), eff.getSettings().getRanks());
    assertEquals("profile:2", eff.getSources().get("ranks"));

    sec.setRanks(EnumSet.of(Rank.FAMILY));
    eff = SectorSettingsResolver.resolve(sec, List.of(p1, p2));
    assertEquals(EnumSet.of(Rank.FAMILY), eff.getSettings().getRanks());
    assertEquals(SectorSettingsResolver.SECTOR, eff.getSources().get("ranks"));
  }

  @Test
  public void emptySetInherits() {
    var p = profile(1, s -> s.setEntities(EnumSet.of(EntityType.VERNACULAR)));
    var sec = sector(Sector.Mode.MERGE);
    sec.setEntities(EnumSet.noneOf(EntityType.class));
    var eff = SectorSettingsResolver.resolve(sec, List.of(p));
    assertEquals(EnumSet.of(EntityType.VERNACULAR), eff.getSettings().getEntities());
  }

  @Test
  public void explicitFalseOverrides() {
    var p = profile(1, s -> s.setCreateImplicitNames(true));
    var sec = sector(Sector.Mode.ATTACH);
    sec.setCreateImplicitNames(false);
    assertEquals(Boolean.FALSE, SectorSettingsResolver.resolve(sec, List.of(p)).getSettings().getCreateImplicitNames());
  }

  @Test
  public void cascadeFieldByField() {
    var p1 = profile(1, s -> {
      s.setRanks(EnumSet.of(Rank.GENUS));
      s.setCopyAccordingTo(true);
    });
    var p2 = profile(2, s -> s.setCopyAccordingTo(false));
    var eff = SectorSettingsResolver.resolve(sector(Sector.Mode.MERGE), List.of(p1, p2));
    assertEquals(EnumSet.of(Rank.GENUS), eff.getSettings().getRanks());
    assertEquals("profile:1", eff.getSources().get("ranks"));
    assertEquals(Boolean.FALSE, eff.getSettings().getCopyAccordingTo());
    assertEquals("profile:2", eff.getSources().get("copyAccordingTo"));
  }

  @Test
  public void blocklistsAddUp() {
    var p1 = profile(1, s -> {
      s.setIssueExclusion(EnumSet.of(Issue.DOUBTFUL_NAME));
      s.setBlockedNames(Set.of("Aus"));
    });
    var p2 = profile(2, s -> s.setBlockedNames(Set.of("Bus")));
    var sec = sector(Sector.Mode.MERGE);
    sec.setIssueExclusion(EnumSet.of(Issue.UNPARSABLE_NAME));

    var eff = SectorSettingsResolver.resolve(sec, List.of(p1, p2));
    assertEquals(Set.of(Issue.DOUBTFUL_NAME, Issue.UNPARSABLE_NAME), eff.getSettings().getIssueExclusion());
    assertEquals("profile:1,sector", eff.getSources().get("issueExclusion"));
    assertEquals(Set.of("Aus", "Bus"), eff.getSettings().getBlockedNames());
    assertEquals("profile:1,profile:2", eff.getSources().get("blockedNames"));
  }

  @Test
  public void resultDoesNotShareSetsWithProfiles() {
    var p = profile(1, s -> s.setRanks(EnumSet.of(Rank.GENUS)));
    var eff = SectorSettingsResolver.resolve(sector(Sector.Mode.MERGE), List.of(p));
    eff.getSettings().getRanks().add(Rank.SPECIES);
    assertEquals(EnumSet.of(Rank.GENUS), p.getSettings().getRanks());
  }

  /**
   * A setting added to SyncSettings but forgotten in the resolver would silently never be inherited.
   */
  @Test
  public void resolverCoversEverySetting() {
    Set<String> props = Arrays.stream(SyncSettings.class.getMethods())
      .filter(m -> m.getName().startsWith("get") && m.getParameterCount() == 0)
      .map(m -> Introspector.decapitalize(m.getName().substring(3)))
      .collect(Collectors.toSet());
    assertEquals(props, SectorSettingsResolver.fieldNames());
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -pl dao -am test -Dtest=SectorSettingsResolverTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure. `SectorProfile` and `SectorSettingsResolver` do not exist.

- [ ] **Step 3: Create `SectorSelector`**

```java
package life.catalogue.api.model;

import life.catalogue.api.vocab.DatasetType;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Which sectors of a project a {@link SectorProfile} applies to.
 * Fields are ANDed, the values within one field ORed. An empty field places no restriction,
 * so an empty selector matches every sector of the project.
 * Publisher and type are those of the subject dataset as it is now, not as it was when the sector was created.
 */
public class SectorSelector {
  private Set<Sector.Mode> modes = EnumSet.noneOf(Sector.Mode.class);
  private Set<DatasetType> datasetTypes = EnumSet.noneOf(DatasetType.class);
  private Set<UUID> publisherKeys = new HashSet<>();
  // matches the subject datasets published by any of the projects sector publishers, so it follows new ones
  private boolean anySectorPublisher;
  private Set<Integer> subjectDatasetKeys = new HashSet<>();
  private Set<Integer> sectorKeys = new HashSet<>();

  public Set<Sector.Mode> getModes() {
    return modes;
  }

  public void setModes(Set<Sector.Mode> modes) {
    this.modes = modes == null ? EnumSet.noneOf(Sector.Mode.class) : modes;
  }

  public Set<DatasetType> getDatasetTypes() {
    return datasetTypes;
  }

  public void setDatasetTypes(Set<DatasetType> datasetTypes) {
    this.datasetTypes = datasetTypes == null ? EnumSet.noneOf(DatasetType.class) : datasetTypes;
  }

  public Set<UUID> getPublisherKeys() {
    return publisherKeys;
  }

  public void setPublisherKeys(Set<UUID> publisherKeys) {
    this.publisherKeys = publisherKeys == null ? new HashSet<>() : publisherKeys;
  }

  public boolean isAnySectorPublisher() {
    return anySectorPublisher;
  }

  public void setAnySectorPublisher(boolean anySectorPublisher) {
    this.anySectorPublisher = anySectorPublisher;
  }

  public Set<Integer> getSubjectDatasetKeys() {
    return subjectDatasetKeys;
  }

  public void setSubjectDatasetKeys(Set<Integer> subjectDatasetKeys) {
    this.subjectDatasetKeys = subjectDatasetKeys == null ? new HashSet<>() : subjectDatasetKeys;
  }

  public Set<Integer> getSectorKeys() {
    return sectorKeys;
  }

  public void setSectorKeys(Set<Integer> sectorKeys) {
    this.sectorKeys = sectorKeys == null ? new HashSet<>() : sectorKeys;
  }

  /**
   * @return true if the selector matches every sector of the project
   */
  @JsonIgnore
  public boolean isEmpty() {
    return modes.isEmpty() && datasetTypes.isEmpty() && publisherKeys.isEmpty() && !anySectorPublisher
           && subjectDatasetKeys.isEmpty() && sectorKeys.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SectorSelector)) return false;
    SectorSelector that = (SectorSelector) o;
    return anySectorPublisher == that.anySectorPublisher
           && Objects.equals(modes, that.modes)
           && Objects.equals(datasetTypes, that.datasetTypes)
           && Objects.equals(publisherKeys, that.publisherKeys)
           && Objects.equals(subjectDatasetKeys, that.subjectDatasetKeys)
           && Objects.equals(sectorKeys, that.sectorKeys);
  }

  @Override
  public int hashCode() {
    return Objects.hash(modes, datasetTypes, publisherKeys, anySectorPublisher, subjectDatasetKeys, sectorKeys);
  }
}
```

- [ ] **Step 4: Create `SectorProfile`**

Fields and annotations:

```java
/**
 * Sync settings shared by every sector of a project its selector matches, so a setting for all Plazi sectors
 * lives in one place. Matching profiles cascade in ascending position, ties broken by id; the sector itself has
 * the last word. See docs/2026-10-05-sector-profiles.md.
 */
public class SectorProfile extends DatasetScopedEntity<Integer> {
  @NotBlank
  private String title;
  private String description;
  private int position;
  @NotNull
  @Valid
  private SectorSelector selector = new SectorSelector();
  @NotNull
  @Valid
  private SectorSettings settings = new SectorSettings();
```

Add plain getters and setters. `setSelector(null)` and `setSettings(null)` store a fresh empty instance, as
`SectorSelector`'s setters do. `equals`/`hashCode` call `super` and cover `title`, `description`, `position`,
`selector` and `settings`. Imports: `jakarta.validation.Valid`, `jakarta.validation.constraints.NotBlank`,
`jakarta.validation.constraints.NotNull`, `java.util.Objects`.

- [ ] **Step 5: Create `EffectiveSectorSettings`**

```java
package life.catalogue.api.model;

import java.util.Map;

/**
 * The settings a sync of one sector uses, and for each setting the level it came from:
 * "default", "sector" or "profile:{id}"; a comma separated list of these for blocklists, which add up.
 */
public class EffectiveSectorSettings {
  private final SectorSettings settings;
  private final Map<String, String> sources;

  public EffectiveSectorSettings(SectorSettings settings, Map<String, String> sources) {
    this.settings = settings;
    this.sources = sources;
  }

  public SectorSettings getSettings() {
    return settings;
  }

  public Map<String, String> getSources() {
    return sources;
  }
}
```

- [ ] **Step 6: Create `SectorSettingsResolver`**

```java
package life.catalogue.dao;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.NomStatus;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Layers the settings of a sector sync: built-in defaults per mode, then every matching profile in the order given,
 * then the sector itself. For scalars and allow-lists the highest level that sets a value wins, null and empty
 * sets meaning "not set". Blocklists are unioned over all levels, so no level can lift a block set below it.
 */
public final class SectorSettingsResolver {
  public static final String DEFAULT = "default";
  public static final String SECTOR = "sector";
  // in merge mode we dont want any higher ranks than family by default
  public static final Set<Rank> MERGE_RANKS_DEFAULT = Collections.unmodifiableSet(EnumSet.of(
    Rank.FAMILY, Rank.GENUS, Rank.SPECIES, Rank.SUBSPECIES, Rank.VARIETY, Rank.FORM
  ));

  private record Level(String source, SyncSettings settings) {}

  private interface Field {
    String name();
    void resolve(List<Level> levels, SyncSettings into, Map<String, String> sources);
  }

  private record Nearest<T>(String name, Function<SyncSettings, T> getter, BiConsumer<SyncSettings, T> setter) implements Field {
    @Override
    public void resolve(List<Level> levels, SyncSettings into, Map<String, String> sources) {
      for (int i = levels.size() - 1; i >= 0; i--) {
        T val = getter.apply(levels.get(i).settings());
        if (isSet(val)) {
          setter.accept(into, val);
          sources.put(name, levels.get(i).source());
          return;
        }
      }
      // no level sets it, e.g. no name filter at all
      sources.put(name, DEFAULT);
    }
  }

  private record Union<E>(String name, Function<SyncSettings, Set<E>> getter, BiConsumer<SyncSettings, Set<E>> setter) implements Field {
    @Override
    public void resolve(List<Level> levels, SyncSettings into, Map<String, String> sources) {
      Set<E> all = new HashSet<>();
      List<String> from = new ArrayList<>();
      for (Level lvl : levels) {
        Set<E> val = getter.apply(lvl.settings());
        if (isSet(val)) {
          all.addAll(val);
          from.add(lvl.source());
        }
      }
      setter.accept(into, all);
      sources.put(name, from.isEmpty() ? DEFAULT : String.join(",", from));
    }
  }

  private static final List<Field> FIELDS = List.<Field>of(
    new Nearest<>("ranks", SyncSettings::getRanks, SyncSettings::setRanks),
    new Nearest<>("entities", SyncSettings::getEntities, SyncSettings::setEntities),
    new Nearest<>("nameTypes", SyncSettings::getNameTypes, SyncSettings::setNameTypes),
    new Nearest<>("nameFilter", SyncSettings::getNameFilter, SyncSettings::setNameFilter),
    new Nearest<>("extinctFilter", SyncSettings::getExtinctFilter, SyncSettings::setExtinctFilter),
    new Nearest<>("copyAccordingTo", SyncSettings::getCopyAccordingTo, SyncSettings::setCopyAccordingTo),
    new Nearest<>("removeOrdinals", SyncSettings::getRemoveOrdinals, SyncSettings::setRemoveOrdinals),
    new Nearest<>("createImplicitNames", SyncSettings::getCreateImplicitNames, SyncSettings::setCreateImplicitNames),
    new Nearest<>("code", SyncSettings::getCode, SyncSettings::setCode),
    new Nearest<>("authorshipUpdate", SyncSettings::getAuthorshipUpdate, SyncSettings::setAuthorshipUpdate),
    new Union<>("nameStatusExclusion", SyncSettings::getNameStatusExclusion, SyncSettings::setNameStatusExclusion),
    new Union<>("issueExclusion", SyncSettings::getIssueExclusion, SyncSettings::setIssueExclusion),
    new Union<>("blockedNames", SyncSettings::getBlockedNames, SyncSettings::setBlockedNames),
    new Union<>("blockedNamePatterns", SyncSettings::getBlockedNamePatterns, SyncSettings::setBlockedNamePatterns)
  );

  private SectorSettingsResolver() {
  }

  public static String source(SectorProfile p) {
    return "profile:" + p.getId();
  }

  /**
   * @return the names of all settings the resolver layers, i.e. all SyncSettings properties
   */
  public static Set<String> fieldNames() {
    return FIELDS.stream().map(Field::name).collect(Collectors.toSet());
  }

  /**
   * The built-in defaults, the lowest level of every resolution.
   */
  public static SectorSettings defaults(Sector.Mode mode) {
    var d = new SectorSettings();
    d.setRanks(mode == Sector.Mode.MERGE ? EnumSet.copyOf(MERGE_RANKS_DEFAULT) : EnumSet.allOf(Rank.class));
    d.setEntities(EnumSet.allOf(EntityType.class));
    d.setNameTypes(EnumSet.noneOf(NameType.class));
    d.setNameStatusExclusion(EnumSet.noneOf(NomStatus.class));
    d.setCopyAccordingTo(false);
    d.setRemoveOrdinals(false);
    d.setCreateImplicitNames(true);
    d.setAuthorshipUpdate(Sector.AuthorshipUpdate.NONE);
    d.setIssueExclusion(EnumSet.noneOf(Issue.class));
    d.setBlockedNames(new HashSet<>());
    d.setBlockedNamePatterns(new HashSet<>());
    return d;
  }

  /**
   * @param profiles the profiles matching the sector, in ascending position. A later one overrides an earlier one.
   */
  public static EffectiveSectorSettings resolve(Sector sector, List<SectorProfile> profiles) {
    List<Level> levels = new ArrayList<>();
    levels.add(new Level(DEFAULT, defaults(sector.getMode())));
    for (SectorProfile p : profiles) {
      levels.add(new Level(source(p), p.getSettings()));
    }
    levels.add(new Level(SECTOR, sector));
    var resolved = new SectorSettings();
    Map<String, String> sources = new LinkedHashMap<>();
    for (Field f : FIELDS) {
      f.resolve(levels, resolved, sources);
    }
    // never hand out a set owned by a profile, the sector or the defaults
    return new EffectiveSectorSettings(SectorSettings.of(resolved), sources);
  }

  static boolean isSet(Object val) {
    if (val == null) return false;
    if (val instanceof Collection<?> c) return !c.isEmpty();
    if (val instanceof String s) return !s.isBlank();
    return true;
  }
}
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `mvn -pl dao -am test -Dtest=SectorSettingsResolverTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, 8 tests.

- [ ] **Step 8: Commit**

```bash
git add api/src/main/java/life/catalogue/api/model/SectorSelector.java api/src/main/java/life/catalogue/api/model/SectorProfile.java \
  api/src/main/java/life/catalogue/api/model/EffectiveSectorSettings.java \
  dao/src/main/java/life/catalogue/dao/SectorSettingsResolver.java dao/src/test/java/life/catalogue/dao/SectorSettingsResolverTest.java
git commit -m "Sector profiles and the resolver that layers defaults, profiles and sector"
```

---

### Task 3: The `sector_profile` table, its mapper, and selector matching in SQL

**Files:**
- Modify: `dao/src/main/resources/life/catalogue/db/dbschema.sql`, `dbschema.md`
- Create: `dao/src/main/java/life/catalogue/db/type2/DatasetTypeSetTypeHandler.java`
- Create: `dao/src/main/java/life/catalogue/db/type2/SectorSettingsTypeHandler.java`
- Create: `dao/src/main/java/life/catalogue/db/mapper/SectorProfileMapper.java`
- Create: `dao/src/main/resources/life/catalogue/db/mapper/SectorProfileMapper.xml`
- Modify: `api/src/main/java/life/catalogue/api/search/SectorSearchRequest.java` (new `profileKey`)
- Modify: `dao/src/main/resources/life/catalogue/db/mapper/SectorMapper.xml` (`WHERE`)
- Test: `dao/src/test/java/life/catalogue/db/mapper/SectorProfileMapperTest.java` (new)
- Test: `dao/src/test/java/life/catalogue/db/mapper/SectorMapperTest.java`

**Interfaces:**
- Consumes: `SectorProfile`, `SectorSelector`, `SectorSettings` (Task 2).
- Produces: `SectorProfileMapper extends CRUD<DSID<Integer>, SectorProfile>, DatasetProcessable<SectorProfile>, DatasetPageable<SectorProfile>, CopyDataset`
  with:
  - `List<SectorProfile> listAll(@Param("datasetKey") int datasetKey)`
  - `List<SectorProfile> listMatching(@Param("key") DSID<Integer> sectorKey)`, ordered by position, then id
  - the sql fragment `life.catalogue.db.mapper.SectorProfileMapper.MATCHES`, written over aliases `p` (profile),
    `s` (sector) and `d` (subject dataset)
- Produces: `SectorSearchRequest.getProfileKey()`/`setProfileKey(Integer)`, query param `profileKey`.

- [ ] **Step 1: Write the failing mapper test** `dao/src/test/java/life/catalogue/db/mapper/SectorProfileMapperTest.java`

```java
package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.Users;

import org.gbif.nameparser.api.Rank;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SectorProfileMapperTest extends CRUDPageableTestBase<Integer, SectorProfile, SectorProfileMapper> {
  static final int SUBJECT = TestEntityGenerator.DATASET11.getKey();

  public SectorProfileMapperTest() {
    super(SectorProfileMapper.class);
  }

  @Override
  SectorProfile createTestEntity(int datasetKey) {
    var p = new SectorProfile();
    p.setDatasetKey(datasetKey);
    p.setTitle("Plazi");
    p.setDescription("All treatment bank articles");
    p.setPosition(3);
    p.getSelector().setModes(EnumSet.of(Sector.Mode.MERGE));
    p.getSelector().setDatasetTypes(EnumSet.of(DatasetType.ARTICLE));
    p.getSelector().setPublisherKeys(Set.of(UUID.randomUUID()));
    p.getSelector().setAnySectorPublisher(true);
    p.getSelector().setSubjectDatasetKeys(Set.of(1001, 1002));
    p.getSelector().setSectorKeys(Set.of(7));
    p.getSettings().setRanks(EnumSet.of(Rank.GENUS, Rank.SPECIES));
    p.getSettings().setEntities(EnumSet.of(EntityType.NAME_USAGE));
    p.getSettings().setCreateImplicitNames(false);
    p.getSettings().setIssueExclusion(EnumSet.of(Issue.DOUBTFUL_NAME));
    p.getSettings().setBlockedNamePatterns(Set.of("^Incertae"));
    p.applyUser(Users.TESTER);
    return p;
  }

  @Override
  SectorProfile createTestEntityIncId(int datasetKey) {
    return createTestEntity(datasetKey);
  }

  @Override
  void updateTestObj(SectorProfile p) {
    p.setTitle("Plazi & friends");
    p.getSelector().setModes(EnumSet.noneOf(Sector.Mode.class));
    p.getSettings().setRanks(EnumSet.of(Rank.SPECIES));
  }

  private SectorProfile profile(String title, int position, Consumer<SectorSelector> selector) {
    var p = new SectorProfile();
    p.setDatasetKey(Datasets.COL);
    p.setTitle(title);
    p.setPosition(position);
    selector.accept(p.getSelector());
    p.applyUser(Users.TESTER);
    mapper().create(p);
    return p;
  }

  private static List<String> titles(List<SectorProfile> profiles) {
    return profiles.stream().map(SectorProfile::getTitle).toList();
  }

  private Sector mergeSector(int datasetKey) {
    Sector s = SectorMapperTest.create(DSID.of(datasetKey, UUID.randomUUID().toString()), DSID.of(SUBJECT, UUID.randomUUID().toString()));
    s.setMode(Sector.Mode.MERGE);
    mapper(SectorMapper.class).create(s);
    return s;
  }

  @Test
  public void listMatching() throws Exception {
    final UUID publisher = UUID.randomUUID();
    try (var st = connection().createStatement()) {
      st.execute("UPDATE dataset SET type='ARTICLE', gbif_publisher_key='" + publisher + "' WHERE key=" + SUBJECT);
    }
    Sector s = mergeSector(Datasets.COL);

    profile("all", 5, sel -> {});
    profile("merge", 1, sel -> sel.setModes(EnumSet.of(Sector.Mode.MERGE)));
    profile("attach", 2, sel -> sel.setModes(EnumSet.of(Sector.Mode.ATTACH)));
    profile("article", 3, sel -> sel.setDatasetTypes(EnumSet.of(DatasetType.ARTICLE)));
    profile("taxonomic", 3, sel -> sel.setDatasetTypes(EnumSet.of(DatasetType.TAXONOMIC)));
    profile("publisher", 4, sel -> sel.setPublisherKeys(Set.of(publisher)));
    profile("other publisher", 4, sel -> sel.setPublisherKeys(Set.of(UUID.randomUUID())));
    profile("any sector publisher", 6, sel -> sel.setAnySectorPublisher(true));
    profile("source", 7, sel -> sel.setSubjectDatasetKeys(Set.of(SUBJECT)));
    profile("sector", 8, sel -> sel.setSectorKeys(Set.of(s.getId())));
    profile("merge of another source", 9, sel -> {
      sel.setModes(EnumSet.of(Sector.Mode.MERGE));
      sel.setSubjectDatasetKeys(Set.of(SUBJECT + 1));
    });
    commit();

    // the publisher is no sector publisher of the project yet
    assertEquals(List.of("merge", "article", "publisher", "all", "source", "sector"), titles(mapper().listMatching(s)));

    var sp = new SectorPublisher();
    sp.setId(publisher);
    sp.setDatasetKey(Datasets.COL);
    sp.setAlias("P");
    sp.setTitle("Publisher");
    sp.applyUser(Users.TESTER);
    mapper(SectorPublisherMapper.class).create(sp);
    commit();
    assertEquals(List.of("merge", "article", "publisher", "all", "any sector publisher", "source", "sector"),
      titles(mapper().listMatching(s)));
  }

  @Test
  public void samePositionCascadesById() {
    Sector s = mergeSector(Datasets.COL);
    var first = profile("first", 1, sel -> {});
    var second = profile("second", 1, sel -> {});
    commit();
    assertTrue(first.getId() < second.getId());
    assertEquals(List.of("first", "second"), titles(mapper().listMatching(s)));
  }

  @Test
  public void profilesStayInTheirDataset() {
    profile("COL wide", 0, sel -> {});
    int other = newDataset();
    Sector s = mergeSector(other);
    commit();
    assertTrue(mapper().listMatching(s).isEmpty());
  }

  @Test
  public void unknownSettingsAreIgnored() throws Exception {
    var p = createTestEntity(Datasets.COL);
    mapper().create(p);
    commit();
    try (var st = connection().createStatement()) {
      st.execute("UPDATE sector_profile SET settings = settings || '{\"someFutureSetting\": true}' WHERE id=" + p.getId());
    }
    commit();
    assertEquals(p.getSettings(), mapper().get(p.getKey()).getSettings());
  }

  @Test
  public void copyDataset() {
    var p = createTestEntity(Datasets.COL);
    mapper().create(p);
    commit();
    int release = newDataset();
    mapper().copyDataset(Datasets.COL, release, false);
    commit();

    var copies = mapper().listAll(release);
    assertEquals(1, copies.size());
    var c = copies.get(0);
    assertEquals(p.getId(), c.getId());
    assertEquals(p.getTitle(), c.getTitle());
    assertEquals(p.getSelector(), c.getSelector());
    assertEquals(p.getSettings(), c.getSettings());
  }
}
```

Add to `SectorMapperTest`:

```java
  @Test
  public void searchByProfile() {
    add2Sectors();
    var pm = mapper(SectorProfileMapper.class);
    var onlyS2 = new SectorProfile();
    onlyS2.setDatasetKey(targetDatasetKey);
    onlyS2.setTitle("only s2");
    onlyS2.getSelector().setSectorKeys(Set.of(s2.getId()));
    onlyS2.applyUser(Users.TESTER);
    pm.create(onlyS2);
    var all = new SectorProfile();
    all.setDatasetKey(targetDatasetKey);
    all.setTitle("all");
    all.applyUser(Users.TESTER);
    pm.create(all);
    commit();

    var req = SectorSearchRequest.byProject(targetDatasetKey);
    req.setProfileKey(onlyS2.getId());
    assertEquals(List.of(s2.getId()), keys(mapper().search(req, new Page())));
    assertEquals(1, mapper().countSearch(req));

    req.setProfileKey(all.getId());
    assertEquals(2, mapper().countSearch(req));

    req.setProfileKey(-1);
    assertEquals(0, mapper().countSearch(req));
  }
```

(`keys(...)` is the helper `SectorMapperTest` already uses in `listBySubject`. Import
`life.catalogue.api.model.SectorProfile`.)

- [ ] **Step 2: Run them to verify they fail**

Run: `mvn -pl dao -am test -Dtest='SectorProfileMapperTest,SectorMapperTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure. `SectorProfileMapper` and `setProfileKey` do not exist.

- [ ] **Step 3: DDL**

In `dbschema.sql`, directly after the `sector_publisher` table and its index:

```sql
CREATE TABLE sector_profile (
  id SERIAL,
  dataset_key INTEGER NOT NULL REFERENCES dataset,
  title TEXT NOT NULL,
  description TEXT,
  position INTEGER NOT NULL DEFAULT 0,
  modes SECTOR_MODE[] NOT NULL DEFAULT '{}',
  dataset_types DATASETTYPE[] NOT NULL DEFAULT '{}',
  publisher_keys UUID[] NOT NULL DEFAULT '{}',
  any_sector_publisher BOOLEAN NOT NULL DEFAULT FALSE,
  subject_dataset_keys INTEGER[] NOT NULL DEFAULT '{}',
  sector_keys INTEGER[] NOT NULL DEFAULT '{}',
  settings JSONB NOT NULL DEFAULT '{}',
  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  PRIMARY KEY (dataset_key, id)
);
CREATE INDEX ON sector_profile (dataset_key);
```

Append the same `CREATE TABLE` and `CREATE INDEX` to the `sql` block of the `2026-10-05 sector profiles` section in
`dbschema.md`. A release copies its project's profiles with their ids, so the id is unique only together with the
dataset key, just as for `sector_publisher`.

- [ ] **Step 4: Type handlers**

`DatasetTypeSetTypeHandler.java`:

```java
package life.catalogue.db.type2;

import life.catalogue.api.vocab.DatasetType;
import life.catalogue.db.type.BaseEnumSetTypeHandler;

public class DatasetTypeSetTypeHandler extends BaseEnumSetTypeHandler<DatasetType> {

  public DatasetTypeSetTypeHandler() {
    super(DatasetType.class, true);
  }
}
```

`SectorSettingsTypeHandler.java`:

```java
package life.catalogue.db.type2;

import life.catalogue.api.model.SectorSettings;

import java.sql.SQLException;

import com.fasterxml.jackson.core.type.TypeReference;

/**
 * Stores the settings of a sector profile as JSONB. Unknown properties are ignored.
 */
public class SectorSettingsTypeHandler extends JsonAbstractHandler<SectorSettings> {

  public SectorSettingsTypeHandler() {
    super("SectorSettings", new TypeReference<SectorSettings>() {});
  }

  @Override
  protected SectorSettings fromJson(String json) throws SQLException {
    SectorSettings s = super.fromJson(json);
    return s == null ? new SectorSettings() : s;
  }
}
```

- [ ] **Step 5: `SectorProfileMapper.java`**

```java
package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SectorProfile;
import life.catalogue.db.CRUD;
import life.catalogue.db.CopyDataset;
import life.catalogue.db.DatasetPageable;
import life.catalogue.db.DatasetProcessable;

import java.util.List;

import org.apache.ibatis.annotations.Param;

public interface SectorProfileMapper extends CRUD<DSID<Integer>, SectorProfile>, DatasetProcessable<SectorProfile>,
  DatasetPageable<SectorProfile>, CopyDataset {

  /**
   * @return all profiles of a project or release in the order they cascade
   */
  List<SectorProfile> listAll(@Param("datasetKey") int datasetKey);

  /**
   * @return the profiles of the sector's own dataset whose selector matches the sector,
   * in the order they cascade: ascending position, ties broken by id
   */
  List<SectorProfile> listMatching(@Param("key") DSID<Integer> sectorKey);
}
```

- [ ] **Step 6: `SectorProfileMapper.xml`**

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper
  PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
  "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="life.catalogue.db.mapper.SectorProfileMapper">

  <sql id="SELECT">
    p.id,
    p.dataset_key,
    p.title,
    p.description,
    p.position,
    p.modes,
    p.dataset_types,
    p.publisher_keys,
    p.any_sector_publisher,
    p.subject_dataset_keys,
    p.sector_keys,
    p.settings,
    p.modified,
    p.modified_by,
    p.created,
    p.created_by
  </sql>

  <sql id="COLS">
    dataset_key,
    <include refid="COLS_NO_DATASETKEY"/>
  </sql>

  <sql id="COLS_NO_DATASETKEY">
    title,
    description,
    position,
    modes,
    dataset_types,
    publisher_keys,
    any_sector_publisher,
    subject_dataset_keys,
    sector_keys,
    settings,
    modified,
    modified_by
  </sql>

  <sql id="PROPS">
    #{datasetKey},
    #{title},
    #{description},
    #{position},
    #{selector.modes, typeHandler=life.catalogue.db.type2.SectorModeSetTypeHandler},
    #{selector.datasetTypes, typeHandler=life.catalogue.db.type2.DatasetTypeSetTypeHandler},
    #{selector.publisherKeys, typeHandler=life.catalogue.db.type2.UuidSetTypeHandler},
    #{selector.anySectorPublisher},
    #{selector.subjectDatasetKeys, typeHandler=life.catalogue.db.type2.IntegerSetTypeHandler},
    #{selector.sectorKeys, typeHandler=life.catalogue.db.type2.IntegerSetTypeHandler},
    #{settings, typeHandler=life.catalogue.db.type2.SectorSettingsTypeHandler}::JSONB,
    now(),
    #{modifiedBy}
  </sql>

  <!--
    True if profile p selects sector s, whose subject dataset is d. An empty array places no restriction.
    Shared with the profileKey filter of SectorMapper, so the members of a profile and the profiles of a sector
    are drawn by one and the same rule.
  -->
  <sql id="MATCHES">
    (cardinality(p.modes) = 0 OR s.mode = ANY(p.modes))
    AND (cardinality(p.dataset_types) = 0 OR d.type = ANY(p.dataset_types))
    AND (cardinality(p.publisher_keys) = 0 OR d.gbif_publisher_key = ANY(p.publisher_keys))
    AND (NOT p.any_sector_publisher OR EXISTS (
      SELECT TRUE FROM sector_publisher sp WHERE sp.dataset_key = s.dataset_key AND sp.id = d.gbif_publisher_key
    ))
    AND (cardinality(p.subject_dataset_keys) = 0 OR s.subject_dataset_key = ANY(p.subject_dataset_keys))
    AND (cardinality(p.sector_keys) = 0 OR s.id = ANY(p.sector_keys))
  </sql>

  <sql id="ORDER">
    ORDER BY p.position, p.id
  </sql>

  <resultMap id="profileResultMap" type="SectorProfile" autoMapping="true">
    <id property="id" column="id"/>
    <result property="settings" column="settings" typeHandler="life.catalogue.db.type2.SectorSettingsTypeHandler"/>
    <association property="selector" javaType="SectorSelector">
      <result property="modes" column="modes" typeHandler="life.catalogue.db.type2.SectorModeSetTypeHandler"/>
      <result property="datasetTypes" column="dataset_types" typeHandler="life.catalogue.db.type2.DatasetTypeSetTypeHandler"/>
      <result property="publisherKeys" column="publisher_keys" typeHandler="life.catalogue.db.type2.UuidSetTypeHandler"/>
      <result property="anySectorPublisher" column="any_sector_publisher"/>
      <result property="subjectDatasetKeys" column="subject_dataset_keys" typeHandler="life.catalogue.db.type2.IntegerSetTypeHandler"/>
      <result property="sectorKeys" column="sector_keys" typeHandler="life.catalogue.db.type2.IntegerSetTypeHandler"/>
    </association>
  </resultMap>

  <select id="get" resultMap="profileResultMap">
    SELECT <include refid="SELECT"/>
    FROM sector_profile p
    WHERE p.id = #{key.id} AND p.dataset_key = #{key.datasetKey}
  </select>

  <select id="exists" resultType="boolean">
    SELECT EXISTS (SELECT TRUE FROM sector_profile WHERE id = #{key.id} AND dataset_key = #{key.datasetKey})
  </select>

  <select id="list" resultMap="profileResultMap">
    SELECT <include refid="SELECT"/>
    FROM sector_profile p
    WHERE p.dataset_key = #{datasetKey}
    <include refid="ORDER"/>
    <include refid="life.catalogue.db.Common.limit"/>
  </select>

  <select id="listAll" resultMap="profileResultMap">
    SELECT <include refid="SELECT"/>
    FROM sector_profile p
    WHERE p.dataset_key = #{datasetKey}
    <include refid="ORDER"/>
  </select>

  <select id="listMatching" resultMap="profileResultMap">
    SELECT <include refid="SELECT"/>
    FROM sector s
      JOIN sector_profile p ON p.dataset_key = s.dataset_key
      LEFT JOIN dataset d ON d.key = s.subject_dataset_key
    WHERE s.dataset_key = #{key.datasetKey} AND s.id = #{key.id}
      AND <include refid="MATCHES"/>
    <include refid="ORDER"/>
  </select>

  <select id="count" resultType="integer">
    SELECT count(*) FROM sector_profile WHERE dataset_key = #{datasetKey}
  </select>

  <delete id="deleteByDataset" parameterType="map">
    DELETE FROM sector_profile WHERE dataset_key = #{datasetKey}
  </delete>

  <insert id="create" parameterType="SectorProfile" useGeneratedKeys="true" keyProperty="id" keyColumn="id">
    INSERT INTO sector_profile (<include refid="COLS"/>, created_by)
    VALUES (<include refid="PROPS"/>, #{createdBy})
  </insert>

  <insert id="copyDataset" parameterType="map">
    INSERT INTO sector_profile (id, dataset_key, <include refid="COLS_NO_DATASETKEY"/>, created, created_by)
    SELECT p.id, #{newDatasetKey}, <include refid="COLS_NO_DATASETKEY"/>, created, created_by
    FROM sector_profile p
    WHERE p.dataset_key = #{datasetKey}
  </insert>

  <update id="update" parameterType="SectorProfile">
    UPDATE sector_profile
    SET (<include refid="COLS"/>) = (<include refid="PROPS"/>)
    WHERE id = #{id} AND dataset_key = #{datasetKey}
  </update>

  <delete id="delete" parameterType="map">
    DELETE FROM sector_profile WHERE id = #{key.id} AND dataset_key = #{key.datasetKey}
  </delete>

  <select id="processDataset" parameterType="map" resultMap="profileResultMap" resultOrdered="true" fetchSize="1000" resultSetType="FORWARD_ONLY">
    SELECT <include refid="SELECT"/>
    FROM sector_profile p
    WHERE p.dataset_key = #{datasetKey}
  </select>

</mapper>
```

- [ ] **Step 7: `SectorSearchRequest.profileKey`**

Next to `publisherKey`:

```java
  /**
   * Only sectors the given sector profile currently selects.
   */
  @QueryParam("profileKey")
  private Integer profileKey;
```

Add a getter and setter, and add `profileKey` to `equals` and `hashCode`. The class has no copy constructor.

In the `WHERE` fragment of `SectorMapper.xml`, add after the `req.publisherKey` condition inside `<where>`:

```xml
        <if test="req.profileKey != null">
          AND EXISTS (
            SELECT TRUE FROM sector_profile p LEFT JOIN dataset d ON d.key = s.subject_dataset_key
            WHERE p.dataset_key = s.dataset_key AND p.id = #{req.profileKey}
              AND <include refid="life.catalogue.db.mapper.SectorProfileMapper.MATCHES"/>
          )
        </if>
```

The subquery's own `d` shadows the outer `d` that the `publisherKey` filter joins. That is intended: `MATCHES` must
see the subject dataset of `s`, which both are.

- [ ] **Step 8: Run the tests to verify they pass**

Run: `mvn -pl dao -am test -Dtest='SectorProfileMapperTest,SectorMapperTest,PgSetupRuleTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS. `PgSetupRuleTest` must stay green: there is no new pg enum.

- [ ] **Step 9: Commit**

```bash
git add dao/src/main/resources/life/catalogue/db/dbschema.sql dao/src/main/resources/life/catalogue/db/dbschema.md \
  dao/src/main/java/life/catalogue/db/type2/DatasetTypeSetTypeHandler.java dao/src/main/java/life/catalogue/db/type2/SectorSettingsTypeHandler.java \
  dao/src/main/java/life/catalogue/db/mapper/SectorProfileMapper.java dao/src/main/resources/life/catalogue/db/mapper/SectorProfileMapper.xml \
  api/src/main/java/life/catalogue/api/search/SectorSearchRequest.java dao/src/main/resources/life/catalogue/db/mapper/SectorMapper.xml \
  dao/src/test/java/life/catalogue/db/mapper/SectorProfileMapperTest.java dao/src/test/java/life/catalogue/db/mapper/SectorMapperTest.java
git commit -m "sector_profile table with live selector matching in SQL"
```

---

### Task 4: Profile DAO, effective settings, and profiles travel with releases

**Files:**
- Create: `dao/src/main/java/life/catalogue/dao/SectorProfileDao.java`
- Modify: `core/src/main/java/life/catalogue/release/AbstractProjectCopy.java:345`
- Modify: `dao/src/main/java/life/catalogue/dao/DatasetDao.java:404-409` (`deleteKeptReleaseData`)
- Modify: `webservice/src/main/java/life/catalogue/command/BundleBuildCmd.java:287`
- Test: `dao/src/test/java/life/catalogue/dao/SectorProfileDaoTest.java` (new)

**Interfaces:**
- Consumes: `SectorProfileMapper.listMatching`, `SectorSettingsResolver.resolve`.
- Produces: `SectorProfileDao extends DatasetEntityDao<Integer, SectorProfile, SectorProfileMapper>` with:
  - constructor `(SqlSessionFactory, Validator)`
  - `EffectiveSectorSettings effectiveSettings(DSID<Integer> sectorKey)`, which throws `NotFoundException`
  - `static void validatePatterns(SyncSettings s)`, which throws `IllegalArgumentException`

- [ ] **Step 1: Write the failing DAO test** `dao/src/test/java/life/catalogue/dao/SectorProfileDaoTest.java`

```java
package life.catalogue.dao;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SectorProfile;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.mapper.SectorMapperTest;

import org.gbif.nameparser.api.Rank;

import java.util.EnumSet;
import java.util.Set;

import org.apache.ibatis.session.SqlSession;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SectorProfileDaoTest extends DaoTestBase {
  SectorProfileDao dao;

  @Before
  public void init() {
    dao = new SectorProfileDao(factory(), validator);
  }

  private SectorProfile profile(String title) {
    var p = new SectorProfile();
    p.setDatasetKey(Datasets.COL);
    p.setTitle(title);
    return p;
  }

  @Test
  public void effectiveSettings() {
    Sector s = SectorMapperTest.create();
    s.setMode(Sector.Mode.MERGE);
    s.setRanks(EnumSet.noneOf(Rank.class));
    try (SqlSession session = factory().openSession(true)) {
      session.getMapper(SectorMapper.class).create(s);
    }
    var p = profile("genera only");
    p.getSettings().setRanks(EnumSet.of(Rank.GENUS));
    dao.create(p, Users.TESTER);

    var eff = dao.effectiveSettings(s.getKey());
    assertEquals(EnumSet.of(Rank.GENUS), eff.getSettings().getRanks());
    assertEquals("profile:" + p.getId(), eff.getSources().get("ranks"));
    // the sector itself sets a code in SectorMapperTest.create
    assertEquals(s.getCode(), eff.getSettings().getCode());
    assertEquals("sector", eff.getSources().get("code"));
  }

  @Test(expected = NotFoundException.class)
  public void effectiveSettingsOfMissingSector() {
    dao.effectiveSettings(DSID.of(Datasets.COL, -1));
  }

  @Test(expected = IllegalArgumentException.class)
  public void invalidBlockedNamePatternIsRejected() {
    var p = profile("broken");
    p.getSettings().setBlockedNamePatterns(Set.of("Aus (bus"));
    dao.create(p, Users.TESTER);
  }

  @Test(expected = IllegalArgumentException.class)
  public void invalidNameFilterIsRejected() {
    var p = profile("broken");
    p.getSettings().setNameFilter("[A-Z");
    dao.create(p, Users.TESTER);
  }
}
```

`SectorMapperTest.create()` targets `Datasets.COL` with a subject in `DATASET11`. Both exist in the apple data that
`DaoTestBase` loads.

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -pl dao -am test -Dtest=SectorProfileDaoTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure. `SectorProfileDao` does not exist.

- [ ] **Step 3: Create `SectorProfileDao`**

```java
package life.catalogue.dao;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.mapper.SectorProfileMapper;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

public class SectorProfileDao extends DatasetEntityDao<Integer, SectorProfile, SectorProfileMapper> {

  public SectorProfileDao(SqlSessionFactory factory, Validator validator) {
    super(false, factory, SectorProfile.class, SectorProfileMapper.class, validator);
  }

  @Override
  protected void validate(SectorProfile p) throws ConstraintViolationException {
    super.validate(p);
    // a profile can select thousands of sectors, so a broken regex has to fail here and not in each of their syncs
    validatePatterns(p.getSettings());
  }

  /**
   * @throws IllegalArgumentException if the name filter or a blocked name pattern is no valid regular expression
   */
  public static void validatePatterns(SyncSettings s) {
    compile("nameFilter", s.getNameFilter(), 0);
    if (s.getBlockedNamePatterns() != null) {
      for (String p : s.getBlockedNamePatterns()) {
        // compiled the way NameBlocklist does
        compile("blockedNamePatterns", p == null ? null : p.trim(), Pattern.CASE_INSENSITIVE);
      }
    }
  }

  private static void compile(String setting, String regex, int flags) {
    if (regex != null && !regex.isBlank()) {
      try {
        Pattern.compile(regex, flags);
      } catch (PatternSyntaxException e) {
        throw new IllegalArgumentException("Invalid " + setting + " regex '" + regex + "': " + e.getDescription(), e);
      }
    }
  }

  /**
   * The settings a sync of the sector uses, and the level each comes from.
   * A sector is resolved against the profiles of its own dataset, so a release sector against the profiles
   * copied into that release.
   */
  public EffectiveSectorSettings effectiveSettings(DSID<Integer> sectorKey) {
    try (SqlSession session = factory.openSession()) {
      Sector s = session.getMapper(SectorMapper.class).get(sectorKey);
      if (s == null) {
        throw NotFoundException.notFound(Sector.class, sectorKey);
      }
      return SectorSettingsResolver.resolve(s, session.getMapper(SectorProfileMapper.class).listMatching(sectorKey));
    }
  }
}
```

Note: the resource layer turns an `IllegalArgumentException` into a 400, as it already does for `SectorDao`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -pl dao -am test -Dtest=SectorProfileDaoTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, 4 tests.

- [ ] **Step 5: Copy, delete and bundle profiles like sector publishers**

- `AbstractProjectCopy.copyData()`, after `copyTable(SectorPublisher.class, SectorPublisherMapper.class, session);`:
  `copyTable(SectorProfile.class, SectorProfileMapper.class, session);`
- `DatasetDao.deleteKeptReleaseData`, after the `SectorPublisherMapper` line:
  `session.getMapper(SectorProfileMapper.class).deleteByDataset(key);`
  Like sectors and publishers, profiles are kept for deleted public releases and removed with the rest of the kept
  data.
- `BundleBuildCmd`, after the `sector_publisher` line:
  `tables.add(new String[]{"sector_profile", "SELECT %s FROM sector_profile WHERE dataset_key = " + key});`

- [ ] **Step 6: Compile everything**

Run: `mvn -pl api,dao,core,webservice -am install -DskipTests`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add dao/src/main/java/life/catalogue/dao/SectorProfileDao.java dao/src/test/java/life/catalogue/dao/SectorProfileDaoTest.java \
  core/src/main/java/life/catalogue/release/AbstractProjectCopy.java dao/src/main/java/life/catalogue/dao/DatasetDao.java \
  webservice/src/main/java/life/catalogue/command/BundleBuildCmd.java
git commit -m "SectorProfileDao with effective settings; releases and bundles carry their profiles"
```

---

### Task 5: REST endpoints

**Files:**
- Create: `webservice/src/main/java/life/catalogue/resources/dataset/SectorProfileResource.java`
- Modify: `webservice/src/main/java/life/catalogue/resources/dataset/SectorResource.java` (constructor + `{id}/settings`)
- Modify: `webservice/src/main/java/life/catalogue/WsROServer.java` (`registerReadOnlyResources` and its caller, ~277 and ~339)
- Modify: `webservice/src/main/java/life/catalogue/WsServer.java` (~364, 503, 506)

**Interfaces:**
- Consumes: `SectorProfileDao` (Task 4), `SectorDao.search(SectorSearchRequest, Page)`,
  `SectorSearchRequest.setProfileKey` (Task 3).
- Produces:
  - `GET|POST /dataset/{key}/sector/profile`
  - `GET|PUT|DELETE /dataset/{key}/sector/profile/{id}`
  - `GET /dataset/{key}/sector/profile/{id}/sector` (paged sectors)
  - `GET /dataset/{key}/sector/{id}/settings` (`EffectiveSectorSettings`)

There are no Jersey resource tests in this repo: the resources are thin and the DAOs carry the tests. This task is
verified by compiling and by a local smoke test.

- [ ] **Step 1: `SectorProfileResource`**

```java
package life.catalogue.resources.dataset;

import life.catalogue.api.model.*;
import life.catalogue.api.search.QuerySearchRequest;
import life.catalogue.api.search.SectorSearchRequest;
import life.catalogue.dao.SectorDao;
import life.catalogue.dao.SectorProfileDao;

import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/**
 * Sector profiles: sync settings for every sector of a project a selector matches.
 * See docs/SECTOR-SETTINGS.md.
 */
@Path("/dataset/{key}/sector/profile")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class SectorProfileResource extends AbstractDatasetScopedResource<Integer, SectorProfile, QuerySearchRequest> {
  private final SectorDao sdao;

  public SectorProfileResource(SectorProfileDao dao, SectorDao sdao) {
    super(SectorProfile.class, dao);
    this.sdao = sdao;
  }

  /**
   * The sectors the profile selects right now, i.e. the ones it applies to on their next sync.
   */
  @GET
  @Path("{id}/sector")
  public ResultPage<Sector> sectors(@PathParam("key") int datasetKey, @PathParam("id") int id, @Valid @BeanParam Page page) {
    var req = SectorSearchRequest.byProject(datasetKey);
    req.setProfileKey(id);
    return sdao.search(req, page);
  }
}
```

CRUD, list and count come from `AbstractDatasetScopedResource`. Its `POST`/`PUT`/`DELETE` are already `@ProjectOnly`
and restricted to admins and editors.

- [ ] **Step 2: Effective settings on `SectorResource`**

Add a `SectorProfileDao profileDao` constructor parameter and field, then:

```java
  /**
   * The settings a sync of this sector uses, resolved from built-in defaults, the matching sector profiles and the
   * sector itself, with the level every value comes from.
   */
  @GET
  @Path("{id}/settings")
  public EffectiveSectorSettings settings(@PathParam("key") int datasetKey, @PathParam("id") int id) {
    return profileDao.effectiveSettings(DSID.of(datasetKey, id));
  }
```

- [ ] **Step 3: Wiring**

- **`WsROServer`**:
  - After `SectorPublisherDao spdao = ...` (~266), add
    `SectorProfileDao sprdao = new SectorProfileDao(getSqlSessionFactory(), validator);`.
  - Add the parameters `SectorProfileDao sprdao, SectorDao secdao` to `registerReadOnlyResources` after `spdao`.
  - Inside it, register `j.register(new SectorProfileResource(sprdao, secdao));` after `SectorPublisherResource`.
  - Pass `sprdao, secdao` at the call site (~277). `secdao` already exists there.
- **`WsServer`**:
  - Create `SectorProfileDao sprdao` the same way next to `spdao` (~364).
  - Pass it to `new SectorResource(secdao, fmsDao, syncManager, sprdao)` (~503).
  - Pass `sprdao, secdao` to `WsROServer.registerReadOnlyResources` (~506).
- `WsBundleServer` inherits the read-only stack, so it needs nothing.

- [ ] **Step 4: Compile and run the webservice unit tests**

Run: `mvn -pl webservice -am install -Dtest='*Test' -Dsurefire.failIfNoSpecifiedTests=false -DskipITs`
Expected: BUILD SUCCESS. This includes `OpenApiBodyWriterTest`, which renders the OpenAPI document from all resources.

- [ ] **Step 5: Commit**

```bash
git add webservice/src/main/java/life/catalogue/resources/dataset/SectorProfileResource.java \
  webservice/src/main/java/life/catalogue/resources/dataset/SectorResource.java \
  webservice/src/main/java/life/catalogue/WsROServer.java webservice/src/main/java/life/catalogue/WsServer.java
git commit -m "REST endpoints for sector profiles and a sector's effective settings"
```

---

### Task 6: Syncs read profiles, and the `SECTOR_*` settings go

**Files:**
- Modify: `core/src/main/java/life/catalogue/assembly/SectorRunnable.java:39-42,340-375,393-403`
- Modify: `api/src/main/java/life/catalogue/api/vocab/Setting.java` (remove 7 values)
- Modify: `api/src/main/java/life/catalogue/api/jackson/SettingsDeserializer.java`
- Modify: `dao/src/main/java/life/catalogue/db/type2/SettingsTypeHandler.java`
- Modify tests: `api/src/test/java/life/catalogue/api/model/DatasetSettingsTest.java:45-46`, `api/src/test/java/life/catalogue/api/jackson/SettingsDeserializerTest.java`,
  `dao/src/test/java/life/catalogue/db/mapper/DatasetMapperTest.java`,
  `core/src/test/java/life/catalogue/assembly/SectorSyncIT.java:59-75,108-131`,
  `core/src/test/java/life/catalogue/assembly/SectorSyncMergeIT.java:204-210`,
  `core/src/test/java/life/catalogue/release/XReleaseIT.java:174-181`

**Interfaces:**
- Consumes: `SectorProfileMapper.listMatching`, `SectorSettingsResolver.resolve`, `SyncSettings.copy`.
- Produces:
  - `SectorRunnable.loadSectorAndUpdateDatasetImport` returns a sector whose settings are fully resolved.
  - `Setting` no longer has `SECTOR_ENTITIES`, `SECTOR_RANKS`, `SECTOR_NAME_TYPES`, `SECTOR_NAME_STATUS_EXCLUSION`,
    `SECTOR_COPY_ACCORDING_TO`, `SECTOR_REMOVE_ORDINALS` or `SECTOR_CREATE_IMPLICIT_NAMES`.
  - `SettingsDeserializer.keysFromJson(Map<String, Object>)` returns `Map<Setting, Object>` and skips unknown keys.

- [ ] **Step 1: Write the failing settings tests (Review Focus 1)**

In `SettingsDeserializerTest` add:

```java
  /**
   * A setting removed from the enum, e.g. the former "sector ranks", must not break every reader of the dataset.
   */
  @Test
  public void unknownKeysAreIgnored() throws Exception {
    DatasetSettings ds = ApiModule.MAPPER.readValue(
      "{\"sector ranks\": [\"genus\"], \"nomenclatural code\": \"botanical\"}", DatasetSettings.class);
    assertEquals(1, ds.size());
    assertEquals(NomCode.BOTANICAL, ds.getEnum(Setting.NOMENCLATURAL_CODE));
  }
```

In `DatasetMapperTest` add:

```java
  @Test
  public void settingsIgnoreUnknownKeys() throws Exception {
    try (var st = connection().createStatement()) {
      st.execute("UPDATE dataset SET settings = '{\"sector ranks\": [\"genus\"], \"nomenclatural code\": \"botanical\"}' WHERE key=" + appleKey);
    }
    commit();
    var ds = mapper().getSettings(appleKey);
    assertEquals(NomCode.BOTANICAL, ds.getEnum(Setting.NOMENCLATURAL_CODE));
    assertEquals(1, ds.size());
  }
```

`DatasetSettings` extends `HashMap<Setting, Object>`, hence `size()`. `SettingsDeserializerTest` needs the imports
`life.catalogue.api.jackson.ApiModule`, `life.catalogue.api.model.DatasetSettings`, `org.gbif.nameparser.api.NomCode`
and `static org.junit.Assert.assertEquals`. `DatasetMapperTest` already imports `org.junit.Assert.*`; add `NomCode`
and `Setting` there.

- [ ] **Step 2: Remove the settings and run the tests to verify they fail**

- Delete the seven `SECTOR_*` values listed in Interfaces, with their javadoc, from `Setting.java`. Drop imports that
  become unused there.
- In `DatasetSettingsTest.generateTestValue`, replace the two removed entries with
  `Map.entry(Setting.SYNC_SCHEDULER_SOURCES, List.of(1000, 1001))`. That keeps a multi-valued setting in the
  roundtrip.

Run: `mvn -pl api,dao -am test -Dtest='SettingsDeserializerTest,DatasetSettingsTest,DatasetMapperTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL. `unknownKeysAreIgnored` and `settingsIgnoreUnknownKeys` fail with "Expected valid Setting value" /
`InvalidFormatException`.

- [ ] **Step 3: Tolerate unknown setting keys**

In `SettingsDeserializer`:

```java
  private static final TypeReference<Map<String, Object>> RAW = new TypeReference<Map<String, Object>>() {};

  @Override
  public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
    Map<Setting, Object> map = keysFromJson(p.readValueAs(RAW));
    convertFromJSON(map);
    return DatasetSettings.of(map);
  }

  /**
   * Looks up the setting of every key, skipping keys that are no setting (anymore) with a warning:
   * one stale key must not make the settings of a dataset unreadable. Values are left as they are.
   */
  public static Map<Setting, Object> keysFromJson(Map<String, Object> raw) {
    Map<Setting, Object> map = new HashMap<>();
    if (raw != null) {
      for (Map.Entry<String, Object> e : raw.entrySet()) {
        try {
          map.put(VocabularyUtils.lookupEnum(e.getKey(), Setting.class), e.getValue());
        } catch (IllegalArgumentException ex) {
          LOG.warn("Ignore unknown dataset setting {}", e.getKey());
        }
      }
    }
    return map;
  }
```

Remove the now-unused `REF`. Use `VocabularyUtils.lookupEnum` exactly as `readSingleValue` already does, and keep its
import.

In `SettingsTypeHandler`, replace `fromJson`:

```java
  private static final ObjectReader RAW_READER = ApiModule.MAPPER.readerFor(new TypeReference<Map<String, Object>>() {});

  @Override
  protected Map<Setting, Object> fromJson(String json) throws SQLException {
    if (Strings.isNullOrEmpty(json)) return Collections.emptyMap();
    Map<Setting, Object> map;
    try {
      map = SettingsDeserializer.keysFromJson(RAW_READER.readValue(json));
    } catch (IOException e) {
      throw new SQLException("Unable to convert JSONB to dataset settings", e);
    }
    // we treat frequency special and store its days to allow simpler calculations in SQL
    Integer days = (Integer) map.remove(Setting.IMPORT_FREQUENCY);
    SettingsDeserializer.convertFromJSON(map);
    if (days != null) {
      map.put(Setting.IMPORT_FREQUENCY, Frequency.fromDays(days));
    }
    return map;
  }
```

Imports: `life.catalogue.api.jackson.ApiModule`, `com.fasterxml.jackson.databind.ObjectReader`,
`com.google.common.base.Strings`, `java.io.IOException`.

- [ ] **Step 4: Run the settings tests to verify they pass**

Run: `mvn -pl api,dao -am test -Dtest='SettingsDeserializerTest,DatasetSettingsTest,DatasetMapperTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 5: Resolve sync settings from profiles**

In `SectorRunnable`:
- Delete `MERGE_RANKS_DEFAULT` (lines 39-42) and `addProjectSettings` (393-403).
- In `loadSectorAndUpdateDatasetImport`, replace everything from `// apply dataset defaults if needed` through the end
  of the ranks block, i.e. lines 340-375 up to `if (validate) {`, with:

```java
      // the settings a sync uses: built-in defaults, the matching profiles of the project, then the sector itself
      var profiles = session.getMapper(SectorProfileMapper.class).listMatching(sectorKey);
      var effective = SectorSettingsResolver.resolve(s, profiles);
      SyncSettings.copy(effective.getSettings(), s);
      LOG.info("Sector {} uses {} matching profiles. Setting sources: {}", sectorKey, profiles.size(), effective.getSources());
```

Imports: `life.catalogue.dao.SectorSettingsResolver`. `SectorProfileMapper` and `SyncSettings` are covered by the
existing wildcard imports of `life.catalogue.db.mapper.*` and `life.catalogue.api.model.*`. Drop imports that are now
unused (`Setting`, `DatasetSettings`, `ObjectUtils`, `Supplier`, `Consumer`, `Rank`), checking each against the rest of
the file.

Update the stale comment in `SectorSync.insertsUsages`, "entities are defaulted to all in
SectorRunnable.loadSectorAndUpdateDatasetImport". It stays true; it now happens via `SectorSettingsResolver`, so say
that.

- [ ] **Step 6: Convert the sync tests from settings to profiles**

`SectorSyncIT`:
- Add a field `SectorProfile projectDefaults;`.
- In `init()`, replace the settings block (the one setting `SECTOR_COPY_ACCORDING_TO` to false) with:

```java
    // accordingTo syncs are off by default for the project
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var pm = session.getMapper(SectorProfileMapper.class);
      pm.deleteByDataset(Datasets.COL);
      projectDefaults = new SectorProfile();
      projectDefaults.setDatasetKey(Datasets.COL);
      projectDefaults.setTitle("Project defaults");
      projectDefaults.getSettings().setCopyAccordingTo(false);
      projectDefaults.applyUser(Users.TESTER);
      pm.create(projectDefaults);
    }
```

- In `accordingto()`, remove `s.setCopyAccordingTo(false);` from the `createSector` modifier. The sector must leave
  the flag unset, or its own `false` now beats the profile. Keep `s.setRemoveOrdinals(true);`.
- Replace the later block that sets `SECTOR_COPY_ACCORDING_TO` to true with:

```java
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      projectDefaults.getSettings().setCopyAccordingTo(true);
      session.getMapper(SectorProfileMapper.class).update(projectDefaults);
    }
```

`SectorSyncMergeIT.setupProject`: replace the "project dataset settings" lines (the `DatasetSettings` with
`SECTOR_REMOVE_ORDINALS`) with:

```java
      // project defaults
      var defaults = new SectorProfile();
      defaults.setDatasetKey(Datasets.COL);
      defaults.setTitle("Project defaults");
      defaults.getSettings().setRemoveOrdinals(true);
      defaults.applyUser(Users.TESTER);
      session.getMapper(SectorProfileMapper.class).create(defaults);
```

`XReleaseIT` (lines 174-181): delete the "set project default settings" block. `SectorSyncMergeIT.setupProject`,
called right after it, used to replace the whole settings map with `updateSettings`, so these settings never took
effect. Removing them keeps the test's behaviour.

- [ ] **Step 7: Run the converted sync and release ITs**

Run: `mvn -pl api,dao,core -am install -DskipTests && mvn -pl core verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='SectorSyncIT,SectorSyncMergeIT,HierarchySyncIT,XReleaseIT'`
Expected: PASS, with the same expected trees as before.

`SectorSyncIT.accordingto` now really removes ordinals, because the sector's `removeOrdinals=true` is persisted. If
`cat14b.txt` prints ordinals and the assertion fails, take the new tree from the log and check that only ordinals
differ. Then update the expected file and say so in the commit message.

- [ ] **Step 8: Commit**

```bash
git add -A api/src/main/java/life/catalogue/api/vocab/Setting.java api/src/main/java/life/catalogue/api/jackson/SettingsDeserializer.java \
  dao/src/main/java/life/catalogue/db/type2/SettingsTypeHandler.java core/src/main/java/life/catalogue/assembly/SectorRunnable.java \
  core/src/main/java/life/catalogue/assembly/SectorSync.java \
  api/src/test dao/src/test/java/life/catalogue/db/mapper/DatasetMapperTest.java core/src/test
git commit -m "Syncs resolve their settings from sector profiles; the SECTOR_* dataset settings are gone

Unknown setting keys are now skipped with a warning instead of making a dataset's settings unreadable."
```

---

### Task 7: Merge blocklists per profile and sector

**Files:**
- Create: `core/src/main/java/life/catalogue/assembly/NameBlocklist.java`
- Modify: `core/src/main/java/life/catalogue/assembly/TreeMergeHandlerConfig.java` (blocked names → `NameBlocklist`)
- Modify: `core/src/main/java/life/catalogue/assembly/TreeMergeHandler.java:59-75,586-601`
- Modify: `core/src/test/java/life/catalogue/assembly/SectorSyncMergeIT.java` (`profiles.yaml` hook + a new parameter)
- Create: `core/src/test/resources/txtree/profiles/{project.txtree,src.txtree,src.yaml,profiles.yaml,expected.txtree,readme.md}`
- Test: `core/src/test/java/life/catalogue/assembly/NameBlocklistTest.java` (new)

**Interfaces:**
- Consumes: the resolved sector settings from Task 6 (`sector.getIssueExclusion()`, `getBlockedNames()`,
  `getBlockedNamePatterns()`).
- Produces: `NameBlocklist(Collection<String> names, Collection<String> patterns)` with
  `boolean isBlocked(FormattableName n)` and `boolean isEmpty()`.
- Produces: `TreeMergeHandlerConfig.isBlocked` keeps its signature and delegates.

- [ ] **Step 1: Write the failing unit test** `core/src/test/java/life/catalogue/assembly/NameBlocklistTest.java`

```java
package life.catalogue.assembly;

import life.catalogue.api.model.Name;

import org.gbif.nameparser.api.Rank;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

public class NameBlocklistTest {

  static Name name(String sciname, String authorship) {
    return Name.newBuilder().scientificName(sciname).authorship(authorship).rank(Rank.SPECIES).build();
  }

  @Test
  public void namesWithAndWithoutAuthorship() {
    var bl = new NameBlocklist(List.of("amara AENEA", "Bus cus L."), null);
    assertTrue(bl.isBlocked(name("Amara aenea", "(De Geer, 1774)")));
    assertTrue(bl.isBlocked(name("Bus cus", "L.")));
    assertFalse(bl.isBlocked(name("Bus cus", "Mill.")));
    assertFalse(bl.isBlocked(name("Amara familiaris", null)));
  }

  @Test
  public void patternsAreFoundAnywhereInTheLabel() {
    var bl = new NameBlocklist(null, List.of("^incertae", "sp\\. ?nov"));
    assertTrue(bl.isBlocked(name("Incertae sedis", null)));
    assertTrue(bl.isBlocked(name("Aus sp. nov", null)));
    assertFalse(bl.isBlocked(name("Aus bus", null)));
  }

  @Test
  public void invalidPatternsAreSkipped() {
    var bl = new NameBlocklist(null, List.of("Aus (bus", "^Cus"));
    assertTrue(bl.isBlocked(name("Cus dus", null)));
    assertFalse(bl.isBlocked(name("Aus (bus", null)));
  }

  @Test
  public void empty() {
    assertTrue(new NameBlocklist(null, List.of(" ")).isEmpty());
    assertFalse(new NameBlocklist(List.of("Aus"), null).isEmpty());
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -pl core test -Dtest=NameBlocklistTest`
Expected: compilation failure. `NameBlocklist` does not exist.

- [ ] **Step 3: Create `NameBlocklist`**, moved out of `TreeMergeHandlerConfig`

```java
package life.catalogue.assembly;

import life.catalogue.api.model.FormattableName;

import java.util.*;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Names a merge must never bring in. Names match case insensitively against the full label with authorship or the
 * bare scientific name, patterns are case insensitive regular expressions searched anywhere in the label.
 */
public class NameBlocklist {
  private static final Logger LOG = LoggerFactory.getLogger(NameBlocklist.class);
  private final Set<String> names = new HashSet<>();
  private final List<Pattern> patterns = new ArrayList<>();

  public NameBlocklist(@Nullable Collection<String> names, @Nullable Collection<String> patterns) {
    if (names != null) {
      for (String n : names) {
        if (!StringUtils.isBlank(n)) {
          this.names.add(norm(n));
        }
      }
    }
    if (patterns != null) {
      for (String p : patterns) {
        if (!StringUtils.isBlank(p)) {
          try {
            this.patterns.add(Pattern.compile(p.trim(), Pattern.CASE_INSENSITIVE));
          } catch (IllegalArgumentException e) {
            LOG.warn("Invalid name pattern: " + p, e);
          }
        }
      }
    }
  }

  public boolean isEmpty() {
    return names.isEmpty() && patterns.isEmpty();
  }

  public boolean isBlocked(FormattableName n) {
    if (names.contains(norm(n.getLabel())) || names.contains(norm(n.getScientificName()))) {
      return true;
    }
    for (Pattern p : patterns) {
      if (p.matcher(n.getLabel()).find()) {
        return true;
      }
    }
    return false;
  }

  private static String norm(String x) {
    return x == null ? null : x.trim().toUpperCase();
  }
}
```

In `TreeMergeHandlerConfig`:
- Delete the `blockedNames` and `blockedNamePatterns` fields and the two loops that fill them in the constructor.
- Add `private final NameBlocklist blocklist;`, assigned in the constructor right after `xCfg` is set:
  `blocklist = new NameBlocklist(xCfg.blockedNames, xCfg.blockedNamePatterns);`
- Make `isBlocked(FormattableName n)` `return blocklist.isBlocked(n);`.
- Delete the leftover `main` method. It only tried `Pattern.compile` on literals.
- Remove imports that become unused.

- [ ] **Step 4: Run the unit test to verify it passes**

Run: `mvn -pl core test -Dtest=NameBlocklistTest`
Expected: PASS, 4 tests.

- [ ] **Step 5: Write the failing merge IT fixture**

Directory `core/src/test/resources/txtree/profiles/`.

`readme.md`:

```markdown
# profiles

Verifies that sector profiles cascade in position order and that profile blocklists reach the merge.

- `all merges` (position 1) lets subspecies through.
- `this source` (position 2) selects source dataset 100, narrows the ranks to genus and species and blocks
  `Amara aenea`.

The later profile wins for ranks, so the subspecies `Bembidion properans alpinum` is not merged. The blocklist reaches
the merge, so `Amara aenea` is not merged while its genus and `Amara familiaris` are.
```

`project.txtree`:

```
Biota [unranked]
  Animalia [kingdom]
    Arthropoda [phylum]
      Insecta [class]
        Coleoptera [order]
          Carabidae [family]
            Bembidion Latreille, 1802 [genus]
              Bembidion lampros (Herbst, 1784) [species]
```

`src.txtree`:

```
Biota [unranked]
  Animalia [kingdom]
    Arthropoda [phylum]
      Insecta [class]
        Coleoptera [order]
          Carabidae [family]
            Amara Bonelli, 1810 [genus]
              Amara aenea (De Geer, 1774) [species]
              Amara familiaris (Duftschmid, 1812) [species]
            Bembidion Latreille, 1802 [genus]
              Bembidion properans (Stephens, 1828) [species]
                Bembidion properans alpinum Netolitzky, 1914 [subspecies]
```

`src.yaml`:

```yaml
code: zoological
```

`profiles.yaml`:

```yaml
# profiles cascade in position order; source datasets are numbered from 100 in SectorSyncMergeIT
- title: all merges
  position: 1
  selector:
    modes: [MERGE]
  settings:
    ranks: [GENUS, SPECIES, SUBSPECIES]
- title: this source
  position: 2
  selector:
    subjectDatasetKeys: [100]
  settings:
    ranks: [GENUS, SPECIES]
    blockedNames:
      - Amara aenea
```

`expected.txtree`:

```
Biota [unranked]
  Animalia [kingdom]
    Arthropoda [phylum]
      Insecta [class]
        Coleoptera [order]
          Carabidae [family]
            Amara Bonelli, 1810 [genus]
              Amara familiaris (Duftschmid, 1812) [species]
            Bembidion Latreille, 1802 [genus]
              Bembidion lampros (Herbst, 1784) [species]
              Bembidion properans (Stephens, 1828) [species]
```

In `SectorSyncMergeIT`:
- Add the parameter `{"profiles", List.of("src")}` at the end of `data()`, with the comment
  `// sector profiles cascade and their blocklists reach the merge, see readme.md`.
- Add `private static final TypeReference<List<SectorProfile>> profileListTypeRef = new TypeReference<>() {};`.
- Add `public final List<SectorProfile> profiles = new ArrayList<>();` to `ProjectTestInfo`.
- In `setupProject`, before the `try (SqlSession session ...)` block that creates the sectors, add:

```java
    // do we have a sector profiles file?
    try {
      info.profiles.addAll(YamlUtils.read(profileListTypeRef, Resources.getResourceAsStream("txtree/" + project + "/profiles.yaml")));
    } catch (IOException e) {
      // only the project defaults then
    }
```

- Right after creating the `defaults` profile from Task 6, inside that session, add:

```java
      for (var p : info.profiles) {
        p.setDatasetKey(Datasets.COL);
        p.applyUser(Users.TESTER);
        session.getMapper(SectorProfileMapper.class).create(p);
      }
```

- [ ] **Step 6: Run the IT to verify it fails**

Run: `mvn -pl core verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='SectorSyncMergeIT'`
Expected: `SectorSyncMergeIT[profiles]` FAILS because the actual tree contains `Amara aenea`. Profile blocklists are
not applied yet; only the release config's are. Every other parameter passes.

- [ ] **Step 7: Apply the resolved blocklists in `TreeMergeHandler`**

Fields:

```java
  // the release config blocklists every merge shares, plus whatever the sector's resolved settings add to them
  private final NameBlocklist blocklist;
  private final Set<Issue> issueExclusion;
```

At the end of the constructor's field assignments, right after `this.cfg = cfg;`:

```java
    this.blocklist = new NameBlocklist(
      union(cfg == null ? null : cfg.xCfg.blockedNames, sector.getBlockedNames()),
      union(cfg == null ? null : cfg.xCfg.blockedNamePatterns, sector.getBlockedNamePatterns())
    );
    this.issueExclusion = union(cfg == null ? null : cfg.xCfg.issueExclusion, sector.getIssueExclusion());
```

Helper:

```java
  private static <T> Set<T> union(@Nullable Collection<T> a, @Nullable Collection<T> b) {
    Set<T> all = new HashSet<>();
    if (a != null) all.addAll(a);
    if (b != null) all.addAll(b);
    return all;
  }
```

In `ignoreUsage`:
- Replace `ignore = cfg != null && cfg.isBlocked(u.getName());` with `ignore = blocklist.isBlocked(u.getName());`.
- In the issue block, replace both `cfg.xCfg.issueExclusion` with `issueExclusion` and the guard
  `cfg != null && !cfg.xCfg.issueExclusion.isEmpty()` with `!issueExclusion.isEmpty()`.

Both now also apply to merge syncs run inside the project, where `cfg` is null. These fields are documented as MERGE
only; `TreeCopyHandler` (ATTACH/UNION) ignores them.

- [ ] **Step 8: Run the merge IT to verify it passes**

Run: `mvn -pl core verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='SectorSyncMergeIT'`
Expected: PASS for every parameter, including `profiles`.

If `profiles` fails, read the actual tree from the log. If the difference has nothing to do with profiles (sibling
order, the printed authorship format), fix `expected.txtree` to match and note it in `readme.md`. If `Amara aenea` or
the subspecies is present, the code is wrong.

- [ ] **Step 9: Commit**

```bash
git add core/src/main/java/life/catalogue/assembly/NameBlocklist.java core/src/main/java/life/catalogue/assembly/TreeMergeHandlerConfig.java \
  core/src/main/java/life/catalogue/assembly/TreeMergeHandler.java core/src/test/java/life/catalogue/assembly/NameBlocklistTest.java \
  core/src/test/java/life/catalogue/assembly/SectorSyncMergeIT.java core/src/test/resources/txtree/profiles
git commit -m "Merge blocklists and issue exclusions come from profiles and sectors too

They add to the release config, so a profile can block names for one publisher only."
```

---

### Task 8: Publisher sectors stop copying ranks; data migration; docs

**Files:**
- Modify: `dao/src/main/java/life/catalogue/dao/SectorDao.java:30,418-421`
- Test: `dao/src/test/java/life/catalogue/dao/SectorDaoTest.java`
- Modify: `dao/src/main/resources/life/catalogue/db/dbschema.md` (data migration in the `2026-10-05 sector profiles` section)
- Create: `docs/SECTOR-SETTINGS.md`
- Modify: `docs/XRELEASE.md:211-224`
- Modify: `docs/2026-10-05-sector-profiles.md` (`Status:` + new `## Outcome`)
- Delete: `docs/2026-10-05-sector-profiles-plan.md` (this plan, per the docs convention, once the work lands)

**Interfaces:**
- Consumes: everything above.
- Produces: `SectorDao.createMissingMergeSectorsFromPublisher(int, int, UUID, Set<Integer>)` creates sectors with no
  ranks of their own.

- [ ] **Step 1: Write the failing DAO test**

In `SectorDaoTest`:

```java
  /**
   * The "Publisher sectors" profile supplies the ranks, so a new publisher sector must not freeze a copy of them.
   */
  @Test
  public void publisherSectorsCarryNoRanks() throws Exception {
    final UUID publisher = UUID.randomUUID();
    try (SqlSession session = factory().openSession(true); var st = session.getConnection().createStatement()) {
      st.execute("UPDATE dataset SET gbif_publisher_key='" + publisher + "', attempt=1,"
        + " license=(SELECT license FROM dataset WHERE key=" + Datasets.COL + ") WHERE key=" + subjectDatasetKey);
    }
    assertEquals(1, dao.createMissingMergeSectorsFromPublisher(Datasets.COL, user, publisher, null));
    try (SqlSession session = factory().openSession()) {
      var sectors = session.getMapper(SectorMapper.class).listByDataset(Datasets.COL, subjectDatasetKey, Sector.Mode.MERGE);
      assertEquals(1, sectors.size());
      assertTrue(sectors.get(0).getRanks().isEmpty());
    }
  }
```

`DatasetMapper.keysByPublisher` selects by publisher key and `deleted IS NULL`, and the apple dataset 11 is not
deleted. The license and `attempt >= 1` are the checks `createMissingMergeSectorsFromPublisher` adds itself.

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -pl dao -am test -Dtest=SectorDaoTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL. The sector's ranks are `[GENUS, SPECIES, SUBSPECIES, VARIETY, FORM]`.

- [ ] **Step 3: Stop copying the ranks**

In `SectorDao`, delete `PUBLISHER_SECTOR_RANKS` and change the overload to:

```java
  /**
   * Creates missing merge sectors for the datasets of a publisher. They carry no settings of their own:
   * the "Publisher sectors" profile of the project provides them, see docs/SECTOR-SETTINGS.md.
   */
  public int createMissingMergeSectorsFromPublisher(int projectKey, int userKey, UUID publisherKey, @Nullable Set<Integer> datasetExclusion) {
    return createMissingMergeSectorsFromPublisher(projectKey, userKey, null, publisherKey, datasetExclusion);
  }
```

`POST /sector/createFromPublisher?ranks=` keeps passing explicit ranks through the other overload.

- [ ] **Step 4: Run it to verify it passes**

Run: `mvn -pl dao -am test -Dtest=SectorDaoTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 5: Data migration in `dbschema.md`**

Append to the `sql` block of the `2026-10-05 sector profiles` section, after the `CREATE TABLE sector_profile`:

```sql
-- the former SECTOR_* settings become a "Project defaults" profile, in every dataset holding them
INSERT INTO sector_profile (dataset_key, title, description, position, settings, created_by, modified_by)
SELECT key, 'Project defaults', 'Migrated from the former SECTOR_* dataset settings', 0,
  jsonb_strip_nulls(jsonb_build_object(
    'entities', settings -> 'sector entities',
    'nameTypes', settings -> 'sector name types',
    'nameStatusExclusion', settings -> 'sector name status exclusion',
    'copyAccordingTo', settings -> 'sector copy according to',
    'removeOrdinals', settings -> 'sector remove ordinals',
    'createImplicitNames', settings -> 'sector create implicit names'
  )), 0, 0
FROM dataset
WHERE settings ?| ARRAY['sector entities', 'sector name types', 'sector name status exclusion',
                        'sector copy according to', 'sector remove ordinals', 'sector create implicit names'];

-- merge sectors always ignored SECTOR_RANKS, so it only ever applied to the other modes
INSERT INTO sector_profile (dataset_key, title, description, position, modes, settings, created_by, modified_by)
SELECT key, 'Project ranks', 'Migrated from the former SECTOR_RANKS dataset setting', 1,
  '{ATTACH,UNION,HIERARCHY}', jsonb_build_object('ranks', settings -> 'sector ranks'), 0, 0
FROM dataset
WHERE settings ? 'sector ranks';

-- one profile supplies the ranks every publisher sector used to carry a copy of
INSERT INTO sector_profile (dataset_key, title, description, position, modes, any_sector_publisher, settings, created_by, modified_by)
SELECT DISTINCT dataset_key, 'Publisher sectors', 'Merge sectors of the datasets of all sector publishers', 2,
  '{MERGE}', TRUE, '{"ranks": ["genus", "species", "subspecies", "variety", "form"]}', 0, 0
FROM sector_publisher;

-- ... so they can drop their copies. Projects only, releases are immutable records.
UPDATE sector s SET ranks = '{}'
FROM dataset src, sector_publisher sp, dataset prj
WHERE src.key = s.subject_dataset_key
  AND sp.dataset_key = s.dataset_key AND sp.id = src.gbif_publisher_key
  AND prj.key = s.dataset_key AND prj.origin = 'PROJECT'
  AND s.mode = 'MERGE'
  AND s.ranks @> '{GENUS,SPECIES,SUBSPECIES,VARIETY,FORM}' AND s.ranks <@ '{GENUS,SPECIES,SUBSPECIES,VARIETY,FORM}';

-- the settings are profiles now. Stale keys are only logged by the new app, but clean them up.
UPDATE dataset SET settings = settings - ARRAY['sector entities', 'sector ranks', 'sector name types',
  'sector name status exclusion', 'sector copy according to', 'sector remove ordinals', 'sector create implicit names']
WHERE settings ?| ARRAY['sector entities', 'sector ranks', 'sector name types',
  'sector name status exclusion', 'sector copy according to', 'sector remove ordinals', 'sector create implicit names'];
```

Below the block, add this prose:
- **Verification:** `SELECT count(*) FROM dataset WHERE settings ?| ARRAY[...]` must return 0.
  `SELECT dataset_key, title, settings FROM sector_profile ORDER BY 1, position` lists the migrated profiles.
- **COL expectations:** COL (3) gets "Project defaults" with entities, nameTypes and `createImplicitNames=false`, and
  "Publisher sectors". About 62,332 of its sectors drop their ranks. The 17 whose publisher is no longer a sector
  publisher keep theirs.
- **Optional:** the extension-only sources (TPL, BHL, IPNI Literature, BioNames) can be grouped by hand in one
  profile through the API.

- [ ] **Step 6: Docs**

`docs/SECTOR-SETTINGS.md` (new, describes current behaviour) has these sections:
1. **What a sector setting is.** The table of the 14 settings: type, what it does, nearest or union, which modes
   honour it. `issueExclusion`, `blockedNames` and `blockedNamePatterns` apply to MERGE only; `authorshipUpdate` to
   HIERARCHY only.
2. **Levels.** Built-in defaults: per mode, with MERGE ranks FAMILY..FORM and other modes all ranks. Then profiles in
   position order, ties broken by id. Then the sector. Null and empty sets inherit.
3. **Profiles and selectors.** The six selector fields with AND/OR semantics. Publisher and type are read live from
   the subject dataset. `anySectorPublisher` follows the project's sector publishers.
4. **API.** The endpoints of Task 5, with one JSON example of a profile, e.g. the "Publisher sectors" profile, and
   one `GET /dataset/3/sector/{id}/settings` response showing `sources`.
5. **Releases.** Profiles are copied into every release, and release sectors resolve against those copies.

`docs/XRELEASE.md`:
- In the config table, change the `blockedNames` / `blockedNamePatterns` row to "Names/patterns to exclude from every
  merge; profiles and sectors can add more, see SECTOR-SETTINGS.md". Change the `issueExclusion` row the same way.

`docs/2026-10-05-sector-profiles.md`:
- Set `Status: implemented on feat/sector-profiles, not yet deployed.`
- Append `## Outcome` recording these deviations from the design:
  - **No `@JsonUnwrapped`.** A shared `SyncSettings` interface is used instead (reason in Task 1).
  - **The `ranks` column default stays `'{}'`.** The type handler writes an empty array for null anyway, and empty
    already means inherit.
  - **No resource tests.** The repo has no Jersey resource tests, so the DAOs carry them.
  - **XReleaseIT never applied its sector settings.** Its `SECTOR_*` settings were overwritten by `setupProject`,
    so they were removed without a behaviour change.
  - **Unknown dataset settings keys are now ignored.** This makes the migration order safe.
  - **`docs/SECTOR-SETTINGS.md` replaces the planned `API.md` section.** `API.md` is a three-line stub.

Delete this plan file.

- [ ] **Step 7: Full verification**

Run: `mvn -pl api,dao,core,webservice -am verify`
Expected: BUILD SUCCESS. Report the surefire/failsafe totals, not just the final line.

- [ ] **Step 8: Commit**

```bash
git add -A dao/src/main/java/life/catalogue/dao/SectorDao.java dao/src/test/java/life/catalogue/dao/SectorDaoTest.java \
  dao/src/main/resources/life/catalogue/db/dbschema.md docs/
git commit -m "Publisher sectors leave their ranks to the Publisher sectors profile; migration and docs"
```

---

## Rollout (with Markus, after review; not part of the branch)

1. **Open the checklistbank UI issue.** Draft it, and open it only after Markus OKs the text. It covers:
   - a profile list and editor under the project's sector menu, using `/dataset/{key}/sector/profile`;
   - the sector form showing inherited values and their source from `GET /dataset/{key}/sector/{id}/settings`;
   - the seven `sector …` settings disappearing from the dataset settings form.
2. **On test (full prod copy), before migrating:** dump all COL sectors and the COL settings, as on 2026-10-05:
   - `GET https://api.test.checklistbank.org/dataset/3/sector?limit=1000&offset=…`
   - `GET https://api.test.checklistbank.org/dataset/3/settings`
3. **Run the migration and deploy the branch to test.**
4. **Compare every sector that is not a publisher sector, plus 1,000 random publisher sectors.** Fetch
   `GET /dataset/3/sector/{id}/settings` and compare it with the old resolution recomputed from the step-2 dump.
   The old rules, per sector:
   - `entities`, `nameTypes`, `nameStatusExclusion`: the sector's value if non-empty, else the project setting, else
     empty. Entities that end up empty become all entity types.
   - `ranks`: the sector's value if non-empty. Otherwise MERGE gets FAMILY..FORM and other modes get the project
     `sector ranks`, else all ranks.
   - `copyAccordingTo`, `removeOrdinals`: the project setting, else false.
   - `createImplicitNames`: the project setting, else true.
   - `code`, `extinctFilter`, `nameFilter`: the sector's own value.
   - `authorshipUpdate`: the sector's value, else none.

   Expect zero differences.
5. **Sync a few representative sectors** and compare their `sector_import` metrics with the previous attempt:
   one Plazi sector, one WoRMS merge sector, the iNat vernacular-only sector and the TPL reference-only sector.
6. **Prod:** the same migration right before the deploy, with no sync or release running in between.
