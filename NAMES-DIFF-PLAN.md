# Authorship-aware change pairing — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the names-diff engine recognise an added/removed/replaced authorship (and a 1-char canonical typo) as a single `changed` entry instead of a separate `removed` + `added`, while keeping distinct same-canonical synonyms apart.

**Architecture:** Replace pass 2's whole-label fuzzy Levenshtein pairing in `ChangedMatcher` with a canonical-based pairing: parse each candidate in the diff *remainder* (small set), strip authorship via the name parser, normalise the canonical with `SciNameNormalizer.normalize`, and pair a removed with an added when their normalised canonicals are within edit distance ≤ 1. When several same-canonical candidates compete, use `AuthorComparator` (year-aware) as a tie-break so synonyms are not mixed up. The stored one-name-per-row file format is unchanged.

**Tech Stack:** Java 25, Maven (multi-module reactor), JUnit 4, MyBatis (unchanged here). Uses existing `dao` classes: `life.catalogue.parser.NameParser`, `life.catalogue.common.tax.SciNameNormalizer`, `life.catalogue.matching.authorship.AuthorComparator`, `life.catalogue.matching.Equality`, `life.catalogue.matching.similarity.LevenshteinDistance` / `NormalizedLevenshtein`, `org.gbif.nameparser.api.Authorship`.

## Global Constraints

- Java 25; 2-space indent, 1TBS braces, 140-column limit; CamelCase types, camelCase vars.
- Design doc: `NAMES-DIFF.md` (repo root). This plan implements it.
- The `docs/` dir is git-ignored in this repo, so plan/spec live at repo root (like `XRelease.md`).
- Tests are JUnit 4 (`org.junit.Test`, `org.junit.Assert.*`) — match the existing `ChangedMatcherTest` style. No database; the tests use the in-memory `NameParser.PARSER` singleton directly.
- Run a single dao test class from the repo root with:
  `mvn -q -pl dao -am test -Dtest=<ClassName>` (`-am` builds `api`/`parser` deps on the first run; later runs can drop `-am`).
- Branch: `feature/names-diff-authorship-pairing` (already created; the spec commit is on it).

---

### Task 1: Swap `changedThreshold` for `canonicalMaxDistance` in `DiffOptions`

The gating knob changes meaning: pairing is no longer a 0–100 similarity threshold but an absolute canonical edit-distance limit.

**Files:**
- Modify: `dao/src/main/java/life/catalogue/printer/diff/DiffOptions.java:28,39-40`
- Test: `dao/src/test/java/life/catalogue/printer/diff/DiffOptionsTest.java:15`

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: `int DiffOptions.getCanonicalMaxDistance()` (default `1`) and `DiffOptions setCanonicalMaxDistance(int)`. Task 2 calls `getCanonicalMaxDistance()`.

- [ ] **Step 1: Update the failing test first**

In `DiffOptionsTest.java`, replace line 15:

```java
    assertEquals(50.0, o.getChangedThreshold(), 0.0001);
```

with:

```java
    assertEquals(1, o.getCanonicalMaxDistance());
```

- [ ] **Step 2: Run the test to verify it fails to compile / fails**

Run: `mvn -q -pl dao -am test -Dtest=DiffOptionsTest`
Expected: compilation failure — `getCanonicalMaxDistance()` does not exist yet.

- [ ] **Step 3: Change `DiffOptions`**

In `DiffOptions.java`, replace line 28:

```java
  private double changedThreshold = 50.0;   // 0..100
```

with:

```java
  private int canonicalMaxDistance = 1;      // max Levenshtein distance between normalised canonicals to pair as "changed"
```

and replace lines 39–40:

```java
  public double getChangedThreshold() { return changedThreshold; }
  public DiffOptions setChangedThreshold(double t) { this.changedThreshold = t; return this; }
```

with:

```java
  public int getCanonicalMaxDistance() { return canonicalMaxDistance; }
  public DiffOptions setCanonicalMaxDistance(int d) { this.canonicalMaxDistance = d; return this; }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -pl dao -am test -Dtest=DiffOptionsTest`
