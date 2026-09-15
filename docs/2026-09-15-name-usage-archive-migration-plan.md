# Name usage archive migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep every archived id at the version of its highest ranked release, archive releases with a step that is safe to run twice, refresh existing archives in place with an admin job, and stop a release from starting while a public release is not archived.

**Architecture:** A pure `ReleaseRanking` (dao) orders a project's releases into base release generations. One per release step in `NameUsageArchiver` (dao, MyBatis SQL) inserts missing rows, rewrites the rows the release is best for, copies matches, applies superseded pairs and writes the release key last. Publishing, a new `ArchiveRefreshJob` (core) and the empty-archive rebuild all use that step; `ProjectRelease.initJob` checks every public release carries its key.

**Tech Stack:** Java 25, MyBatis 3.5 XML mappers, PostgreSQL 17 (hash partitioned tables), fastutil, JUnit 4 with `PgSetupRule`/`TestDataRule`/`NameMatchingRule` (TestContainers), Dropwizard/Jersey admin resource.

**Spec:** `docs/2026-09-15-name-usage-archive-migration.md`

## Global Constraints

- Work in the worktree `/Users/markus/code/col/backend/.claude/worktrees/stable-ids` on branch `chore/col-stable-id-improvement`. Commit after every task, never push.
- Every `mvn` command assumes this ran first in the same shell: `export JAVA_HOME=$HOME/.sdkman/candidates/java/25.0.3-librca PATH=$HOME/.sdkman/candidates/java/25.0.3-librca/bin:$PATH`
- Maven runs offline (`-o`). After changing `dao`, run `mvn -o -pl dao -am install -DskipTests` before testing `core` or `webservice`, otherwise the stale jar from `~/.m2` is tested.
- A single integration test runs with `-Dit.test=<Class> -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false`.
- Code style: 2 space indent, 140 columns, match the surrounding comment density.
- In SQL touching partitioned tables (`name_usage`, `name`, `name_rel`, `reference`, `taxon_metrics`, `name_match`) the release key is a literal `${releaseKey}`, never `#{releaseKey}`: a bind parameter defeats partition pruning once pgjdbc switches to a generic plan.
- No statement ever deletes a `name_usage_archive` row. Every archive statement writes only what is missing or different.
- The per release step writes the release key last, in one statement.
- Temporary datasets have keys of 100000000 and above (`DatasetMapper.NOT_TEMP`).
- An extended release's base release comes from its job's `params.baseReleaseKey`, never from dataset notes. Fallback, verbatim from the spec: "the newest base release of the project that is not private, not a temporary dataset (key below 100000000), was not deleted before the extended release was created, and was created at least one full day before it."
- Within one generation the base release ranks above its extended releases, and a newer extended release above an older one.
- This plan file is deleted in the last task; durable facts go into the design record's `## Outcome` (CLAUDE.md documentation rules).

## File Structure

Create:
- `dao/src/main/java/life/catalogue/dao/ReleaseRanking.java` - ranks one project's releases; answers archivable, supplies, blocking keys, top.
- `dao/src/main/java/life/catalogue/dao/ArchiveStats.java` - counters of an archive run.
- `dao/src/test/java/life/catalogue/dao/ReleaseRankingTest.java` - unit test, no database.
- `dao/src/test/java/life/catalogue/dao/NameUsageArchiverIT.java` - the per release step against a fixture.
- `dao/src/test/resources/test-data/archive/*.csv` - fixture: releases 11 (deleted), 12, 13 (extended, base 12), 14 and an archive holding first versions.
- `core/src/main/java/life/catalogue/release/IgnoredReleases.java` - ignored release keys from both release configs.
- `core/src/main/java/life/catalogue/jobs/ArchiveRefreshJob.java` - the per project migration job.
- `core/src/test/java/life/catalogue/jobs/ArchiveRefreshJobIT.java`

Modify:
- `dao/src/main/java/life/catalogue/db/mapper/DatasetMapper.java` + `DatasetMapper.xml` - nested `ArchivableRelease`, `listReleasesForArchive`.
- `dao/src/main/java/life/catalogue/db/mapper/ArchivedNameUsageMapper.java` + `.xml` - the step's statements.
- `dao/src/main/java/life/catalogue/db/mapper/ArchivedNameUsageMatchMapper.java` + `.xml` - release match statements.
- `dao/src/main/java/life/catalogue/dao/NameUsageArchiver.java` - rewritten around the step.
- `dao/src/main/java/life/catalogue/matching/ArchiveMatcher.java` - per project rematch.
- `dao/src/test/java/life/catalogue/junit/TestDataRule.java` - `ARCHIVE` fixture.
- `dao/src/test/java/life/catalogue/db/mapper/{ArchivedNameUsageMapperTest,ArchivedNameUsageMatchMapperTest,DatasetMapperTest}.java`
- `core/src/main/java/life/catalogue/release/PublishReleaseListener.java`, `ProjectRelease.java`
- `core/src/test/java/life/catalogue/release/XReleaseIT.java`, `ProjectReleaseIT.java`
- `webservice/src/main/java/life/catalogue/resources/AdminResource.java`, `webservice/src/main/java/life/catalogue/command/ArchiveCmd.java`
- `dao/src/main/resources/life/catalogue/db/dbschema.md`, `CLAUDE.md`, `docs/2026-09-15-name-usage-archive-migration.md`, `docs/2026-09-15-stable-id-evidence-model.md`

---

### Task 1: Release ranking

**Files:**
- Modify: `dao/src/main/java/life/catalogue/db/mapper/DatasetMapper.java` (add nested class)
- Create: `dao/src/main/java/life/catalogue/dao/ReleaseRanking.java`
- Test: `dao/src/test/java/life/catalogue/dao/ReleaseRankingTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `DatasetMapper.ArchivableRelease` with no-arg constructor, `ArchivableRelease(int key, DatasetOrigin origin, Integer attempt, boolean privat, LocalDateTime created, @Nullable LocalDateTime deleted, @Nullable Integer baseReleaseKey)`, getters/setters `getKey/getOrigin/getAttempt/isPrivat/getCreated/getDeleted/getBaseReleaseKey`, and `boolean isArchivable()`.
  - `ReleaseRanking(int projectKey, List<ArchivableRelease> releases, IntSet ignored)` with `int getProjectKey()`, `List<ArchivableRelease> archivable()`, `boolean isArchivable(int)`, `boolean supplies(int)`, `List<Integer> blockingKeys(int)`, `boolean isTop(int)`, `@Nullable Integer baseRelease(int)`, `boolean isFallbackBase(int)`, `String describe()`. Unknown release keys throw `IllegalArgumentException`.

- [ ] **Step 1: Write the failing test**

Create `dao/src/test/java/life/catalogue/dao/ReleaseRankingTest.java`:

```java
package life.catalogue.dao;

import life.catalogue.db.mapper.DatasetMapper.ArchivableRelease;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.Test;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import static life.catalogue.api.vocab.DatasetOrigin.RELEASE;
import static life.catalogue.api.vocab.DatasetOrigin.XRELEASE;
import static org.junit.Assert.*;

public class ReleaseRankingTest {
  static final LocalDateTime JAN = LocalDateTime.of(2026, 1, 1, 10, 0);

  static ArchivableRelease base(int key, int attempt, LocalDateTime created) {
    return new ArchivableRelease(key, RELEASE, attempt, false, created, null, null);
  }

  static ArchivableRelease xr(int key, int attempt, LocalDateTime created, Integer baseKey) {
    return new ArchivableRelease(key, XRELEASE, attempt, false, created, null, baseKey);
  }

  static ReleaseRanking rank(IntSet ignored, ArchivableRelease... releases) {
    return new ReleaseRanking(3, List.of(releases), ignored);
  }

  static ReleaseRanking rank(ArchivableRelease... releases) {
    return rank(new IntOpenHashSet(), releases);
  }

  static List<Integer> keys(List<ArchivableRelease> releases) {
    return releases.stream().map(ArchivableRelease::getKey).toList();
  }

  @Test
  public void generationsNewestFirstWithBaseAboveItsExtendedReleases() {
    var r = rank(
      base(10, 1, JAN),
      xr(11, 2, JAN.plusDays(3), 10),
      base(20, 3, JAN.plusMonths(1)),
      xr(21, 4, JAN.plusMonths(1).plusDays(3), 20),
      xr(22, 5, JAN.plusMonths(1).plusDays(9), 20)
    );
    assertEquals(List.of(20, 22, 21, 10, 11), keys(r.archivable()));
    assertTrue(r.isTop(20));
    assertFalse(r.isTop(22));
  }

  @Test
  public void lateExtendedReleaseStaysInItsGeneration() {
    // the extended release of 10 was built after base release 20 existed
    var r = rank(
      base(10, 1, JAN),
      base(20, 2, JAN.plusMonths(1)),
      xr(11, 3, JAN.plusMonths(1).plusDays(5), 10)
    );
    assertEquals(List.of(20, 10, 11), keys(r.archivable()));
    assertEquals(Integer.valueOf(10), r.baseRelease(11));
    assertFalse(r.isFallbackBase(11));
  }

  @Test
  public void fallbackBaseIsTheNewestPublicBaseReleaseAtLeastOneDayOlder() {
    var created = JAN.plusMonths(2);
    var r = rank(
      base(10, 1, JAN),
      new ArchivableRelease(20, RELEASE, 2, true, JAN.plusMonths(1), null, null), // private
      new ArchivableRelease(30, RELEASE, 3, false, JAN.plusMonths(1).plusDays(5), created.minusDays(2), null), // deleted before the XR
      base(40, 4, created.minusHours(12)), // less than a full day older
      xr(50, 5, created, null)
    );
    assertEquals(Integer.valueOf(10), r.baseRelease(50));
    assertTrue(r.isFallbackBase(50));
  }

  @Test
  public void fallbackBaseMayHaveBeenDeletedSince() {
    var created = JAN.plusMonths(1);
    var r = rank(
      new ArchivableRelease(10, RELEASE, 1, false, JAN, created.plusMonths(12), null),
      xr(11, 2, created, null)
    );
    assertEquals(Integer.valueOf(10), r.baseRelease(11));
    assertFalse(r.isArchivable(10));
  }

  @Test
  public void unknownRecordedBaseFallsBack() {
    var r = rank(
      base(10, 1, JAN),
      xr(11, 2, JAN.plusDays(3), 999)
    );
    assertEquals(Integer.valueOf(10), r.baseRelease(11));
    assertTrue(r.isFallbackBase(11));
  }

  @Test
  public void privateAndDeletedReleasesAreNotArchivable() {
    var r = rank(
      base(10, 1, JAN),
      new ArchivableRelease(20, RELEASE, 2, true, JAN.plusMonths(1), null, null),
      new ArchivableRelease(30, RELEASE, 3, false, JAN.plusMonths(2), JAN.plusMonths(3), null)
    );
    assertEquals(List.of(10), keys(r.archivable()));
    assertTrue(r.isTop(10));
    assertFalse(r.supplies(20));
    assertFalse(r.supplies(30));
  }

  @Test
  public void ignoredReleasesAreArchivableButSupplyNothing() {
    var r = rank(new IntOpenHashSet(new int[]{20}),
      base(10, 1, JAN),
      base(20, 2, JAN.plusMonths(1)),
      base(30, 3, JAN.plusMonths(2))
    );
    assertEquals(List.of(30, 20, 10), keys(r.archivable()));
    assertTrue(r.isArchivable(20));
    assertFalse(r.supplies(20));
    assertTrue(r.supplies(10));
  }

  @Test
  public void blockingKeys() {
    var r = rank(new IntOpenHashSet(new int[]{20}),
      base(10, 1, JAN),
      base(20, 2, JAN.plusMonths(1)),
      base(30, 3, JAN.plusMonths(2))
    );
    // a supplying release is blocked by the supplying releases ranked above it
    assertEquals(List.of(), r.blockingKeys(30));
    assertEquals(List.of(30), r.blockingKeys(10));
    // a release supplying nothing never overwrites a version any supplying release holds
    assertEquals(List.of(30, 10), r.blockingKeys(20));
  }

  @Test(expected = IllegalArgumentException.class)
  public void unknownRelease() {
    rank(base(10, 1, JAN)).supplies(99);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -o -pl dao test -Dtest=ReleaseRankingTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL, compilation error `cannot find symbol` for `ArchivableRelease` and `ReleaseRanking`.

- [ ] **Step 3: Add `ArchivableRelease` to `DatasetMapper`**

In `dao/src/main/java/life/catalogue/db/mapper/DatasetMapper.java` add these imports where missing: `life.catalogue.api.vocab.DatasetOrigin`, `java.time.LocalDateTime`, `javax.annotation.Nullable`. Add as the last member of the interface, before its closing brace:

```java
  /**
   * A release of a project as the name usage archive ranks it, see life.catalogue.dao.ReleaseRanking.
   */
  class ArchivableRelease {
    private int key;
    private DatasetOrigin origin;
    private Integer attempt;
    private boolean privat;
    private LocalDateTime created;
    private LocalDateTime deleted;
    private Integer baseReleaseKey; // as recorded by the job that built an extended release

    public ArchivableRelease() {
    }

    public ArchivableRelease(int key, DatasetOrigin origin, Integer attempt, boolean privat, LocalDateTime created,
                             @Nullable LocalDateTime deleted, @Nullable Integer baseReleaseKey) {
      this.key = key;
      this.origin = origin;
      this.attempt = attempt;
      this.privat = privat;
      this.created = created;
      this.deleted = deleted;
      this.baseReleaseKey = baseReleaseKey;
    }

    /**
     * @return true if the release is public and not deleted, so its data exists and may be archived
     */
    public boolean isArchivable() {
      return !privat && deleted == null;
    }

    public int getKey() {
      return key;
    }

    public void setKey(int key) {
      this.key = key;
    }

    public DatasetOrigin getOrigin() {
      return origin;
    }

    public void setOrigin(DatasetOrigin origin) {
      this.origin = origin;
    }

    public Integer getAttempt() {
      return attempt;
    }

    public void setAttempt(Integer attempt) {
      this.attempt = attempt;
    }

    public boolean isPrivat() {
      return privat;
    }

    public void setPrivat(boolean privat) {
      this.privat = privat;
    }

    public LocalDateTime getCreated() {
      return created;
    }

    public void setCreated(LocalDateTime created) {
      this.created = created;
    }

    public LocalDateTime getDeleted() {
      return deleted;
    }

    public void setDeleted(LocalDateTime deleted) {
      this.deleted = deleted;
    }

    public Integer getBaseReleaseKey() {
      return baseReleaseKey;
    }

    public void setBaseReleaseKey(Integer baseReleaseKey) {
      this.baseReleaseKey = baseReleaseKey;
    }

    @Override
    public String toString() {
      return origin + " " + key;
    }
  }
```

- [ ] **Step 4: Write `ReleaseRanking`**

Create `dao/src/main/java/life/catalogue/dao/ReleaseRanking.java`:

```java
package life.catalogue.dao;

import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.db.mapper.DatasetMapper.ArchivableRelease;

import java.time.LocalDateTime;
import java.util.*;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * Ranks the releases of one project for the name usage archive, which keeps every id at the version of the highest
 * ranked release carrying it. See docs/2026-09-15-name-usage-archive-migration.md.
 *
 * An extended release belongs to the generation of the base release it was built on. Generations are ordered by their
 * base release's attempt, newest first. Within one generation the base release ranks above its extended releases, and
 * a newer extended release above an older one.
 *
 * The base release of an extended release is the one its job recorded as params.baseReleaseKey. Where that is missing
 * it is the newest base release that is not private, was not deleted before the extended release was created, and was
 * created at least one full day before it. Dataset notes are never read, other projects may write them differently.
 */
public class ReleaseRanking {
  private final int projectKey;
  private final Map<Integer, ArchivableRelease> byKey = new HashMap<>();
  private final Int2IntMap baseOf = new Int2IntOpenHashMap();
  private final IntSet fallbackBase = new IntOpenHashSet();
  private final IntSet ignored;
  private final List<ArchivableRelease> ranked;

  /**
   * @param releases every release of the project, private and deleted ones included, but no temporary datasets
   * @param ignored the release keys the project's release configs ignore
   */
  public ReleaseRanking(int projectKey, List<ArchivableRelease> releases, IntSet ignored) {
    this.projectKey = projectKey;
    this.ignored = ignored;
    releases.forEach(r -> byKey.put(r.getKey(), r));
    for (var r : releases) {
      if (r.getOrigin() == DatasetOrigin.XRELEASE) {
        var recorded = r.getBaseReleaseKey() == null ? null : byKey.get(r.getBaseReleaseKey());
        if (recorded != null && recorded.getOrigin() == DatasetOrigin.RELEASE) {
          baseOf.put(r.getKey(), recorded.getKey());
        } else {
          var fallback = fallbackBase(r, releases);
          if (fallback != null) {
            baseOf.put(r.getKey(), fallback.intValue());
            fallbackBase.add(r.getKey());
          }
        }
      }
    }
    ranked = releases.stream().sorted(this::compare).toList();
  }

  @Nullable
  private static Integer fallbackBase(ArchivableRelease xr, List<ArchivableRelease> releases) {
    if (xr.getCreated() == null) {
      return null;
    }
    final LocalDateTime latest = xr.getCreated().minusDays(1);
    return releases.stream()
      .filter(r -> r.getOrigin() == DatasetOrigin.RELEASE && !r.isPrivat() && r.getCreated() != null)
      .filter(r -> r.getDeleted() == null || r.getDeleted().isAfter(xr.getCreated()))
      .filter(r -> !r.getCreated().isAfter(latest))
      .max(Comparator.comparing(ArchivableRelease::getCreated).thenComparingInt(ArchivableRelease::getKey))
      .map(ArchivableRelease::getKey)
      .orElse(null);
  }

  /**
   * @return the attempt of the base release a release belongs to, MIN_VALUE for an extended release without any
   */
  private int generation(ArchivableRelease r) {
    if (r.getOrigin() == DatasetOrigin.XRELEASE) {
      return baseOf.containsKey(r.getKey()) ? attempt(byKey.get(baseOf.get(r.getKey()))) : Integer.MIN_VALUE;
    }
    return attempt(r);
  }

  private static int attempt(ArchivableRelease r) {
    return r.getAttempt() == null ? 0 : r.getAttempt();
  }

  /**
   * Highest rank first.
   */
  private int compare(ArchivableRelease a, ArchivableRelease b) {
    int c = Integer.compare(generation(b), generation(a));
    if (c != 0) return c;
    c = Boolean.compare(a.getOrigin() == DatasetOrigin.XRELEASE, b.getOrigin() == DatasetOrigin.XRELEASE);
    if (c != 0) return c;
    c = Integer.compare(attempt(b), attempt(a));
    if (c != 0) return c;
    return Integer.compare(b.getKey(), a.getKey());
  }

  private ArchivableRelease require(int releaseKey) {
    var r = byKey.get(releaseKey);
    if (r == null) {
      throw new IllegalArgumentException("Dataset " + releaseKey + " is no release of project " + projectKey);
    }
    return r;
  }

  public int getProjectKey() {
    return projectKey;
  }

  /**
   * @return the public, not deleted releases, highest rank first
   */
  public List<ArchivableRelease> archivable() {
    return ranked.stream().filter(ArchivableRelease::isArchivable).toList();
  }

  public boolean isArchivable(int releaseKey) {
    return require(releaseKey).isArchivable();
  }

  /**
   * @return true if the release is archivable and no release config ignores it, so its data may be an archived version
   */
  public boolean supplies(int releaseKey) {
    return require(releaseKey).isArchivable() && !ignored.contains(releaseKey);
  }

  /**
   * @return the keys of the supplying releases whose archived versions the given release must not overwrite:
   *   the ones ranked above it if it supplies versions itself, all of them if it does not
   */
  public List<Integer> blockingKeys(int releaseKey) {
    final boolean supplies = supplies(releaseKey);
    List<Integer> keys = new ArrayList<>();
    for (var r : ranked) {
      if (r.getKey() == releaseKey) {
        if (supplies) {
          break;
        }
      } else if (supplies(r.getKey())) {
        keys.add(r.getKey());
      }
    }
    return keys;
  }

  /**
   * @return true if the release is the project's highest ranked archivable release
   */
  public boolean isTop(int releaseKey) {
    require(releaseKey);
    var archivable = archivable();
    return !archivable.isEmpty() && archivable.get(0).getKey() == releaseKey;
  }

  @Nullable
  public Integer baseRelease(int xreleaseKey) {
    require(xreleaseKey);
    return baseOf.containsKey(xreleaseKey) ? baseOf.get(xreleaseKey) : null;
  }

  /**
   * @return true if the base release of the extended release was not recorded by its job and came from the fallback rule
   */
  public boolean isFallbackBase(int xreleaseKey) {
    return fallbackBase.contains(xreleaseKey);
  }

  /**
   * @return one line per release, highest rank first, for the job log
   */
  public String describe() {
    var sb = new StringBuilder();
    for (var r : ranked) {
      sb.append(r.getKey()).append(' ').append(r.getOrigin()).append(" attempt ").append(r.getAttempt());
      if (r.getOrigin() == DatasetOrigin.XRELEASE) {
        sb.append(", base ").append(baseRelease(r.getKey()));
        if (isFallbackBase(r.getKey())) {
          sb.append(" (fallback)");
        }
      }
      if (!r.isArchivable()) {
        sb.append(r.isPrivat() ? ", private" : ", deleted");
      } else if (!supplies(r.getKey())) {
        sb.append(", ignored");
      }
      sb.append('\n');
    }
    return sb.toString();
  }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -o -pl dao test -Dtest=ReleaseRankingTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, `Tests run: 9, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add dao/src/main/java/life/catalogue/db/mapper/DatasetMapper.java dao/src/main/java/life/catalogue/dao/ReleaseRanking.java dao/src/test/java/life/catalogue/dao/ReleaseRankingTest.java
git commit -m "feat(archive): rank a project's releases into base release generations"
```

---

### Task 2: The per release archive step

**Files:**
- Create: `dao/src/test/resources/test-data/archive/` (12 csv files, Step 1)
- Modify: `dao/src/test/java/life/catalogue/junit/TestDataRule.java`
- Test: `dao/src/test/java/life/catalogue/dao/NameUsageArchiverIT.java`
- Modify: `dao/src/main/java/life/catalogue/db/mapper/DatasetMapper.java`, `dao/src/main/resources/life/catalogue/db/mapper/DatasetMapper.xml`
- Modify: `dao/src/main/java/life/catalogue/db/mapper/ArchivedNameUsageMapper.java`, `dao/src/main/resources/life/catalogue/db/mapper/ArchivedNameUsageMapper.xml`
- Modify: `dao/src/main/java/life/catalogue/db/mapper/ArchivedNameUsageMatchMapper.java`, `dao/src/main/resources/life/catalogue/db/mapper/ArchivedNameUsageMatchMapper.xml`
- Create: `dao/src/main/java/life/catalogue/dao/ArchiveStats.java`
- Modify (rewrite): `dao/src/main/java/life/catalogue/dao/NameUsageArchiver.java`
- Modify: `dao/src/test/java/life/catalogue/db/mapper/ArchivedNameUsageMapperTest.java`, `ArchivedNameUsageMatchMapperTest.java`, `DatasetMapperTest.java`
- Modify: `core/src/main/java/life/catalogue/release/PublishReleaseListener.java`, `core/src/test/java/life/catalogue/release/XReleaseIT.java`

**Interfaces:**
- Consumes: `ReleaseRanking`, `DatasetMapper.ArchivableRelease` from Task 1.
- Produces:
  - `DatasetMapper.listReleasesForArchive(int projectKey): List<ArchivableRelease>` - all releases of the project incl. private and deleted, no temp datasets, ordered by attempt.
  - `ArchiveStats` public int fields `releases, inserted, rewritten, renamed, keysAdded, matchesCopied, matchesDeleted, supersededApplied, supersededCleared`; `void add(ArchiveStats)`; `boolean isUnchanged()`; `toString()` starting with `"<n> releases"`.
  - `NameUsageArchiver(SqlSessionFactory)`, `NameUsageArchiver(SqlSessionFactory, IntFunction<IntSet> ignoredReleases)`, `ReleaseRanking ranking(int projectKey)`, `void rebuildAll(boolean truncate)`, `ArchiveStats rebuildProject(int projectKey, boolean copyMatches)`, `ArchiveStats archiveProject(ReleaseRanking, boolean copyMatches, boolean dryRun)`, `ArchiveStats archiveRelease(int releaseKey)`, `ArchiveStats archiveRelease(ReleaseRanking, int releaseKey, boolean copyMatches, boolean dryRun)`, `List<Integer> unarchivedReleases(int projectKey)`.
  - `TestDataRule.ARCHIVE` and `TestDataRule.archive()`.
  - `ArchivedNameUsageMatchMapper.copyReleaseMatches(int, int, List<Integer>)`, `deleteUnmatchedReleaseMatches(int, int, List<Integer>)`; `createMissingMatches`, `refreshMatches` and `createAllMatches` are removed.

- [ ] **Step 1: Create the fixture**

Create these files under `dao/src/test/resources/test-data/archive/`. Dataset 3 (COL) is seeded by `data.sql` and must not be listed.

`dataset.csv`:
```
key,source_key,type,title,origin,attempt,created_by,modified_by,created,private,deleted
11,3,TAXONOMIC,Base release 1,RELEASE,1,100,100,2020-01-01 00:00:00,f,2021-01-01 00:00:00
12,3,TAXONOMIC,Base release 2,RELEASE,2,100,100,2020-02-01 00:00:00,f,
13,3,TAXONOMIC,Extended release 2,XRELEASE,3,100,100,2020-02-05 00:00:00,f,
14,3,TAXONOMIC,Base release 3,RELEASE,4,100,100,2020-03-01 00:00:00,f,
```

`job.csv`:
```
key,job_class,lane,status,priority,dataset_key,created_by,created,params
a3333333-3333-3333-3333-000000000001,ProjectRelease,DEFAULT,FINISHED,LOW,3,100,2020-01-01 00:00:00,"{""projectKey"": 3, ""newDatasetKey"": 11}"
a3333333-3333-3333-3333-000000000002,ProjectRelease,DEFAULT,FINISHED,LOW,3,100,2020-02-01 00:00:00,"{""projectKey"": 3, ""newDatasetKey"": 12}"
a3333333-3333-3333-3333-000000000003,XRelease,DEFAULT,FINISHED,LOW,3,100,2020-02-05 00:00:00,"{""projectKey"": 3, ""baseReleaseKey"": 12, ""newDatasetKey"": 13}"
a3333333-3333-3333-3333-000000000004,ProjectRelease,DEFAULT,FINISHED,LOW,3,100,2020-03-01 00:00:00,"{""projectKey"": 3, ""newDatasetKey"": 14}"
```

`dataset_import.csv`:
```
dataset_key,attempt,job_key,origin,created_by
3,1,a3333333-3333-3333-3333-000000000001,PROJECT,100
3,2,a3333333-3333-3333-3333-000000000002,PROJECT,100
3,3,a3333333-3333-3333-3333-000000000003,PROJECT,100
3,4,a3333333-3333-3333-3333-000000000004,PROJECT,100
```

`names_index.csv`:
```
id,scientific_name,normalized
1,Abies alba,abies alb
2,Picea abies,picea abi
3,Larix decidua,larix decidu
4,Pinus nigra,pinus nigr
5,Pinus mugo,pinus mug
6,Cedrus libani,cedrus liban
```

`name_match.csv`:
```
dataset_key,name_id,index_id
12,na,1
12,nc,2
12,ne,4
13,na,1
13,nc,2
13,nd,3
13,ne,4
14,na,1
14,ne,5
```

`name_usage_archive.csv` (first versions; 13 and 14 were never archived):
```
id,n_id,dataset_key,n_rank,n_scientific_name,n_authorship,status,release_keys
A,na,3,SPECIES,Abies alba,Mill.,ACCEPTED,"{11,12}"
B,nb,3,SPECIES,Cedrus libani,A.Rich.,ACCEPTED,{11}
C,nc,3,SPECIES,Picea abies,(L.) H.Karst.,ACCEPTED,{12}
E,ne,3,SPECIES,Pinus nigra,J.F.Arnold,ACCEPTED,{12}
```

`name_12.csv`:
```
id,rank,scientific_name,authorship
na,SPECIES,Abies alba,Mill.
nc,SPECIES,Picea abies,(L.) H.Karst.
ne,SPECIES,Pinus nigra,J.F.Arnold
```

`name_usage_12.csv`:
```
id,name_id,status
A,na,ACCEPTED
C,nc,ACCEPTED
E,ne,ACCEPTED
```

`name_13.csv`:
```
id,rank,scientific_name,authorship
na,SPECIES,Abies alba,Mill.
nc,SPECIES,Picea abies,(L.) H.Karst.
nd,SPECIES,Larix decidua,Mill.
ne,SPECIES,Pinus nigra,J.F.Arnold
```

`name_usage_13.csv`:
```
id,name_id,status
A,na,ACCEPTED
C,nc,PROVISIONALLY_ACCEPTED
D,nd,ACCEPTED
E,ne,ACCEPTED
```

`name_14.csv`:
```
id,rank,scientific_name,authorship
na,SPECIES,Abies alba,Miller
ne,SPECIES,Pinus mugo,Turra
```

`name_usage_14.csv`:
```
id,name_id,status
A,na,ACCEPTED
E,ne,ACCEPTED
```

- [ ] **Step 2: Register the fixture in `TestDataRule`**

In `dao/src/test/java/life/catalogue/junit/TestDataRule.java` add below `COL_SYNCED`:

```java
  /**
   * A project with base releases 11 (deleted), 12 and 14, extended release 13 built on 12, and an archive that still
   * holds first versions and never got 13 or 14. See NameUsageArchiverIT.
   */
  public final static TestData ARCHIVE = new TestData("archive", 3,
    Map.of("name", Map.of("type", NameType.SCIENTIFIC)), Set.of(3, 12, 13, 14), false);
```

and below `colSynced()`:

```java
  public static TestDataRule archive() {
    return new TestDataRule(ARCHIVE);
  }
```

- [ ] **Step 3: Write the failing integration test**

Create `dao/src/test/java/life/catalogue/dao/NameUsageArchiverIT.java`:

```java
package life.catalogue.dao;

import life.catalogue.api.model.ArchivedNameUsage;
import life.catalogue.api.model.DSID;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.db.mapper.ArchivedNameUsageMapper;
import life.catalogue.db.mapper.ArchivedNameUsageMatchMapper;
import life.catalogue.db.mapper.DatasetMapper.ArchivableRelease;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The archive fixture ranks 14, 12, 13 - base release 12 above its extended release 13 - and 11 is deleted:
 * <ul>
 *   <li>A Abies alba: in 11, 12, 13, 14, authorship corrected to Miller in 14</li>
 *   <li>B Cedrus libani: only in the deleted 11</li>
 *   <li>C Picea abies: in 12 and 13, provisionally accepted in 13 only, dropped from 14</li>
 *   <li>D Larix decidua: only in the extended release 13, missing from the archive</li>
 *   <li>E Pinus nigra: in 12 and 13, renamed to Pinus mugo in 14</li>
 * </ul>
 */
public class NameUsageArchiverIT {
  static final int PROJECT = Datasets.COL;

  @ClassRule
  public static SqlSessionFactoryRule pgSetupRule = new PgSetupRule();

  @Rule
  public final TestDataRule testDataRule = TestDataRule.archive();

  SqlSessionFactory factory;
  NameUsageArchiver archiver;

  @Before
  public void init() {
    factory = SqlSessionFactoryRule.getSqlSessionFactory();
    archiver = new NameUsageArchiver(factory);
    DatasetInfoCache.CACHE.clear();
    // the matches of the old archive, following the names its rows hold
    try (SqlSession session = factory.openSession(true)) {
      var amm = session.getMapper(ArchivedNameUsageMatchMapper.class);
      amm.persist(DSID.of(PROJECT, "A"), null, 1);
      amm.persist(DSID.of(PROJECT, "B"), null, 6);
      amm.persist(DSID.of(PROJECT, "C"), null, 2);
      amm.persist(DSID.of(PROJECT, "E"), null, 4);
    }
  }

  ArchivedNameUsage get(String id) {
    try (SqlSession session = factory.openSession(true)) {
      return session.getMapper(ArchivedNameUsageMapper.class).get(DSID.of(PROJECT, id));
    }
  }

  Integer nidx(String id) {
    try (SqlSession session = factory.openSession(true)) {
      var m = session.getMapper(ArchivedNameUsageMatchMapper.class).get(DSID.of(PROJECT, id));
      return m == null ? null : m.getNidx();
    }
  }

  String sql(String query) throws Exception {
    try (SqlSession session = factory.openSession(true);
         Statement st = session.getConnection().createStatement();
         ResultSet rs = st.executeQuery(query)
    ) {
      return rs.next() ? rs.getString(1) : null;
    }
  }

  void assertNewestGenerations() {
    var a = get("A");
    assertEquals("Miller", a.getName().getAuthorship());
    assertArrayEquals(new int[]{11, 12, 13, 14}, a.getReleaseKeys());
    assertEquals(Integer.valueOf(1), nidx("A"));

    var b = get("B");
    assertEquals("A.Rich.", b.getName().getAuthorship());
    assertArrayEquals(new int[]{11}, b.getReleaseKeys());
    assertEquals(Integer.valueOf(6), nidx("B"));

    // base release 12 outranks its extended release 13
    var c = get("C");
    assertEquals(TaxonomicStatus.ACCEPTED, c.getStatus());
    assertArrayEquals(new int[]{12, 13}, c.getReleaseKeys());

    var d = get("D");
    assertEquals("Larix decidua", d.getName().getScientificName());
    assertArrayEquals(new int[]{13}, d.getReleaseKeys());
    assertEquals(Integer.valueOf(3), nidx("D"));

    var e = get("E");
    assertEquals("Pinus mugo", e.getName().getScientificName());
    assertArrayEquals(new int[]{12, 13, 14}, e.getReleaseKeys());
    assertEquals(Integer.valueOf(5), nidx("E"));
  }

  @Test
  public void ranking() {
    var ranking = archiver.ranking(PROJECT);
    assertEquals(List.of(14, 12, 13), ranking.archivable().stream().map(ArchivableRelease::getKey).toList());
    assertEquals(Integer.valueOf(12), ranking.baseRelease(13));
    assertFalse(ranking.isFallbackBase(13));
    assertFalse(ranking.isArchivable(11));
  }

  @Test
  public void archiveProject() {
    assertEquals(List.of(13, 14), archiver.unarchivedReleases(PROJECT));

    var stats = archiver.archiveProject(archiver.ranking(PROJECT), true, false);
    assertEquals(3, stats.releases);
    assertEquals(1, stats.inserted);
    assertNewestGenerations();
    assertTrue(archiver.unarchivedReleases(PROJECT).isEmpty());

    var again = archiver.archiveProject(archiver.ranking(PROJECT), true, false);
    assertTrue("a rerun wrote " + again, again.isUnchanged());
  }

  @Test
  public void publishedInOrder() {
    for (int key : new int[]{12, 13, 14}) {
      archiver.archiveRelease(key);
    }
    assertNewestGenerations();
  }

  @Test
  public void extendedReleasePublishedAfterTheNextBaseRelease() {
    for (int key : new int[]{12, 14, 13}) {
      archiver.archiveRelease(key);
    }
    assertNewestGenerations();
  }

  @Test
  public void supersededOnlyFromTheHighestRankedRelease() throws Exception {
    try (SqlSession session = factory.openSession(true)) {
      var anum = session.getMapper(ArchivedNameUsageMapper.class);
      anum.addSuperseded(14, "C", "A"); // the newest release dropped C in favour of A
      anum.addSuperseded(12, "B", "A"); // an older release's staging is stale
    }
    archiver.archiveProject(archiver.ranking(PROJECT), true, false);
    assertEquals("A", sql("SELECT superseded_by FROM name_usage_archive WHERE dataset_key=3 AND id='C'"));
    assertNull(sql("SELECT superseded_by FROM name_usage_archive WHERE dataset_key=3 AND id='B'"));
    assertEquals("0", sql("SELECT count(*) FROM usage_id_superseded"));
  }

  @Test
  public void dryRunWritesNothing() {
    var stats = archiver.archiveProject(archiver.ranking(PROJECT), true, true);
    assertEquals(1, stats.inserted);
    assertEquals(1, stats.renamed);
    assertTrue(stats.rewritten > 0);
    assertNull(get("D"));
    assertEquals("Mill.", get("A").getName().getAuthorship());
    assertEquals(List.of(13, 14), archiver.unarchivedReleases(PROJECT));
  }
}
```

- [ ] **Step 4: Run it to verify it fails**

Run: `mvn -o -pl dao verify -Dit.test=NameUsageArchiverIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false`
Expected: FAIL, compilation errors for `ranking`, `archiveProject`, `unarchivedReleases`, `archiveRelease(int)`.

- [ ] **Step 5: Add `listReleasesForArchive`**

In `DatasetMapper.java`, right after `listReleasesQuick`:

```java
  /**
   * Lists every release of a project for the name usage archive, private and deleted ones included, but no temporary
   * datasets, ordered by attempt. An extended release carries the base release key its job recorded, if any.
   */
  List<ArchivableRelease> listReleasesForArchive(@Param("projectKey") int projectKey);
```

In `DatasetMapper.xml`, right after `<select id="listReleasesQuick" ...>...</select>`:

```xml
  <select id="listReleasesForArchive" resultType="life.catalogue.db.mapper.DatasetMapper$ArchivableRelease">
    SELECT d.key, d.origin, d.attempt, d.private AS privat, d.created, d.deleted,
      CASE WHEN d.origin = 'XRELEASE'::DATASETORIGIN THEN (j.params->>'baseReleaseKey')::int END AS base_release_key
    FROM dataset d
      LEFT JOIN dataset_import di ON di.dataset_key=d.source_key AND di.attempt=d.attempt
      LEFT JOIN job j ON j.key=di.job_key
    WHERE d.source_key=#{projectKey}
      AND <include refid="IS_RELEASE"/>
    ORDER BY d.attempt, d.key
  </select>
```

- [ ] **Step 6: Replace the archive statements in `ArchivedNameUsageMapper.xml`**

Add right after the closing `</sql>` of `<sql id="COLS">`:

```xml
  <!-- The columns that make up the archived version of an id, paired by position with VERSION_VALUES.
       Include with property alias "" for insert and SET target lists, and "a." to compare. -->
  <sql id="VERSION_COLS">
    ${alias}n_id, ${alias}n_rank, ${alias}n_candidatus, ${alias}n_notho, ${alias}n_code, ${alias}n_nom_status,
    ${alias}n_original_spelling, ${alias}n_gender_agreement, ${alias}n_gender, ${alias}n_origin, ${alias}n_type,
    ${alias}n_scientific_name, ${alias}n_authorship, ${alias}n_uninomial, ${alias}n_genus, ${alias}n_infrageneric_epithet,
    ${alias}n_specific_epithet, ${alias}n_infraspecific_epithet, ${alias}n_cultivar_epithet,
    ${alias}n_basionym_authors, ${alias}n_basionym_ex_authors, ${alias}n_basionym_year,
    ${alias}n_combination_authors, ${alias}n_combination_ex_authors, ${alias}n_combination_year,
    ${alias}n_sanctioning_author, ${alias}n_published_in_id, ${alias}n_published_in_page, ${alias}n_nomenclatural_note,
    ${alias}n_unparsed, ${alias}n_identifier, ${alias}n_etymology, ${alias}n_link, ${alias}n_remarks,
    ${alias}extinct, ${alias}status, ${alias}origin, ${alias}parent_id, ${alias}name_phrase, ${alias}identifier,
    ${alias}link, ${alias}remarks,
    ${alias}according_to, ${alias}basionym, ${alias}accepted, ${alias}classification, ${alias}published_in
  </sql>

  <sql id="VERSION_VALUES">
    n.id, n.rank, n.candidatus, n.notho, n.code, n.nom_status,
    n.original_spelling, n.gender_agreement, n.gender, n.origin, n.type,
    n.scientific_name, n.authorship, n.uninomial, n.genus, n.infrageneric_epithet,
    n.specific_epithet, n.infraspecific_epithet, n.cultivar_epithet,
    n.basionym_authors, n.basionym_ex_authors, n.basionym_year,
    n.combination_authors, n.combination_ex_authors, n.combination_year,
    n.sanctioning_author, n.published_in_id, n.published_in_page, n.nomenclatural_note,
    n.unparsed, n.identifier, n.etymology, n.link, n.remarks,
    u.extinct, u.status, u.origin, u.parent_id, u.name_phrase, u.identifier,
    u.link, u.remarks,
    ra.citation, bas.sn,
    CASE WHEN is_synonym(u.status) THEN (up.id,np.rank,np.scientific_name,np.authorship)::simple_name END,
    CASE WHEN is_synonym(u.status) THEN mp.classification ELSE m.classification END,
    rp.citation
  </sql>

  <!-- A name can have several basionym relations. A plain join would yield one row per relation and an UPDATE applies
       an arbitrary one, so the "only rewrite what differs" comparison would write on every run: pick one, ordered. -->
  <sql id="VERSION_FROM">
    name_usage u
      JOIN name n ON n.dataset_key=u.dataset_key AND n.id=u.name_id
      LEFT JOIN reference ra ON ra.dataset_key=u.dataset_key AND ra.id=u.according_to_id
      LEFT JOIN LATERAL (
        SELECT (bn.id,bn.rank,bn.scientific_name,bn.authorship)::simple_name AS sn
        FROM name_rel br JOIN name bn ON bn.dataset_key=br.dataset_key AND bn.id=br.related_name_id
        WHERE br.dataset_key=u.dataset_key AND br.name_id=n.id AND br.type='BASIONYM'::nomreltype
        ORDER BY bn.id
        LIMIT 1
      ) bas ON true
      LEFT JOIN taxon_metrics m ON m.dataset_key=u.dataset_key AND m.taxon_id=u.id
      LEFT JOIN taxon_metrics mp ON mp.dataset_key=u.dataset_key AND mp.taxon_id=u.parent_id
      LEFT JOIN name_usage up ON up.dataset_key=u.dataset_key AND up.id=u.parent_id
      LEFT JOIN name np ON np.dataset_key=u.dataset_key AND np.id=up.name_id
      LEFT JOIN reference rp ON rp.dataset_key=u.dataset_key AND rp.id=n.published_in_id
  </sql>

  <!-- the keys of the releases whose archived versions a statement must not overwrite, see ReleaseRanking.blockingKeys -->
  <sql id="BLOCKING">ARRAY[<foreach collection="blocking" item="k" separator=",">${k}</foreach>]::int[]</sql>
```

Delete the three elements `<update id="addReleaseKey">`, `<update id="updateExistingUsages">` and `<insert id="createMissingUsages">` and put in their place:

```xml
  <insert id="createMissingUsages" parameterType="map">
    INSERT INTO name_usage_archive (id, dataset_key, release_keys, <include refid="VERSION_COLS"><property name="alias" value=""/></include>)
    SELECT u.id, #{projectKey}, '{}'::int[], <include refid="VERSION_VALUES"/>
    FROM <include refid="VERSION_FROM"/>
    WHERE u.dataset_key=${releaseKey}
      AND NOT EXISTS (SELECT true FROM name_usage_archive a WHERE a.dataset_key=#{projectKey} AND a.id=u.id)
    ON CONFLICT (dataset_key, id) DO NOTHING
  </insert>

  <update id="updateExistingUsages" parameterType="map">
    UPDATE name_usage_archive a
    SET (<include refid="VERSION_COLS"><property name="alias" value=""/></include>) = (<include refid="VERSION_VALUES"/>)
    FROM <include refid="VERSION_FROM"/>
    WHERE a.dataset_key=#{projectKey}
      AND u.dataset_key=${releaseKey}
      AND a.id=u.id
      AND NOT (a.release_keys &amp;&amp; <include refid="BLOCKING"/>)
      AND (<include refid="VERSION_COLS"><property name="alias" value="a."/></include>) IS DISTINCT FROM (<include refid="VERSION_VALUES"/>)
  </update>

  <update id="addReleaseKey" parameterType="map">
    UPDATE name_usage_archive a
    SET release_keys = (SELECT array_agg(DISTINCT k ORDER BY k) FROM unnest(array_append(a.release_keys, ${releaseKey})) AS k)
    FROM name_usage u
    WHERE a.dataset_key=#{projectKey}
      AND u.dataset_key=${releaseKey}
      AND a.id=u.id
      AND NOT (a.release_keys @&gt; ARRAY[${releaseKey}])
  </update>

  <update id="tidyReleaseKeys" parameterType="map">
    UPDATE name_usage_archive a
    SET release_keys = t.keys
    FROM (
      SELECT x.id, coalesce((SELECT array_agg(DISTINCT k ORDER BY k) FROM unnest(x.release_keys) AS k), '{}'::int[]) AS keys
      FROM name_usage_archive x
      WHERE x.dataset_key=#{projectKey}
    ) t
    WHERE a.dataset_key=#{projectKey}
      AND a.id=t.id
      AND a.release_keys IS DISTINCT FROM t.keys
  </update>

  <select id="countMissingUsages" resultType="integer">
    SELECT count(*)
    FROM name_usage u
    WHERE u.dataset_key=${releaseKey}
      AND NOT EXISTS (SELECT true FROM name_usage_archive a WHERE a.dataset_key=#{projectKey} AND a.id=u.id)
  </select>

  <select id="countOutdatedUsages" resultType="integer">
    SELECT count(*)
    FROM name_usage_archive a, <include refid="VERSION_FROM"/>
    WHERE a.dataset_key=#{projectKey}
      AND u.dataset_key=${releaseKey}
      AND a.id=u.id
      AND NOT (a.release_keys &amp;&amp; <include refid="BLOCKING"/>)
      AND (<include refid="VERSION_COLS"><property name="alias" value="a."/></include>) IS DISTINCT FROM (<include refid="VERSION_VALUES"/>)
      <if test="renamedOnly">AND a.n_scientific_name IS DISTINCT FROM n.scientific_name</if>
  </select>

  <select id="countMissingReleaseKeys" resultType="integer">
    SELECT count(*)
    FROM name_usage_archive a
      JOIN name_usage u ON u.dataset_key=${releaseKey} AND u.id=a.id
    WHERE a.dataset_key=#{projectKey}
      AND NOT (a.release_keys @&gt; ARRAY[${releaseKey}])
  </select>

  <select id="isReleaseArchived" resultType="boolean">
    SELECT EXISTS (
      SELECT true FROM name_usage_archive WHERE dataset_key=#{projectKey} AND release_keys @&gt; ARRAY[${releaseKey}]
    )
  </select>

  <select id="hasUsages" resultType="boolean">
    SELECT EXISTS (SELECT true FROM name_usage WHERE dataset_key=${releaseKey})
  </select>
```

Replace `<update id="clearSuperseded">` with:

```xml
  <update id="clearSuperseded" parameterType="map">
    UPDATE name_usage_archive a SET superseded_by = null
    FROM name_usage u
    WHERE a.dataset_key=#{projectKey}
      AND u.dataset_key=${releaseKey}
      AND a.id=u.id
      AND a.superseded_by IS NOT NULL
  </update>
```

- [ ] **Step 7: Update `ArchivedNameUsageMapper.java`**

Delete the declarations (with their javadoc) of `addReleaseKey`, `updateExistingUsages` and `createMissingUsages`, and put in their place:

```java
  /**
   * Inserts the archive records missing for the usages of a release, with empty release keys: addReleaseKey adds the
   * key last. Safe to run twice, also at the same time.
   * @return number of inserted archive records
   */
  int createMissingUsages(@Param("projectKey") int projectKey, @Param("releaseKey") int releaseKey);

  /**
   * Rewrites the archived version of the usages of a release wherever any archived column differs, skipping records
   * that carry one of the blocking release keys, i.e. whose version a higher ranked release holds. See ReleaseRanking.
   * @return number of rewritten archive records
   */
  int updateExistingUsages(@Param("projectKey") int projectKey, @Param("releaseKey") int releaseKey,
                           @Param("blocking") List<Integer> blocking);

  /**
   * Adds the release key, keeping the array sorted, to every archive record of a usage of the release that lacks it.
   * The per release step runs this last, so a release key present in the archive means the release was archived completely.
   * @return number of archive records the key was added to
   */
  int addReleaseKey(@Param("projectKey") int projectKey, @Param("releaseKey") int releaseKey);

  /**
   * Sorts and de-duplicates the release keys of every archive record of a project.
   * @return number of changed archive records
   */
  int tidyReleaseKeys(@Param("projectKey") int projectKey);

  /**
   * Dry run counterpart of createMissingUsages.
   */
  int countMissingUsages(@Param("projectKey") int projectKey, @Param("releaseKey") int releaseKey);

  /**
   * Dry run counterpart of updateExistingUsages.
   * @param renamedOnly if true only counts records whose scientific name would change
   */
  int countOutdatedUsages(@Param("projectKey") int projectKey, @Param("releaseKey") int releaseKey,
                          @Param("blocking") List<Integer> blocking, @Param("renamedOnly") boolean renamedOnly);

  /**
   * Dry run counterpart of addReleaseKey, counting existing archive records only.
   */
  int countMissingReleaseKeys(@Param("projectKey") int projectKey, @Param("releaseKey") int releaseKey);

  /**
   * @return true if any archive record of the project carries the release key
   */
  boolean isReleaseArchived(@Param("projectKey") int projectKey, @Param("releaseKey") int releaseKey);

  /**
   * @return true if the release has any name usage
   */
  boolean hasUsages(@Param("releaseKey") int releaseKey);
```

- [ ] **Step 8: Replace the match statements**

In `ArchivedNameUsageMatchMapper.xml` delete `<update id="refreshMatches">`, `<insert id="createMissingMatches">` and `<insert id="createAllMatches">`, and add:

```xml
  <insert id="copyReleaseMatches" parameterType="map">
    INSERT INTO name_usage_archive_match (dataset_key, index_id, usage_id)
    SELECT #{projectKey}, nm.index_id, u.id
    FROM name_usage u
      JOIN name_match nm ON nm.dataset_key=u.dataset_key AND nm.name_id=u.name_id
      JOIN name_usage_archive a ON a.dataset_key=#{projectKey} AND a.id=u.id
    WHERE u.dataset_key=${releaseKey}
      AND NOT (a.release_keys &amp;&amp; <include refid="life.catalogue.db.mapper.ArchivedNameUsageMapper.BLOCKING"/>)
    ON CONFLICT (dataset_key, usage_id) DO UPDATE SET index_id = EXCLUDED.index_id
      WHERE name_usage_archive_match.index_id IS DISTINCT FROM EXCLUDED.index_id
  </insert>

  <delete id="deleteUnmatchedReleaseMatches" parameterType="map">
    DELETE FROM name_usage_archive_match am
    USING name_usage u, name_usage_archive a
    WHERE am.dataset_key=#{projectKey}
      AND u.dataset_key=${releaseKey} AND u.id=am.usage_id
      AND a.dataset_key=#{projectKey} AND a.id=am.usage_id
      AND NOT (a.release_keys &amp;&amp; <include refid="life.catalogue.db.mapper.ArchivedNameUsageMapper.BLOCKING"/>)
      AND NOT EXISTS (SELECT true FROM name_match nm WHERE nm.dataset_key=${releaseKey} AND nm.name_id=u.name_id)
  </delete>
```

In `ArchivedNameUsageMatchMapper.java` add `import java.util.List;`, delete the declarations of `createMissingMatches`, `refreshMatches` and `createAllMatches` with their javadoc, and add:

```java
  /**
   * Copies the names index matches of a release's usages into the archive for the archive records no blocking release
   * holds, only where they differ. See NameUsageArchiver.
   * @return number of inserted or changed archive matches
   */
  int copyReleaseMatches(@Param("projectKey") int projectKey, @Param("releaseKey") int releaseKey,
                         @Param("blocking") List<Integer> blocking);

  /**
   * Removes the archive match of the release usages whose name has no match in the release, for the archive records no
   * blocking release holds: an empty match is never stored.
   * @return number of removed archive matches
   */
  int deleteUnmatchedReleaseMatches(@Param("projectKey") int projectKey, @Param("releaseKey") int releaseKey,
                                    @Param("blocking") List<Integer> blocking);
```

- [ ] **Step 9: Create `ArchiveStats`**

Create `dao/src/main/java/life/catalogue/dao/ArchiveStats.java`:

```java
package life.catalogue.dao;

/**
 * What archiving releases wrote into the name usage archive, or in a dry run would write. See NameUsageArchiver.
 */
public class ArchiveStats {
  public int releases;
  public int inserted;
  public int rewritten;
  /**
   * Dry runs only: rewritten records whose scientific name changes, i.e. the ones a rematch has work with.
   */
  public int renamed;
  public int keysAdded;
  public int matchesCopied;
  public int matchesDeleted;
  public int supersededApplied;
  public int supersededCleared;

  public void add(ArchiveStats other) {
    releases += other.releases;
    inserted += other.inserted;
    rewritten += other.rewritten;
    renamed += other.renamed;
    keysAdded += other.keysAdded;
    matchesCopied += other.matchesCopied;
    matchesDeleted += other.matchesDeleted;
    supersededApplied += other.supersededApplied;
    supersededCleared += other.supersededCleared;
  }

  /**
   * @return true if nothing was, or would be, written
   */
  public boolean isUnchanged() {
    return inserted + rewritten + keysAdded + matchesCopied + matchesDeleted + supersededApplied + supersededCleared == 0;
  }

  @Override
  public String toString() {
    return String.format("%d releases, %d records inserted, %d rewritten (%d renamed), %d release keys added, "
        + "%d matches copied and %d removed, %d superseded ids recorded and %d cleared",
      releases, inserted, rewritten, renamed, keysAdded, matchesCopied, matchesDeleted, supersededApplied, supersededCleared);
  }
}
```

- [ ] **Step 10: Rewrite `NameUsageArchiver`**

Replace the whole content of `dao/src/main/java/life/catalogue/dao/NameUsageArchiver.java` with:

```java
package life.catalogue.dao;

import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.mapper.ArchivedNameUsageMapper;
import life.catalogue.db.mapper.ArchivedNameUsageMatchMapper;
import life.catalogue.db.mapper.DatasetMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * Builds and maintains the name usage archive of projects: one record per id any public release of the project
 * carried, holding the version of the highest ranked release that carries it, see {@link ReleaseRanking} and
 * docs/2026-09-15-name-usage-archive-migration.md.
 *
 * Everything goes through one per release step, {@link #archiveRelease(ReleaseRanking, int, boolean, boolean)}, used by
 * publishing, by the ArchiveRefreshJob and by building an empty archive. It only writes what is missing or different, so
 * running it twice, even at the same time, writes nothing the second time - both apps of a blue-green deploy receive the
 * publish event. It writes the release key last and in one statement, so a release whose key appears in the archive has
 * been archived completely, which {@link #unarchivedReleases(int)} relies on.
 *
 * Archive records are never deleted: an id only deleted releases carried could never be found again otherwise.
 */
public class NameUsageArchiver {
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageArchiver.class);
  private final SqlSessionFactory factory;
  private final IntFunction<IntSet> ignoredReleases;

  /**
   * An archiver that ignores no release, e.g. for tests.
   */
  public NameUsageArchiver(SqlSessionFactory factory) {
    this(factory, projectKey -> new IntOpenHashSet());
  }

  /**
   * @param ignoredReleases the release keys a project's release configs ignore, by project key
   */
  public NameUsageArchiver(SqlSessionFactory factory, IntFunction<IntSet> ignoredReleases) {
    this.factory = factory;
    this.ignoredReleases = ignoredReleases;
  }

  public ReleaseRanking ranking(int projectKey) {
    try (SqlSession session = factory.openSession(true)) {
      var releases = session.getMapper(DatasetMapper.class).listReleasesForArchive(projectKey);
      return new ReleaseRanking(projectKey, releases, ignoredReleases.apply(projectKey));
    }
  }

  /**
   * Builds the archive of every project from its releases.
   * @param truncate if true deletes the entire archive first, which loses every id only deleted releases carried
   */
  public void rebuildAll(boolean truncate) {
    List<Integer> projects;
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      var req = new DatasetSearchRequest();
      req.setOrigin(List.of(DatasetOrigin.PROJECT));
      req.setSortBy(DatasetSearchRequest.SortBy.KEY);
      projects = dm.searchKeys(req, Users.SUPERUSER);
      if (truncate) {
        LOG.warn("Truncate entire name usage archive");
        session.getMapper(ArchivedNameUsageMatchMapper.class).truncate();
        session.getMapper(ArchivedNameUsageMapper.class).truncate();
      }
    }

    LOG.info("Total number of projects found to rebuild: {}", projects.size());
    for (var key : projects) {
      try {
        rebuildProject(key, true);
      } catch (Exception e) {
        LOG.error("Failed to archive names for project {}", key, e);
      }
    }
  }

  /**
   * Builds the archive of a project whose archive is empty, from the releases that still exist.
   * @param copyMatches if true also copies the names index matches of the release usages
   * @throws IllegalArgumentException if the key is not a project or its archive already contains usages
   */
  public ArchiveStats rebuildProject(int projectKey, boolean copyMatches) {
    try (SqlSession session = factory.openSession(true)) {
      var project = session.getMapper(DatasetMapper.class).get(projectKey);
      if (project.getOrigin() != DatasetOrigin.PROJECT) {
        throw new IllegalArgumentException("Dataset " + projectKey + " is not a project");
      }
      if (project.hasDeletedDate()) {
        throw new IllegalArgumentException("Project " + projectKey + " is deleted");
      }
      int count = session.getMapper(ArchivedNameUsageMapper.class).count(projectKey);
      if (count > 0) {
        throw new IllegalArgumentException("Project " + projectKey + " already contains " + count + " archived name usages");
      }
    }
    return archiveProject(ranking(projectKey), copyMatches, false);
  }

  /**
   * Runs the per release step for every public, not deleted release of the project, highest rank first, so a record is
   * rewritten at most once, by its best release. Then tidies the release keys of all records of the project.
   */
  public ArchiveStats archiveProject(ReleaseRanking ranking, boolean copyMatches, boolean dryRun) {
    final int projectKey = ranking.getProjectKey();
    LOG.info("{} the name usage archive of project {} from its releases, highest rank first:\n{}",
      dryRun ? "Counting what would change in" : "Refreshing", projectKey, ranking.describe());
    var total = new ArchiveStats();
    for (var r : ranking.archivable()) {
      total.add(archiveRelease(ranking, r.getKey(), copyMatches, dryRun));
    }
    if (!dryRun) {
      try (SqlSession session = factory.openSession(true)) {
        int tidied = session.getMapper(ArchivedNameUsageMapper.class).tidyReleaseKeys(projectKey);
        LOG.info("Sorted and de-duplicated the release keys of {} archived usages of project {}", tidied, projectKey);
      }
    }
    LOG.info("{} name usage archive of project {}: {}", dryRun ? "Dry run of the" : "Refreshed", projectKey, total);
    return total;
  }

  /**
   * Archives a published release, ranked against all releases of its project.
   */
  public ArchiveStats archiveRelease(int releaseKey) {
    var info = DatasetInfoCache.CACHE.info(releaseKey);
    if (!info.origin.isRelease()) {
      throw new IllegalArgumentException("Not a release " + releaseKey);
    }
    return archiveRelease(ranking(info.sourceKey), releaseKey, true, false);
  }

  /**
   * The per release step, see the class docs.
   * @param copyMatches if true copies the names index matches of the release usages for the records it holds the version of
   * @param dryRun if true only counts what the step would write, each release against the archive as it is now
   */
  public ArchiveStats archiveRelease(ReleaseRanking ranking, int releaseKey, boolean copyMatches, boolean dryRun) {
    if (!ranking.isArchivable(releaseKey)) {
      throw new IllegalArgumentException("Release " + releaseKey + " is private or deleted and cannot be archived");
    }
    final int projectKey = ranking.getProjectKey();
    final boolean supplies = ranking.supplies(releaseKey);
    final List<Integer> blocking = ranking.blockingKeys(releaseKey);
    final ArchiveStats stats = new ArchiveStats();
    stats.releases = 1;
    try (SqlSession session = factory.openSession(true)) {
      var anum = session.getMapper(ArchivedNameUsageMapper.class);
      if (dryRun) {
        stats.inserted = anum.countMissingUsages(projectKey, releaseKey);
        if (supplies) {
          stats.rewritten = anum.countOutdatedUsages(projectKey, releaseKey, blocking, false);
          stats.renamed = anum.countOutdatedUsages(projectKey, releaseKey, blocking, true);
        }
        stats.keysAdded = anum.countMissingReleaseKeys(projectKey, releaseKey);

      } else {
        stats.inserted = anum.createMissingUsages(projectKey, releaseKey);
        if (supplies) {
          stats.rewritten = anum.updateExistingUsages(projectKey, releaseKey, blocking);
        }
        if (copyMatches) {
          var amm = session.getMapper(ArchivedNameUsageMatchMapper.class);
          stats.matchesCopied = amm.copyReleaseMatches(projectKey, releaseKey, blocking);
          stats.matchesDeleted = amm.deleteUnmatchedReleaseMatches(projectKey, releaseKey, blocking);
        }
        // redirects are decided by the newest release alone, the staged pairs of an older one are stale
        if (ranking.isTop(releaseKey)) {
          stats.supersededCleared = anum.clearSuperseded(projectKey, releaseKey);
          stats.supersededApplied = anum.applySuperseded(projectKey, releaseKey);
        }
        anum.deleteSuperseded(releaseKey);
        // last: a release key in the archive means the release was archived completely
        stats.keysAdded = anum.addReleaseKey(projectKey, releaseKey);
      }
    }
    LOG.info("{} release {} of project {}: {}", dryRun ? "Dry run of archiving" : "Archived", releaseKey, projectKey, stats);
    return stats;
  }

  /**
   * @return the public, not deleted releases of the project that have usages but whose key appears nowhere in the
   *   archive, i.e. which were never, or not completely, archived
   */
  public List<Integer> unarchivedReleases(int projectKey) {
    List<Integer> missing = new ArrayList<>();
    try (SqlSession session = factory.openSession(true)) {
      var anum = session.getMapper(ArchivedNameUsageMapper.class);
      for (var r : session.getMapper(DatasetMapper.class).listReleasesForArchive(projectKey)) {
        if (r.isArchivable() && !anum.isReleaseArchived(projectKey, r.getKey()) && anum.hasUsages(r.getKey())) {
          missing.add(r.getKey());
        }
      }
    }
    return missing;
  }
}
```

- [ ] **Step 11: Update the mapper tests**

In `ArchivedNameUsageMapperTest.java` replace the `createMissingUsages` test with:

```java
  @Test
  public void archiveStatements() throws Exception {
    // apple has no release data, but this proves every statement runs against the schema
    final int rel = 1000;
    assertEquals(0, mapper().createMissingUsages(Datasets.COL, rel));
    assertEquals(0, mapper().updateExistingUsages(Datasets.COL, rel, List.of()));
    assertEquals(0, mapper().updateExistingUsages(Datasets.COL, rel, List.of(1, 2)));
    assertEquals(0, mapper().addReleaseKey(Datasets.COL, rel));
    assertEquals(0, mapper().tidyReleaseKeys(Datasets.COL));
    assertEquals(0, mapper().countMissingUsages(Datasets.COL, rel));
    assertEquals(0, mapper().countOutdatedUsages(Datasets.COL, rel, List.of(1), false));
    assertEquals(0, mapper().countOutdatedUsages(Datasets.COL, rel, List.of(), true));
    assertEquals(0, mapper().countMissingReleaseKeys(Datasets.COL, rel));
    assertFalse(mapper().isReleaseArchived(Datasets.COL, rel));
    assertFalse(mapper().hasUsages(rel));
    assertEquals(0, mapper().clearSuperseded(Datasets.COL, rel));
  }
```

In `ArchivedNameUsageMatchMapperTest.java` add `import java.util.List;`, delete the tests `createMissingUsages` and `refreshMatches`, and add:

```java
  @Test
  public void releaseMatches() throws Exception {
    // apple has no release data, but this proves both statements run against the schema
    assertEquals(0, mapper().copyReleaseMatches(Datasets.COL, 1000, List.of()));
    assertEquals(0, mapper().deleteUnmatchedReleaseMatches(Datasets.COL, 1000, List.of(1, 2)));
  }
```

In `DatasetMapperTest.java` add:

```java
  @Test
  public void listReleasesForArchive() throws Exception {
    // apple has no releases of COL, but this proves the statement and its result mapping run
    assertTrue(mapper().listReleasesForArchive(Datasets.COL).isEmpty());
  }
```

- [ ] **Step 12: Fix the two callers outside dao**

In `core/src/main/java/life/catalogue/release/PublishReleaseListener.java` replace

```java
      // When a release gets published we need to modify the projects name archive:
      // a) Usages with new ids need to be added
      // b) For all still existing usages the release_key needs to be added
      try {
        archiver.archiveRelease(event.obj.getKey(), true);
```

with

```java
      // the published release enters the project's name usage archive. Both apps of a blue-green deploy receive this
      // event, which the archiver is safe against
      try {
        archiver.archiveRelease(event.obj.getKey());
```

In `core/src/test/java/life/catalogue/release/XReleaseIT.java` replace `archiver.archiveRelease(releaseKey, true);` with `archiver.archiveRelease(releaseKey);`.

- [ ] **Step 13: Run the dao tests**

Run: `mvn -o -pl dao test -Dtest='ReleaseRankingTest,ArchivedNameUsage*Test,DatasetMapperTest,PgSetupRuleTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, no failures.

Run: `mvn -o -pl dao verify -Dit.test=NameUsageArchiverIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false`
Expected: PASS, `Tests run: 6, Failures: 0, Errors: 0`

- [ ] **Step 14: Compile core against the new dao**

Run: `mvn -o -pl dao -am install -DskipTests && mvn -o -pl core test-compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 15: Commit**

```bash
git add dao/src/test/resources/test-data/archive dao/src/test/java/life/catalogue/junit/TestDataRule.java \
  dao/src/test/java/life/catalogue/dao/NameUsageArchiverIT.java \
  dao/src/main/java/life/catalogue/db/mapper/DatasetMapper.java dao/src/main/resources/life/catalogue/db/mapper/DatasetMapper.xml \
  dao/src/main/java/life/catalogue/db/mapper/ArchivedNameUsageMapper.java dao/src/main/resources/life/catalogue/db/mapper/ArchivedNameUsageMapper.xml \
  dao/src/main/java/life/catalogue/db/mapper/ArchivedNameUsageMatchMapper.java dao/src/main/resources/life/catalogue/db/mapper/ArchivedNameUsageMatchMapper.xml \
  dao/src/main/java/life/catalogue/dao/ArchiveStats.java dao/src/main/java/life/catalogue/dao/NameUsageArchiver.java \
  dao/src/test/java/life/catalogue/db/mapper/ArchivedNameUsageMapperTest.java dao/src/test/java/life/catalogue/db/mapper/ArchivedNameUsageMatchMapperTest.java \
  dao/src/test/java/life/catalogue/db/mapper/DatasetMapperTest.java \
  core/src/main/java/life/catalogue/release/PublishReleaseListener.java core/src/test/java/life/catalogue/release/XReleaseIT.java
git commit -m "feat(archive): archive a release with one ranked step that is safe to run twice"
```

---

### Task 3: The archive refresh job

**Files:**
- Modify: `dao/src/main/java/life/catalogue/matching/ArchiveMatcher.java`
- Create: `core/src/main/java/life/catalogue/release/IgnoredReleases.java`
- Create: `core/src/main/java/life/catalogue/jobs/ArchiveRefreshJob.java`
- Test: `core/src/test/java/life/catalogue/jobs/ArchiveRefreshJobIT.java`
- Modify: `core/src/main/java/life/catalogue/release/PublishReleaseListener.java`
- Modify: `webservice/src/main/java/life/catalogue/resources/AdminResource.java`
- Modify: `webservice/src/main/java/life/catalogue/command/ArchiveCmd.java`

**Interfaces:**
- Consumes: `NameUsageArchiver(SqlSessionFactory, IntFunction<IntSet>)`, `ranking(int)`, `archiveProject(ReleaseRanking, boolean, boolean)`, `ArchiveStats` (Task 2); `TestDataRule.archive()` (Task 2).
- Produces:
  - `ArchiveMatcher.match(int projectKey)`.
  - `IgnoredReleases(SqlSessionFactory)` implementing `IntFunction<IntSet>`.
  - `ArchiveRefreshJob(int userKey, SqlSessionFactory factory, NameIndex ni, int projectKey, boolean dryRun)`; its final step starts with `"<n> releases"`, or `"dry run"` for a dry run.
  - `POST /admin/archive/refresh?projectKey=<key>&dryRun=<bool>` answering `JobInfo`.

- [ ] **Step 1: Write the failing integration test**

Create `core/src/test/java/life/catalogue/jobs/ArchiveRefreshJobIT.java`:

```java
package life.catalogue.jobs;

import life.catalogue.api.model.DSID;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.mapper.ArchivedNameUsageMapper;
import life.catalogue.db.mapper.ArchivedNameUsageMatchMapper;
import life.catalogue.junit.NameMatchingRule;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class ArchiveRefreshJobIT {

  @ClassRule
  public static SqlSessionFactoryRule pgSetupRule = new PgSetupRule();

  // matchAll=false: no archived name is matched before the job runs, so every archive match comes from its rematch
  @Rule
  public final TestRule chain = RuleChain
    .outerRule(TestDataRule.archive())
    .around(new NameMatchingRule(SqlSessionFactoryRule::getSqlSessionFactory, false, false));

  ArchiveRefreshJob job(boolean dryRun) {
    return new ArchiveRefreshJob(Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), NameMatchingRule.getIndex(),
      Datasets.COL, dryRun);
  }

  @Test
  public void refreshesAndRematches() throws Exception {
    var job = job(false);
    job.run();
    assertEquals("job failed: " + job.getError(), JobStatus.FINISHED, job.getStatus());
    assertTrue(job.getStep(), job.getStep().startsWith("3 releases"));

    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var anum = session.getMapper(ArchivedNameUsageMapper.class);
      assertEquals("Miller", anum.get(DSID.of(Datasets.COL, "A")).getName().getAuthorship());
      assertEquals("Pinus mugo", anum.get(DSID.of(Datasets.COL, "E")).getName().getScientificName());
      var amm = session.getMapper(ArchivedNameUsageMatchMapper.class);
      for (String id : List.of("A", "B", "C", "D", "E")) {
        assertNotNull("archived name " + id + " was not matched", amm.get(DSID.of(Datasets.COL, id)));
      }
    }
  }

  @Test
  public void dryRunWritesNothing() throws Exception {
    var job = job(true);
    job.run();
    assertEquals("job failed: " + job.getError(), JobStatus.FINISHED, job.getStatus());
    assertTrue(job.getStep(), job.getStep().startsWith("dry run"));
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      assertNull(session.getMapper(ArchivedNameUsageMapper.class).get(DSID.of(Datasets.COL, "D")));
    }
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -o -pl dao -am install -DskipTests && mvn -o -pl core verify -Dit.test=ArchiveRefreshJobIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false`
Expected: FAIL, compilation error `cannot find symbol: class ArchiveRefreshJob`.

- [ ] **Step 3: Add the per project rematch**

In `dao/src/main/java/life/catalogue/matching/ArchiveMatcher.java` add after `match()`:

```java
  /**
   * Rematches the archived names of one project through the names index. Only changed matches are written, and an
   * archived name that no longer matches loses its match record. Other projects are left alone.
   */
  public void match(int projectKey) {
    LOG.info("Rematch the name usage archive of project {}", projectKey);
    try (SqlSession readOnlySession = factory.openSession(true)) {
      var amum = readOnlySession.getMapper(ArchivedNameUsageMapper.class);
      try (BulkMatchHandler hn = new BulkMatchHandler(true, ArchivedNameUsageMatchMapper.class, true)) {
        PgUtils.consume(() -> amum.processArchivedNames(projectKey, false), hn);
      }
    } finally {
      LOG.info("Rematched {} archived names of project {}: {} changed, {} without a match", total, projectKey, updated, nomatch);
    }
  }
```

- [ ] **Step 4: Create `IgnoredReleases`**

Create `core/src/main/java/life/catalogue/release/IgnoredReleases.java`:

```java
package life.catalogue.release;

import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.vocab.Setting;
import life.catalogue.db.mapper.DatasetMapper;

import java.util.function.IntFunction;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * The release keys a project's release and extended release configs tell id mapping to ignore, see
 * ProjectReleaseConfig#ignoredReleases. The name usage archive never takes a version from them.
 */
public class IgnoredReleases implements IntFunction<IntSet> {
  private final SqlSessionFactory factory;

  public IgnoredReleases(SqlSessionFactory factory) {
    this.factory = factory;
  }

  @Override
  public IntSet apply(int projectKey) {
    DatasetSettings settings;
    try (SqlSession session = factory.openSession(true)) {
      settings = session.getMapper(DatasetMapper.class).getSettings(projectKey);
    }
    IntSet ignored = new IntOpenHashSet();
    if (settings != null) {
      var rCfg = ProjectRelease.loadConfig(ProjectReleaseConfig.class, settings.getURI(Setting.RELEASE_CONFIG), false);
      var xCfg = ProjectRelease.loadConfig(XReleaseConfig.class, settings.getURI(Setting.XRELEASE_CONFIG), false);
      if (rCfg.ignoredReleases != null) {
        ignored.addAll(rCfg.ignoredReleases);
      }
      if (xCfg.ignoredReleases != null) {
        ignored.addAll(xCfg.ignoredReleases);
      }
    }
    return ignored;
  }
}
```

- [ ] **Step 5: Create `ArchiveRefreshJob`**

Create `core/src/main/java/life/catalogue/jobs/ArchiveRefreshJob.java`:

```java
package life.catalogue.jobs;

import life.catalogue.api.vocab.JobPriority;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.DatasetBlockingJob;
import life.catalogue.dao.ArchiveStats;
import life.catalogue.dao.DaoUtils;
import life.catalogue.dao.NameUsageArchiver;
import life.catalogue.matching.ArchiveMatcher;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.release.IgnoredReleases;

import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Refreshes the name usage archive of one project in place, so every archived id holds the version of its highest
 * ranked release, then rematches the archived names through the names index. It never deletes an archive record.
 *
 * It holds the project's dataset lock, the lock base and extended release jobs take, so no release of the project maps
 * ids meanwhile. A dry run only counts what would be written. See docs/2026-09-15-name-usage-archive-migration.md.
 */
public class ArchiveRefreshJob extends DatasetBlockingJob {
  private final SqlSessionFactory factory;
  private final NameIndex ni;
  private final boolean dryRun;
  private ArchiveStats stats;
  private int rematched;

  public record Params(int projectKey, boolean dryRun) {
  }

  public ArchiveRefreshJob(int userKey, SqlSessionFactory factory, NameIndex ni, int projectKey, boolean dryRun) {
    super(projectKey, userKey, JobPriority.HIGH);
    DaoUtils.requireProject(projectKey, "Only projects have a name usage archive");
    this.factory = factory;
    this.ni = ni.assertOnline();
    this.dryRun = dryRun;
    this.logToFile = true;
  }

  @Override
  public Object getParams() {
    return new Params(datasetKey, dryRun);
  }

  @Override
  public boolean isDuplicate(BackgroundJob other) {
    return other instanceof ArchiveRefreshJob job && job.datasetKey == datasetKey;
  }

  @Override
  protected void runWithLock() throws Exception {
    var archiver = new NameUsageArchiver(factory, new IgnoredReleases(factory));
    setStep(dryRun ? "counting changes" : "refreshing archive records");
    stats = archiver.archiveProject(archiver.ranking(datasetKey), false, dryRun);
    if (!dryRun) {
      checkIfCancelled();
      setStep("rematching archived names");
      var matcher = new ArchiveMatcher(factory, ni);
      matcher.match(datasetKey);
      rematched = matcher.getUpdated();
    }
  }

  @Override
  protected void onFinishLocked() throws Exception {
    // a successful job's step is cleared before onFinish runs, so the outcome has to be set here to be kept
    if (getStatus() == JobStatus.FINISHED && stats != null) {
      setStep(dryRun ? "dry run, nothing written: " + stats : stats + ", " + rematched + " archive matches changed by the names index");
    }
  }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `mvn -o -pl dao -am install -DskipTests && mvn -o -pl core verify -Dit.test=ArchiveRefreshJobIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false`
Expected: PASS, `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 7: Wire the job and the ignored releases**

In `PublishReleaseListener.java` replace `this.archiver = new NameUsageArchiver(factory);` with `this.archiver = new NameUsageArchiver(factory, new IgnoredReleases(factory));`, and in the class javadoc replace

```java
 * It then
 *  - publishes the concept DOI
 * For COL releases it also does:
 *  - copies existing exports to the COL export folder
 *  - inserts deleted ids from the reports into the names archive
 *  - removes resurrected ids from the names archive
```

with

```java
 * It then
 *  - publishes the concept DOI
 *  - archives the name usages of the release, see NameUsageArchiver
 * For COL releases it also does:
 *  - copies existing exports to the COL export folder
```

In `AdminResource.java` add after the `@Path("/rematch/archive")` method:

```java
  @POST
  @Path("/archive/refresh")
  public JobInfo refreshArchive(@QueryParam("projectKey") Integer projectKey,
                                @QueryParam("dryRun") boolean dryRun,
                                @Auth User user) {
    Preconditions.checkArgument(projectKey != null, "A projectKey parameter must be given");
    return runJob(new ArchiveRefreshJob(user.getKey(), factory, namesIndex, projectKey, dryRun));
  }
```

In `ArchiveCmd.java` add `import life.catalogue.release.IgnoredReleases;` and replace `archiver = new NameUsageArchiver(factory);` with `archiver = new NameUsageArchiver(factory, new IgnoredReleases(factory));`.

- [ ] **Step 8: Compile everything**

Run: `mvn -o -pl webservice -am install -DskipTests`
Expected: `BUILD SUCCESS`

- [ ] **Step 9: Commit**

```bash
git add dao/src/main/java/life/catalogue/matching/ArchiveMatcher.java \
  core/src/main/java/life/catalogue/release/IgnoredReleases.java core/src/main/java/life/catalogue/jobs/ArchiveRefreshJob.java \
  core/src/test/java/life/catalogue/jobs/ArchiveRefreshJobIT.java core/src/main/java/life/catalogue/release/PublishReleaseListener.java \
  webservice/src/main/java/life/catalogue/resources/AdminResource.java webservice/src/main/java/life/catalogue/command/ArchiveCmd.java
git commit -m "feat(archive): refresh a project's archive in place with an admin job"
```

---

### Task 4: Releases refuse to start while a public release is not archived

**Files:**
- Modify: `core/src/main/java/life/catalogue/release/ProjectRelease.java` (`initJob`)
- Test: `core/src/test/java/life/catalogue/release/ProjectReleaseIT.java`

**Interfaces:**
- Consumes: `NameUsageArchiver.unarchivedReleases(int)` (Task 2).
- Produces: a base or extended release job fails in `initJob` with an `IllegalStateException` naming the unarchived release keys.

- [ ] **Step 1: Write the failing test**

Add to `ProjectReleaseIT.java`:

```java
  /**
   * A public release missing from the name usage archive, e.g. because archiving it on publish failed, would let the id
   * provider issue its identifiers again. The release refuses to start instead.
   */
  @Test
  public void releaseRefusesUnarchivedPublicRelease() throws Exception {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      session.getConnection().createStatement().execute(
        "UPDATE name_usage_archive SET release_keys = array_remove(release_keys, 13) WHERE dataset_key=" + projectKey);
    }
    ProjectRelease release = buildRelease();
    release.run();
    assertEquals(JobStatus.FAILED, release.getStatus());
    assertTrue(release.getError().getMessage(), release.getError().getMessage().contains("[13]"));
  }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -o -pl core verify -Dit.test=ProjectReleaseIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false`
Expected: FAIL in `releaseRefusesUnarchivedPublicRelease`, `expected:<FAILED> but was:<FINISHED>`.

- [ ] **Step 3: Add the check**

In `ProjectRelease.java` change `initJob` to:

```java
  @Override
  void initJob() throws Exception {
    super.initJob();
    assertReleasesArchived();
    // point to release in CLB - this requires the datasetKey to exist already
    newDataset.setUrl(UriBuilder.fromUri(clbURI)
      .path("dataset")
      .path(newDataset.getKey().toString())
      .build());
    dDao.update(newDataset, user);
  }

  /**
   * Every public release of the project has to be in the name usage archive before ids are mapped: the id sequence starts
   * above the highest archived id, so the ids only an unarchived release carries could be issued again to other names.
   * Publishing archives a release; this catches the ones whose archiving failed or never happened.
   * It runs once the release dataset and its import metrics exist, which the error handling of a failed job expects.
   */
  private void assertReleasesArchived() {
    var missing = new NameUsageArchiver(factory).unarchivedReleases(projectKey);
    if (!missing.isEmpty()) {
      throw new IllegalStateException(String.format("Public releases %s of project %s are missing from the name usage archive, "
          + "so their identifiers could be issued again. Archive them first with POST /admin/archive/refresh?projectKey=%s",
        missing, projectKey, projectKey));
    }
  }
```

- [ ] **Step 4: Run the release tests**

Run: `mvn -o -pl core verify -Dit.test='ProjectReleaseIT,XReleaseBasicIT,XReleaseIT,IdProviderIT,IdProviderReleaseIT,IdProviderArchiveIT' -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false`
Expected: PASS, no failures. `XReleaseIT` archives its base release before building the extended one and `ArchivingRule` archives the fixtures, so none of the existing tests trips the check.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/life/catalogue/release/ProjectRelease.java core/src/test/java/life/catalogue/release/ProjectReleaseIT.java
git commit -m "feat(release): refuse to map ids while a public release is missing from the archive"
```

---

### Task 5: Documentation, plan removal and full verification

**Files:**
- Modify: `dao/src/main/resources/life/catalogue/db/dbschema.md`
- Modify: `CLAUDE.md`
- Modify: `docs/2026-09-15-name-usage-archive-migration.md`
- Modify: `docs/2026-09-15-stable-id-evidence-model.md`
- Delete: `docs/2026-09-15-name-usage-archive-migration-plan.md`

**Interfaces:**
- Consumes: everything from Tasks 1-4.
- Produces: docs that describe the shipped behaviour; no code.

- [ ] **Step 1: Replace the archive migration in `dbschema.md`**

Replace the whole section from the line `#### 2026-09-15 name usage archive holds the latest version of an id, not the first` up to, not including, `#### 2026-09-10 split authorship out of sector subject and target names` with:

````markdown
#### 2026-09-15 name usage archive holds the newest version of an id, not the first
No DDL, but a **data refresh per project before its first release after this deploy**, done by an admin job.

`name_usage_archive` only ever inserted an id and then appended release keys to it, so every archived usage carried
the name, authorship, rank, status and classification of the release that first minted its id - for COL often a
decade out of date. That is the snapshot the id provider scores the next release against. Publishing now runs
`NameUsageArchiver.archiveRelease`, which keeps every id at the version of its highest ranked release, see
`docs/2026-09-15-name-usage-archive-migration.md`.

Existing archives are refreshed in place, never deleted: deleting one loses every id only deleted releases carried, and
can start the id sequence below an already published id. Per project, COL first:

```sql
-- 1. back up the project's archive
CREATE TABLE name_usage_archive_bak_3 AS SELECT * FROM name_usage_archive WHERE dataset_key = 3;
CREATE TABLE name_usage_archive_match_bak_3 AS SELECT * FROM name_usage_archive_match WHERE dataset_key = 3;
```

2. `POST /admin/archive/refresh?projectKey=3&dryRun=true`, then check the release ranking in the job log and the counts
   in the job's step
3. `POST /admin/archive/refresh?projectKey=3`
4. `VACUUM (ANALYZE) name_usage_archive;` - the first run rewrites most rows
5. before publishing the first release afterwards, diff its created, deleted and resurrected reports against the
   previous attempt

Until the job ran, a release of a project whose archive lacks one of its public releases refuses to start.
Do not use deploy's `archive.sh`: the `archive` command refuses a non empty archive, and emptying it first is exactly
what loses ids. Rollback:

```sql
DELETE FROM name_usage_archive_match WHERE dataset_key = 3;
DELETE FROM name_usage_archive WHERE dataset_key = 3;
INSERT INTO name_usage_archive SELECT * FROM name_usage_archive_bak_3;
INSERT INTO name_usage_archive_match SELECT * FROM name_usage_archive_match_bak_3;
```

````

In the section `#### 2026-09-15 record which id superseded a deleted one` replace
`` `NameUsageArchiver.archiveRelease` applies and drops it when the release goes public. `` with
`` `NameUsageArchiver.archiveRelease` applies it when the release goes public if that release is the project's highest ranked one, and drops it either way. ``

- [ ] **Step 2: Update `CLAUDE.md`**

In the **Stable identifiers** paragraph replace the four lines starting with `The archive is the memory all of this reads:` and ending with `release in `usage_id_superseded` and only applied on publish).` with:

```markdown
The archive is the memory all of this reads: one row per id ever issued, holding the version of the highest ranked
release that carries it (`ReleaseRanking`: base release generations newest first, an extended release in the generation
of the base release its job recorded as `params.baseReleaseKey`, the base release above its extended releases). It used
to freeze the first version, which is why an old id kept losing to a younger duplicate. Publishing runs the per release
step `NameUsageArchiver.archiveRelease`, which never deletes a row, is safe to run twice - the broker delivers the event
to both apps of a blue-green deploy - and writes the release key last, so a key's presence means the release was
archived completely: base and extended release jobs refuse to start while a public release of their project lacks its
key. `ArchiveRefreshJob` (`POST /admin/archive/refresh?projectKey=`) refreshes a whole project in place and rematches it
through the names index. An id a release drops can record which id took it over (`name_usage_archive.superseded_by`,
staged per release in `usage_id_superseded`, applied on publish by the project's highest ranked release only). See
[`docs/2026-09-15-name-usage-archive-migration.md`](docs/2026-09-15-name-usage-archive-migration.md).
```

- [ ] **Step 3: Record the outcome in the design record**

In `docs/2026-09-15-name-usage-archive-migration.md` replace the `Status:` paragraph (three lines, up to `delete an archive and must not be run.`) with:

```markdown
Status: implemented on branch `chore/col-stable-id-improvement`, not merged or deployed yet. It replaces the archive
migration of [2026-09-15-stable-id-evidence-model.md](2026-09-15-stable-id-evidence-model.md).
```

and append at the end of the file:

```markdown
## Outcome

- The release start check runs right after the release job created its dataset and import metrics
  (`ProjectRelease.initJob`, which `XRelease.initJob` passes through), not before. A job failing earlier breaks
  `onError` and `onFinishLocked`, which expect both. It still runs before any id work.
- The version comparison picks a name's basionym with an ordered `LIMIT 1` lateral join. The plain join the branch used
  yields one row per basionym relation and an UPDATE applies an arbitrary one, so the "only rewrite what differs"
  statement would have written on every run.
- A dry run counts each release against the archive as it is before the run, so the counts of lower ranked releases
  include records a higher ranked one would already have written.
- `createMissingMatches`, `refreshMatches` and `createAllMatches` are gone: matches follow the release usage's own
  `name_id` on publish, and the names index in the refresh job.
- Tests: `ReleaseRankingTest`, `NameUsageArchiverIT` on the `archive` fixture, `ArchiveRefreshJobIT` and
  `ProjectReleaseIT.releaseRefusesUnarchivedPublicRelease`.
```

- [ ] **Step 4: Point the evidence model record at the refresh**

In `docs/2026-09-15-stable-id-evidence-model.md`, under `## Migration`, replace the paragraph with:

```markdown
Two `dbschema.md` entries dated 2026-09-15: the `superseded_by` column plus the `usage_id_superseded` staging table,
and - no DDL but mandatory - a one-off **refresh** of every project's name usage archive before the first release after
this deploy, because existing archives still hold first versions. The rebuild first planned here deleted each archive
and would have lost every id only deleted releases carried; the refresh replacing it is designed in
[2026-09-15-name-usage-archive-migration.md](2026-09-15-name-usage-archive-migration.md). Expect a one-off burst of id
churn on that first release, concentrated on names whose authorship or rank was corrected since their id was minted,
and near zero from then on.
```

- [ ] **Step 5: Delete this plan**

Run: `git rm docs/2026-09-15-name-usage-archive-migration-plan.md`

- [ ] **Step 6: Run the full verification**

Run: `mvn -o -pl webservice -am install -DskipTests`
Expected: `BUILD SUCCESS`

Run: `mvn -o -pl dao test`
Expected: `BUILD SUCCESS`, no failures (DB backed tests, takes several minutes).

Run: `mvn -o -pl core test`
Expected: `BUILD SUCCESS`, no failures.

Run: `mvn -o -pl dao verify -Dit.test=NameUsageArchiverIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false`
Expected: PASS.

Run: `mvn -o -pl core verify -Dit.test='ArchiveRefreshJobIT,ProjectReleaseIT,XReleaseBasicIT,XReleaseIT,IdProviderIT,IdProviderReleaseIT,IdProviderArchiveIT' -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add dao/src/main/resources/life/catalogue/db/dbschema.md CLAUDE.md \
  docs/2026-09-15-name-usage-archive-migration.md docs/2026-09-15-stable-id-evidence-model.md
git commit -m "docs: replace the archive rebuild with the in place refresh, record the outcome"
```