Expected: PASS. (This will not fully build unless `NamesDiffEngine` still compiles — it references `getChangedThreshold()`. If the module fails to compile at `NamesDiffEngine.java:17`, that's expected and fixed in Task 2; to validate Task 1 in isolation, temporarily proceed to Task 2. If you prefer a green checkpoint here, do Task 1 + Task 2 Steps for `NamesDiffEngine` together before running.)

- [ ] **Step 5: Commit**

```bash
git add dao/src/main/java/life/catalogue/printer/diff/DiffOptions.java \
        dao/src/test/java/life/catalogue/printer/diff/DiffOptionsTest.java
git commit -m "refactor(diff): replace changedThreshold with canonicalMaxDistance option"
```

---

### Task 2: Canonical-based pairing core in `ChangedMatcher` (1-in-1-out)

Rewrite pass B of `ChangedMatcher.match`: parse+normalise each remainder candidate, block by genus, pair only when exactly **one** eligible added candidate is within canonical edit distance ≤ `canonicalMaxDistance`. Multi-eligible candidates are left unpaired for now (Task 3 adds the tie-break). Wire `NamesDiffEngine.assemble` to the new signature.

**Files:**
- Modify: `dao/src/main/java/life/catalogue/printer/diff/ChangedMatcher.java` (rewrite `match` signature + pass B; keep Pass A and `block()`)
- Modify: `dao/src/main/java/life/catalogue/printer/diff/NamesDiffEngine.java:3,17`
- Test: `dao/src/test/java/life/catalogue/printer/diff/ChangedMatcherTest.java` (migrate existing calls; add new cases)

**Interfaces:**
- Consumes: `DiffOptions.getCanonicalMaxDistance()` (Task 1).
- Produces:
  - `ChangedMatcher.Result match(List<String> removed, List<String> added, int canonicalMaxDistance)` — new 3-arg signature (drops the `double threshold, StringSimilarity` params).
  - `ChangedMatcher.Result` record unchanged: `record Result(List<ChangedName> changed, List<String> removed, List<String> added)`.
  - Private helpers `parse(String)`, `choose(...)`, `authorEquality(...)` — Task 3 extends `choose`.

- [ ] **Step 1: Migrate existing tests + add new failing tests**

Replace the entire body of `ChangedMatcherTest.java` with:

```java
package life.catalogue.printer.diff;

import life.catalogue.printer.NamesDiff;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

public class ChangedMatcherTest {

  @Test
  public void pairsAuthorshipAppended() {
    var r = ChangedMatcher.match(
      List.of("Abies alba", "Zea mays L."),
      List.of("Abies alba Mill.", "Quercus robur"),
      1);
    assertEquals(1, r.changed().size());
    assertEquals("Abies alba", r.changed().get(0).before());
    assertEquals("Abies alba Mill.", r.changed().get(0).after());
    assertEquals(List.of("Zea mays L."), r.removed());
    assertEquals(List.of("Quercus robur"), r.added());
  }

  @Test
  public void pairsShortAuthorshipAppended() {
    // The headline case: a short canonical whose whole-label Levenshtein was below the old 50% threshold.
    var r = ChangedMatcher.match(
      List.of("Accipiter nisus"),
      List.of("Accipiter nisus (Linnaeus, 1758)"),
      1);
    assertEquals(1, r.changed().size());
    assertTrue(r.removed().isEmpty());
    assertTrue(r.added().isEmpty());
  }

  @Test
  public void pairsAuthorshipReplacedOneInOneOut() {
    // Different authors, but only one candidate each -> reported as changed.
    var r = ChangedMatcher.match(
      List.of("Poa annua Mill."),
      List.of("Poa annua L."),
      1);
    assertEquals(1, r.changed().size());
    assertEquals("Poa annua Mill.", r.changed().get(0).before());
    assertEquals("Poa annua L.", r.changed().get(0).after());
  }

  @Test
  public void pairsCanonicalTypoWithinDistanceOne() {
    var r = ChangedMatcher.match(
      List.of("Accipiter nisus"),
      List.of("Accipiter nisius"),
      1);
    assertEquals(1, r.changed().size());
  }

  @Test
  public void differentSpeciesSameGenusNotPaired() {
    var r = ChangedMatcher.match(
      List.of("Accipiter nisus"),
      List.of("Accipiter minor"),
      1);
    assertTrue(r.changed().isEmpty());
    assertEquals(List.of("Accipiter nisus"), r.removed());
    assertEquals(List.of("Accipiter minor"), r.added());
  }

  @Test
  public void identicalPairsAreHealedNotChanged() {
    var r = ChangedMatcher.match(List.of("Aus aus"), List.of("Aus aus"), 1);
    assertTrue(r.changed().isEmpty());
    assertTrue(r.removed().isEmpty());
    assertTrue(r.added().isEmpty());
  }

  @Test
  public void differentGenusNotPaired() {
    var r = ChangedMatcher.match(List.of("Aus aus"), List.of("Zea mays"), 1);
    assertTrue(r.changed().isEmpty());
    assertEquals(List.of("Aus aus"), r.removed());
    assertEquals(List.of("Zea mays"), r.added());
  }

  @Test
  public void identicalHealedRegardlessOfPairing() {
    // A pairing must not consume an added string that a later removed item is identical to.
    var r = ChangedMatcher.match(
      List.of("Abies alphaz", "Abies alpha"),
      List.of("Abies alpha", "Abies alphax"),
      1);
    assertFalse(r.removed().contains("Abies alpha"));
    assertFalse(r.added().contains("Abies alpha"));
    assertTrue(r.changed().stream().noneMatch(c -> c.before().equals("Abies alpha") || c.after().equals("Abies alpha")));
    assertEquals(1, r.changed().size());
    assertEquals("Abies alphaz", r.changed().get(0).before());
    assertEquals("Abies alphax", r.changed().get(0).after());
  }

  @Test
  public void unparsableLabelsDoNotThrow() {
    // Fallback path: parse returns empty -> normalise whole label. Must not throw.
    var r = ChangedMatcher.match(
      List.of("?incertae?"),
      List.of("?incertae? sp"),
      1);
    assertNotNull(r);
    // Both labels survive the fallback path (parse empty -> normalise whole label); exact bucketing is
    // not asserted, only that the call completes and accounts for both inputs.
    assertEquals(2, r.changed().size() * 2 + r.removed().size() + r.added().size());
  }

  @Test
  public void assembleTruncates() {
    NamesDiff d = NamesDiffEngine.assemble("a", "b",
      new java.util.ArrayList<>(List.of("Aus aa", "Bus bb", "Cus cc")),
      new java.util.ArrayList<>(List.of("Xus xx", "Yus yy")),
      DiffOptions.defaults().setMaxItems(2));
    assertTrue(d.isTruncated());
    assertEquals(2, d.getRemoved().size());
    assertEquals(2, d.getAdded().size());
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -pl dao -am test -Dtest=ChangedMatcherTest`
Expected: compilation failure — `match(List, List, int)` does not exist (old signature is `match(List, List, double, StringSimilarity)`).

- [ ] **Step 3: Rewrite `ChangedMatcher.java`**

Replace the whole file with:

```java
package life.catalogue.printer.diff;

import life.catalogue.api.model.Name;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.tax.SciNameNormalizer;
import life.catalogue.matching.Equality;
import life.catalogue.matching.authorship.AuthorComparator;
import life.catalogue.matching.similarity.LevenshteinDistance;
import life.catalogue.matching.similarity.NormalizedLevenshtein;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.Authorship;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pass 2 of the diff: pairs "removed" and "added" candidates that are the same name with only its
 * authorship (or a single-character canonical typo) changed, rather than an unrelated delete + insert.
 *
 * <p>Candidates are blocked by their first token (genus). Within a block a removed is paired with an
 * added when their authorship-stripped, {@link SciNameNormalizer#normalize(String) normalised} canonicals
 * are within {@code canonicalMaxDistance} edit operations. When exactly one added candidate is eligible it
 * is paired unconditionally (covers added/removed/replaced authorship, e.g. {@code Mill.}→{@code L.}); when
 * several same-canonical variants compete, {@link AuthorComparator} (year-aware) is used as a tie-break so
 * distinct synonyms are not mixed up.
 *
 * <p>Pass A first heals identical strings (present on both sides), independent of iteration order; those are
 * not changes. Only the diff remainder reaches the parse-based pass B, so per-candidate parsing is cheap.
 */
public class ChangedMatcher {

  private static final AuthorComparator AUTHOR_CMP = new AuthorComparator(AuthorshipNormalizer.INSTANCE);
  private static final NormalizedLevenshtein DISPLAY_SIM = new NormalizedLevenshtein();

  public record Result(List<ChangedName> changed, List<String> removed, List<String> added) {}

  /** A parsed candidate: raw label, its normalised canonical (authorship stripped) and parsed authorship. */
  private static final class Candidate {
    final String label;
    final String normCanonical;
    final Authorship authorship; // null when the label could not be parsed

    Candidate(String label, String normCanonical, Authorship authorship) {
      this.label = label;
      this.normCanonical = normCanonical;
      this.authorship = authorship;
    }
  }

  public static Result match(List<String> removed, List<String> added, int canonicalMaxDistance) {
    // Pass A: global exact-match healing. Any string present in both sides (by multiplicity) is
    // unchanged and dropped from all outputs, independent of iteration order.
    Map<String, Integer> availableAdded = new HashMap<>();
    for (String a : added) {
      availableAdded.merge(a, 1, Integer::sum);
    }
    Map<String, Integer> healed = new HashMap<>();
    List<String> removedRemain = new ArrayList<>();
    for (String r : removed) {
      int avail = availableAdded.getOrDefault(r, 0);
      if (avail > 0) {
        availableAdded.put(r, avail - 1);
        healed.merge(r, 1, Integer::sum);
      } else {
        removedRemain.add(r);
      }
    }
    List<String> addedRemain = new ArrayList<>();
    for (String a : added) {
      int h = healed.getOrDefault(a, 0);
      if (h > 0) {
        healed.put(a, h - 1); // this occurrence was healed against a removed twin
      } else {
        addedRemain.add(a);
      }
    }

    // Pass B: canonical-based pairing over the remainders. No identical pairs remain after Pass A.
    List<Candidate> rem = new ArrayList<>(removedRemain.size());
    for (String s : removedRemain) rem.add(parse(s));
    List<Candidate> add = new ArrayList<>(addedRemain.size());
    for (String s : addedRemain) add.add(parse(s));

    Map<String, List<Integer>> byBlock = new LinkedHashMap<>();
    for (int i = 0; i < add.size(); i++) {
      byBlock.computeIfAbsent(block(add.get(i).label), k -> new ArrayList<>()).add(i);
    }
    boolean[] usedAdded = new boolean[add.size()];

    List<ChangedName> changed = new ArrayList<>();
    List<String> removedLeft = new ArrayList<>();

    for (Candidate r : rem) {
      List<Integer> candidates = byBlock.getOrDefault(block(r.label), List.of());
      List<Integer> eligible = new ArrayList<>();
      for (int idx : candidates) {
        if (usedAdded[idx]) continue;
        int d = LevenshteinDistance.getDistance(r.normCanonical, add.get(idx).normCanonical);
        if (d <= canonicalMaxDistance) eligible.add(idx);
      }
      int chosen = choose(r, add, eligible, canonicalMaxDistance);
      if (chosen >= 0) {
        usedAdded[chosen] = true;
        Candidate a = add.get(chosen);
        changed.add(new ChangedName(r.label, a.label, NameChunker.chunks(r.label, a.label),
          DISPLAY_SIM.getSimilarity(r.label, a.label)));
      } else {
        removedLeft.add(r.label);
      }
    }

    List<String> addedLeft = new ArrayList<>();
    for (int i = 0; i < add.size(); i++) {
      if (!usedAdded[i]) addedLeft.add(add.get(i).label);
    }
    return new Result(changed, removedLeft, addedLeft);
  }

  /**
   * Picks the added candidate index to pair with the removed one, or -1 for none.
   * Task 2: pair only when exactly one candidate is eligible. Extended in Task 3.
   */
  private static int choose(Candidate r, List<Candidate> add, List<Integer> eligible, int canonicalMaxDistance) {
    return eligible.size() == 1 ? eligible.get(0) : -1;
  }

  /** Parses a label into its normalised canonical and authorship; falls back to the raw label if unparsable. */
  private static Candidate parse(String label) {
    var opt = NameParser.PARSER.parse(label);
    if (opt.isPresent()) {
      Name n = opt.get().getName();
      String canonical = n.getScientificName();
      if (canonical == null || canonical.isBlank()) {
        canonical = label;
      }
      return new Candidate(label, SciNameNormalizer.normalize(canonical), n.getCombinationAuthorship());
    }
    return new Candidate(label, SciNameNormalizer.normalize(label), null);
  }

  /** Blocking key: the first whitespace-delimited token (genus), or the whole string if no space. */
  static String block(String name) {
    int sp = name.indexOf(' ');
    return sp < 0 ? name : name.substring(0, sp);
  }
}
```

- [ ] **Step 4: Wire `NamesDiffEngine.assemble` to the new signature**

In `NamesDiffEngine.java`, delete line 3:

```java
import life.catalogue.matching.similarity.NormalizedLevenshtein;
```

and replace line 17:

```java
    ChangedMatcher.Result m = ChangedMatcher.match(removed, added, opts.getChangedThreshold(), new NormalizedLevenshtein());
```

with:

```java
    ChangedMatcher.Result m = ChangedMatcher.match(removed, added, opts.getCanonicalMaxDistance());
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn -q -pl dao -am test -Dtest=ChangedMatcherTest,DiffOptionsTest`
Expected: PASS. If `pairsCanonicalTypoWithinDistanceOne` fails, print the two normalised canonicals (the parser may stem `nisus`/`nisius` differently); the distance must be ≤ 1 — if the parser folds them to distance 0 the test still passes, if to ≥ 2 adjust the epithet in the test to a genuine single-character variant (e.g. `Accipiter nisus` → `Accipiter nisas`). Do **not** relax the distance limit.

- [ ] **Step 6: Commit**

```bash
git add dao/src/main/java/life/catalogue/printer/diff/ChangedMatcher.java \
        dao/src/main/java/life/catalogue/printer/diff/NamesDiffEngine.java \
        dao/src/test/java/life/catalogue/printer/diff/ChangedMatcherTest.java
git commit -m "feat(diff): pair authorship changes via normalised canonical distance"
```

---

### Task 3: Multiplicity tie-break so same-canonical synonyms are not mixed up

Extend `choose(...)` so that when several same-canonical candidates are eligible, the removed is paired with the best candidate whose author/year comparison is **not** `DIFFERENT`; if all are `DIFFERENT`, it stays unpaired. This keeps distinct synonyms (`Abax ellipticus` Cuvier/Porta/Schauberger; `Statice scoparia` two authors) apart, and picks the correct partner when one truly matches.

**Files:**
- Modify: `dao/src/main/java/life/catalogue/printer/diff/ChangedMatcher.java` (`choose`; add `authorEquality`)
- Test: `dao/src/test/java/life/catalogue/printer/diff/ChangedMatcherTest.java` (add multiplicity cases)

**Interfaces:**
- Consumes: `AuthorComparator.compare(Authorship, Authorship)` → `life.catalogue.matching.Equality` (`EQUAL` / `DIFFERENT` / `UNKNOWN`); `Authorship.isEmpty()`.
- Produces: no new public API; behaviour change in `match` for eligible sets larger than one.

- [ ] **Step 1: Add failing multiplicity tests**

Append these methods inside `ChangedMatcherTest` (before the closing brace):

```java
  @Test
  public void multipleSynonymsWithDistinctYearsStayApart() {
    // Same canonical, distinct years -> AuthorComparator DIFFERENT -> none paired.
    var r = ChangedMatcher.match(
      List.of("Abax ellipticus (Cuvier, 1833)", "Abax ellipticus Schauberger, 1927"),
      List.of("Abax ellipticus Porta, 1901", "Abax ellipticus Latreille, 1806"),
      1);
    assertTrue(r.changed().isEmpty());
    assertEquals(2, r.removed().size());
    assertEquals(2, r.added().size());
  }

  @Test
  public void multipleSynonymsPairTheMatchingAuthorOnly() {
    // Two eligible added; only one has a compatible author -> that one pairs, the other stays added.
    var r = ChangedMatcher.match(
      List.of("Statice scoparia Pall. ex Willd."),
      List.of("Statice scoparia Pallas ex Willdenow", "Statice scoparia C.A.Mey. ex Boiss."),
      1);
    assertEquals(1, r.changed().size());
    assertEquals("Statice scoparia Pall. ex Willd.", r.changed().get(0).before());
    assertEquals("Statice scoparia Pallas ex Willdenow", r.changed().get(0).after());
    assertEquals(List.of("Statice scoparia C.A.Mey. ex Boiss."), r.added());
    assertTrue(r.removed().isEmpty());
  }
```

- [ ] **Step 2: Run tests to verify the multiplicity ones fail**

Run: `mvn -q -pl dao -am test -Dtest=ChangedMatcherTest`
Expected: `multipleSynonymsPairTheMatchingAuthorOnly` FAILS (Task 2's `choose` returns -1 for >1 eligible, so nothing pairs). `multipleSynonymsWithDistinctYearsStayApart` already passes (conservative behaviour), which is fine.

- [ ] **Step 3: Extend `choose` and add `authorEquality`**

In `ChangedMatcher.java`, replace the `choose` method:

```java
  private static int choose(Candidate r, List<Candidate> add, List<Integer> eligible, int canonicalMaxDistance) {
    return eligible.size() == 1 ? eligible.get(0) : -1;
  }
```

with:

```java
  private static int choose(Candidate r, List<Candidate> add, List<Integer> eligible, int canonicalMaxDistance) {
    if (eligible.isEmpty()) return -1;
    if (eligible.size() == 1) return eligible.get(0); // 1-in-1-out: pair regardless of author (e.g. Mill.->L.)
    // Several same-canonical variants compete: pair only a non-DIFFERENT author/year, best first.
    int best = -1;
    int bestScore = Integer.MIN_VALUE;
    for (int idx : eligible) {
      Candidate a = add.get(idx);
      Equality eq = authorEquality(r.authorship, a.authorship);
      if (eq == Equality.DIFFERENT) continue;
      int d = LevenshteinDistance.getDistance(r.normCanonical, a.normCanonical);
      int score = (canonicalMaxDistance - d) * 10 + (eq == Equality.EQUAL ? 2 : 1);
      if (score > bestScore) {
        bestScore = score;
        best = idx;
      }
    }
    return best;
  }

  /** Author/year comparison, treating a missing authorship on either side as UNKNOWN (poolable, not DIFFERENT). */
  private static Equality authorEquality(Authorship a1, Authorship a2) {
    if (a1 == null || a2 == null || a1.isEmpty() || a2.isEmpty()) {
      return Equality.UNKNOWN;
    }
    return AUTHOR_CMP.compare(a1, a2);
  }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -pl dao -am test -Dtest=ChangedMatcherTest`
Expected: PASS. If `multipleSynonymsPairTheMatchingAuthorOnly` fails because `AuthorComparator` rates `Pall. ex Willd.` vs `Pallas ex Willdenow` as `DIFFERENT`, log both comparisons; the correct partner only needs to be *not* `DIFFERENT` while `C.A.Mey. ex Boiss.` is `DIFFERENT`. If the abbreviation expansion is not recognised, substitute a clearer compatible pair in the test (e.g. added `"Statice scoparia Willd."` which shares the author token) — keep the DIFFERENT distractor.

- [ ] **Step 5: Run the wider diff test suite for regressions**

Run: `mvn -q -pl dao -am test -Dtest=ChangedMatcherTest,DiffOptionsTest,NameChunkerTest,StreamingMergeDiffEngineTest,MyersDiffEngineTest,DiffEngineCrossCheckTest,NamesDiffModelTest`
Expected: PASS. `DiffEngineCrossCheckTest` confirms the streaming and Myers engines still agree through `assemble`.

- [ ] **Step 6: Commit**

```bash
git add dao/src/main/java/life/catalogue/printer/diff/ChangedMatcher.java \
        dao/src/test/java/life/catalogue/printer/diff/ChangedMatcherTest.java
git commit -m "feat(diff): author/year tie-break keeps same-canonical synonyms apart"
```

---

## Self-review notes

- **Spec coverage:** decisions 1 (replace fuzzy pass) → Task 2; 2 (`SciNameNormalizer.normalize`) → Task 2 `parse`; 3 (distance ≤ 1) → Tasks 1–2; 4 (1-in-1-out) → Task 2 `choose`; 5 (author/year tie-break) → Task 3; 6 (file format unchanged) → no file/writer changes in any task. Worked-outcomes table rows each have a test (short add, replace, congener, Abax multi, Statice multi, typo).
- **Follow-ups not in scope (from spec "Open questions"):** `yearDifferenceAllowed` uses `AuthorComparator`'s built-in 11-year window; greedy within-block order is accepted. No config surface added beyond `canonicalMaxDistance`.
- **Integration tests:** `BaseDiffServiceIT` / `DatasetDiffServiceIT` / `SectorDiffServiceIT` are DB-backed and unaffected by signature changes (they go through `assemble`); run them in CI, not required for these unit-level tasks.
