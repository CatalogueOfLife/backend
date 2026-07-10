# Names Diff Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `BaseDiffService`'s GNU `diff`/`sort` subprocesses with a pluggable in‑JVM diff engine that returns a structured `NamesDiff` object (added / removed / changed names, changed names carrying marked chunks).

**Architecture:** A `NamesDiffEngine` interface with two implementations. `StreamingMergeDiffEngine` merge‑joins two sorted name streams to find added/removed (O(1) memory over the common set, scales to 10M), then a shared second pass pairs similar removed↔added into "changed" and marks the differing characters. `MyersDiffEngine` (java‑diff‑utils, size‑guarded) is a whole‑list cross‑check. The dataset‑to‑dataset path is replaced by a pure‑SQL name generator that emits labels sorted by Postgres C collation (byte order), streamed via a forward‑only cursor.

**Tech Stack:** Java 21, MyBatis, Postgres 17 (`LC_COLLATE 'C'`), `io.github.java-diff-utils:java-diff-utils:4.12` (already a `dao` dependency), existing `life.catalogue.matching.similarity.*` utils, JUnit 4 + TestContainers (`DaoTestBase`).

## Global Constraints

- Java 21. No Panama/FFM. No new dependencies (`java-diff-utils` and the similarity utils are already on the `dao` classpath).
- No OS `Process` anywhere in the diff paths (no `diff`, no `sort`).
- Engine must not hold whole name lists in JVM memory; only the differences (streaming engine).
- Sort/compare order is **UTF‑8 byte order == Unicode code‑point order** (matches Postgres `LC_COLLATE 'C'`, `PgUtils.java:98`). Never use `String.compareTo` or `java.text.Collator` for the merge order.
- Similarity scores are on a **0..100** scale (`StringSimilarity.getSimilarity`, 100 = identical). `changedThreshold` and `ChangedName.similarity` use the same 0..100 scale. (This corrects the spec's "0..1" wording.)
- All new production classes live in package `life.catalogue.printer.diff` in the **dao** module, except the evolved `NamesDiff` which stays in `life.catalogue.printer` (already imported elsewhere).
- Code style: 2‑space indent, 140 col, `@Nullable`/`@VisibleForTesting` as used in the repo.
- API change is a **replacement**: the diff endpoints return `application/json` `NamesDiff`; the udiff text, `diffBinaryVersion`, and `DiffHealthCheck` are removed.

## File Structure

**Create (dao/src/main/java/life/catalogue/printer/diff/):**
- `ChunkOp.java` — enum EQUAL/DELETE/INSERT
- `Chunk.java` — record `(ChunkOp op, String text)`
- `ChangedName.java` — record `(String before, String after, List<Chunk> chunks, double similarity)`
- `DiffInput.java` — record `(String label, Supplier<Stream<String>> lines)`
- `DiffOptions.java` — options + the code‑point comparator
- `NamesDiffEngine.java` — interface + shared static `assemble(...)`
- `NameChunker.java` — char‑level marking via java‑diff‑utils
- `ChangedMatcher.java` — pass‑2 blocking / pairing / healing
- `StreamingMergeDiffEngine.java`
- `MyersDiffEngine.java`
- `DiffNamesParam.java` — params for the SQL dataset name generator

**Modify:**
- `dao/.../printer/NamesDiff.java` — evolve to the JSON payload
- `dao/.../printer/BaseDiffService.java` — remove udiff/process; `diff()` via engine
- `dao/.../printer/SectorDiffService.java` — constructor signature
- `dao/.../printer/DatasetDiffService.java` — pure‑SQL generator + engine
- `dao/.../db/mapper/NameUsageMapper.java` + `NameUsageMapper.xml` — dataset name generator
- `webservice/.../resources/dataset/AbstractDiffResource.java`, `DatasetDiffResource.java` — JSON
- `webservice/.../WsServer.java` — service constructor args + drop `DiffHealthCheck` registration

**Delete:**
- `webservice/.../dw/health/DiffHealthCheck.java`

**Create tests (dao/src/test/java/life/catalogue/printer/...):**
- `diff/NamesDiffModelTest.java`, `diff/DiffOptionsTest.java`, `diff/NameChunkerTest.java`, `diff/ChangedMatcherTest.java`, `diff/StreamingMergeDiffEngineTest.java`, `diff/MyersDiffEngineTest.java`, `diff/DiffEngineCrossCheckTest.java`, `DatasetNamesGeneratorIT.java`

**Task order note:** the SQL generator (Task 7) lands *before* the service rewrite (Task 8) because the new `DatasetDiffService` calls it. Tasks 7 removes nothing, so `dao` keeps compiling; Task 8 rewrites `BaseDiffService` **and** `DatasetDiffService` **and** `SectorDiffService` **in one commit**, because removing `udiff()` from the base class breaks the old `DatasetDiffService` until it too is rewritten.

---

## Task 1: Diff model types (`ChunkOp`, `Chunk`, `ChangedName`) and evolve `NamesDiff`

**Files:**
- Create: `dao/src/main/java/life/catalogue/printer/diff/ChunkOp.java`
- Create: `dao/src/main/java/life/catalogue/printer/diff/Chunk.java`
- Create: `dao/src/main/java/life/catalogue/printer/diff/ChangedName.java`
- Modify: `dao/src/main/java/life/catalogue/printer/NamesDiff.java` (full rewrite)
- Test: `dao/src/test/java/life/catalogue/printer/diff/NamesDiffModelTest.java`

**Interfaces:**
- Produces:
  - `enum ChunkOp { EQUAL, DELETE, INSERT }`
  - `record Chunk(ChunkOp op, String text)`
  - `record ChangedName(String before, String after, List<Chunk> chunks, double similarity)`
  - `class NamesDiff` with ctor `NamesDiff(String label1, String label2)`; getters `getLabel1/2()`, `getRemoved()`, `getAdded()`, `getChanged()` (all `List`), `isTruncated()`/`setTruncated(boolean)`; derived `getRemovedCount()/getAddedCount()/getChangedCount()`; `isIdentical()`.

- [ ] **Step 1: Write the failing test**

`dao/src/test/java/life/catalogue/printer/diff/NamesDiffModelTest.java`:
```java
package life.catalogue.printer.diff;

import life.catalogue.printer.NamesDiff;
import life.catalogue.api.jackson.ApiModule;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

public class NamesDiffModelTest {

  @Test
  public void counts() {
    NamesDiff d = new NamesDiff("a", "b");
    assertTrue(d.isIdentical());
    d.getRemoved().add("Aus aus");
    d.getAdded().add("Bus bus");
    d.getChanged().add(new ChangedName("Cus cus L.", "Cus cus L., 1758",
      List.of(new Chunk(ChunkOp.EQUAL, "Cus cus L."), new Chunk(ChunkOp.INSERT, ", 1758")), 92.0));
    assertFalse(d.isIdentical());
    assertEquals(1, d.getRemovedCount());
    assertEquals(1, d.getAddedCount());
    assertEquals(1, d.getChangedCount());
  }

  @Test
  public void jsonRoundtrips() throws Exception {
    NamesDiff d = new NamesDiff("dataset_3#1", "dataset_3#2");
    d.getAdded().add("Bus bus");
    d.getChanged().add(new ChangedName("Cus cus", "Cus cvs",
      List.of(new Chunk(ChunkOp.EQUAL, "Cus c"), new Chunk(ChunkOp.DELETE, "u"),
              new Chunk(ChunkOp.INSERT, "v"), new Chunk(ChunkOp.EQUAL, "s")), 88.0));
    var mapper = ApiModule.MAPPER;
    String json = mapper.writeValueAsString(d);
    assertTrue(json.contains("\"added\""));
    assertTrue(json.contains("\"chunks\""));
    assertTrue(json.contains("INSERT"));
    NamesDiff back = mapper.readValue(json, NamesDiff.class);
    assertEquals(1, back.getAddedCount());
    assertEquals(1, back.getChangedCount());
    assertEquals("Cus cvs", back.getChanged().get(0).after());
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd dao && mvn -q -Dtest=NamesDiffModelTest test`
Expected: FAIL — `ChunkOp`/`Chunk`/`ChangedName` do not exist; `NamesDiff` has no `getRemoved()`.

- [ ] **Step 3: Create the model types**

`ChunkOp.java`:
```java
package life.catalogue.printer.diff;

public enum ChunkOp {
  EQUAL, DELETE, INSERT
}
```

`Chunk.java`:
```java
package life.catalogue.printer.diff;

/**
 * A contiguous segment of a changed name. EQUAL text is unchanged; DELETE text exists only in the
 * old name (before); INSERT text exists only in the new name (after). The old name is the
 * concatenation of EQUAL+DELETE chunks, the new name the concatenation of EQUAL+INSERT chunks.
 */
public record Chunk(ChunkOp op, String text) {}
```

`ChangedName.java`:
```java
package life.catalogue.printer.diff;

import java.util.List;

/**
 * A name present on both sides but not identical: renamed, re-authored, etc.
 * @param similarity 0..100, 100 = identical (see StringSimilarity)
 */
public record ChangedName(String before, String after, List<Chunk> chunks, double similarity) {}
```

- [ ] **Step 4: Rewrite `NamesDiff`**

`dao/src/main/java/life/catalogue/printer/NamesDiff.java` (replace entire file):
```java
package life.catalogue.printer;

import life.catalogue.printer.diff.ChangedName;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured diff between two sorted name lists.
 * label1/label2 identify the two sides (e.g. "dataset_3#1" for an import attempt or "dataset_3" for a dataset key).
 * removed = present in side 1 but not side 2; added = present in side 2 but not side 1;
 * changed = pairs of removed/added names that are similar enough to be treated as a modification.
 */
public class NamesDiff {
  private final String label1;
  private final String label2;
  private final List<String> removed = new ArrayList<>();
  private final List<String> added = new ArrayList<>();
  private final List<ChangedName> changed = new ArrayList<>();
  private boolean truncated = false;

  public NamesDiff(String label1, String label2) {
    this.label1 = label1;
    this.label2 = label2;
  }

  public String getLabel1() { return label1; }
  public String getLabel2() { return label2; }

  public List<String> getRemoved() { return removed; }
  public List<String> getAdded() { return added; }
  public List<ChangedName> getChanged() { return changed; }

  public int getRemovedCount() { return removed.size(); }
  public int getAddedCount() { return added.size(); }
  public int getChangedCount() { return changed.size(); }

  public boolean isTruncated() { return truncated; }
  public void setTruncated(boolean truncated) { this.truncated = truncated; }

  public boolean isIdentical() {
    return removed.isEmpty() && added.isEmpty() && changed.isEmpty();
  }

  @Override
  public String toString() {
    return "NamesDiff{" + label1 + " vs " + label2 +
      ", removed=" + removed.size() + ", added=" + added.size() + ", changed=" + changed.size() +
      (truncated ? ", truncated" : "") + '}';
  }
}
```
Note: Jackson (via `ApiModule.MAPPER`) serialises the public getters and deserialises records by their canonical constructor; the `getRemoved()/getAdded()/getChanged()` lists are mutable so both the engine and the deserialiser populate them. If `ApiModule.MAPPER` cannot deserialise, annotate the getters with `@JsonProperty` and add `@JsonCreator public NamesDiff(@JsonProperty("label1") String l1, @JsonProperty("label2") String l2)` — the round‑trip test in Step 1 is the gate.

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd dao && mvn -q -Dtest=NamesDiffModelTest test`
Expected: PASS (both tests).

- [ ] **Step 6: Commit**

```bash
git add dao/src/main/java/life/catalogue/printer/diff/ChunkOp.java \
        dao/src/main/java/life/catalogue/printer/diff/Chunk.java \
        dao/src/main/java/life/catalogue/printer/diff/ChangedName.java \
        dao/src/main/java/life/catalogue/printer/NamesDiff.java \
        dao/src/test/java/life/catalogue/printer/diff/NamesDiffModelTest.java
git commit -m "feat(diff): structured NamesDiff model with added/removed/changed chunks"
```

---

## Task 2: `DiffInput`, `DiffOptions` and the code‑point comparator

**Files:**
- Create: `dao/src/main/java/life/catalogue/printer/diff/DiffInput.java`
- Create: `dao/src/main/java/life/catalogue/printer/diff/DiffOptions.java`
- Test: `dao/src/test/java/life/catalogue/printer/diff/DiffOptionsTest.java`

**Interfaces:**
- Produces:
  - `record DiffInput(String label, java.util.function.Supplier<java.util.stream.Stream<String>> lines)`
  - `class DiffOptions` with `public static final Comparator<String> CODEPOINT`; getters/setters `getOrder()/setOrder`, `getChangedThreshold()/setChangedThreshold`, `getMaxItems()/setMaxItems`, `getMaxChangedCandidates()/setMaxChangedCandidates`; `static DiffOptions defaults()`. Defaults: order=CODEPOINT, changedThreshold=50.0, maxItems=0, maxChangedCandidates=20_000_000L. Setters return `this`.

- [ ] **Step 1: Write the failing test**

`DiffOptionsTest.java`:
```java
package life.catalogue.printer.diff;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

public class DiffOptionsTest {

  @Test
  public void defaults() {
    DiffOptions o = DiffOptions.defaults();
    assertEquals(50.0, o.getChangedThreshold(), 0.0001);
    assertEquals(0, o.getMaxItems());
    assertSame(DiffOptions.CODEPOINT, o.getOrder());
  }

  @Test
  public void codepointOrderMatchesByteOrder() {
    // C collation / byte order: uppercase before lowercase; space (0x20) before letters
    List<String> in = new ArrayList<>(List.of("Zea", "aus", "Aus bus", "Aus", "Aus aus"));
    in.sort(DiffOptions.CODEPOINT);
    assertEquals(List.of("Aus", "Aus aus", "Aus bus", "Zea", "aus"), in);
  }

  @Test
  public void codepointHandlesNonAscii() {
    // 'Z' (0x5A) < 'É' (U+00C9); ASCII sorts before Latin-1 supplement
    assertTrue(DiffOptions.CODEPOINT.compare("Zea", "Ébre") < 0);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd dao && mvn -q -Dtest=DiffOptionsTest test`
Expected: FAIL — `DiffOptions` not found.

- [ ] **Step 3: Create `DiffInput`**

```java
package life.catalogue.printer.diff;

import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A labelled, re-openable source of name labels, one per line.
 * For merge-based engines the stream MUST be sorted by DiffOptions.order.
 * The engine closes each Stream it obtains (try-with-resources), which releases any underlying
 * file handle or DB cursor/session via the stream's onClose.
 */
public record DiffInput(String label, Supplier<Stream<String>> lines) {}
```

- [ ] **Step 4: Create `DiffOptions` with the comparator**

```java
package life.catalogue.printer.diff;

import java.util.Comparator;

public class DiffOptions {

  /**
   * Compares by Unicode code point, which equals UTF-8 byte order for well-formed text and thus
   * matches Postgres LC_COLLATE 'C'. NOT String.compareTo (UTF-16 code-unit order differs for
   * supplementary characters).
   */
  public static final Comparator<String> CODEPOINT = (a, b) -> {
    int i = 0, j = 0;
    final int la = a.length(), lb = b.length();
    while (i < la && j < lb) {
      int ca = a.codePointAt(i);
      int cb = b.codePointAt(j);
      if (ca != cb) {
        return Integer.compare(ca, cb);
      }
      i += Character.charCount(ca);
      j += Character.charCount(cb);
    }
    return Integer.compare(la - i, lb - j);
  };

  private Comparator<String> order = CODEPOINT;
  private double changedThreshold = 50.0;   // 0..100
  private int maxItems = 0;                  // 0 = unlimited output per list
  private long maxChangedCandidates = 20_000_000L; // OOM backstop for pass-1 buffers

  public static DiffOptions defaults() {
    return new DiffOptions();
  }

  public Comparator<String> getOrder() { return order; }
  public DiffOptions setOrder(Comparator<String> order) { this.order = order; return this; }

  public double getChangedThreshold() { return changedThreshold; }
  public DiffOptions setChangedThreshold(double t) { this.changedThreshold = t; return this; }

  public int getMaxItems() { return maxItems; }
  public DiffOptions setMaxItems(int maxItems) { this.maxItems = maxItems; return this; }

  public long getMaxChangedCandidates() { return maxChangedCandidates; }
  public DiffOptions setMaxChangedCandidates(long v) { this.maxChangedCandidates = v; return this; }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd dao && mvn -q -Dtest=DiffOptionsTest test`
Expected: PASS (all three).

- [ ] **Step 6: Commit**

```bash
git add dao/src/main/java/life/catalogue/printer/diff/DiffInput.java \
        dao/src/main/java/life/catalogue/printer/diff/DiffOptions.java \
        dao/src/test/java/life/catalogue/printer/diff/DiffOptionsTest.java
git commit -m "feat(diff): DiffInput + DiffOptions with code-point (C collation) comparator"
```

---

## Task 3: `NameChunker` — intra‑name marking via java‑diff‑utils

**Files:**
- Create: `dao/src/main/java/life/catalogue/printer/diff/NameChunker.java`
- Test: `dao/src/test/java/life/catalogue/printer/diff/NameChunkerTest.java`

**Interfaces:**
- Consumes: `Chunk`, `ChunkOp` (Task 1).
- Produces: `class NameChunker` with `static List<Chunk> chunks(String before, String after)`. Adjacent chunks of the same op are merged; EQUAL+DELETE text equals `before`, EQUAL+INSERT equals `after`.

- [ ] **Step 1: Write the failing test**

`NameChunkerTest.java`:
```java
package life.catalogue.printer.diff;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

public class NameChunkerTest {

  private static String reconstruct(List<Chunk> chunks, boolean before) {
    StringBuilder sb = new StringBuilder();
    for (Chunk c : chunks) {
      if (c.op() == ChunkOp.EQUAL || c.op() == (before ? ChunkOp.DELETE : ChunkOp.INSERT)) {
        sb.append(c.text());
      }
    }
    return sb.toString();
  }

  @Test
  public void authorshipAppended() {
    List<Chunk> chunks = NameChunker.chunks("Cus cus L.", "Cus cus L., 1758");
    assertEquals("Cus cus L.", reconstruct(chunks, true));
    assertEquals("Cus cus L., 1758", reconstruct(chunks, false));
    assertTrue(chunks.stream().anyMatch(c -> c.op() == ChunkOp.INSERT && c.text().contains("1758")));
  }

  @Test
  public void midWordChange() {
    List<Chunk> chunks = NameChunker.chunks("Cus cus", "Cus cvs");
    assertEquals("Cus cus", reconstruct(chunks, true));
    assertEquals("Cus cvs", reconstruct(chunks, false));
    assertTrue(chunks.stream().anyMatch(c -> c.op() == ChunkOp.DELETE && c.text().equals("u")));
    assertTrue(chunks.stream().anyMatch(c -> c.op() == ChunkOp.INSERT && c.text().equals("v")));
  }

  @Test
  public void identical() {
    List<Chunk> chunks = NameChunker.chunks("Aus aus", "Aus aus");
    assertEquals(1, chunks.size());
    assertEquals(ChunkOp.EQUAL, chunks.get(0).op());
    assertEquals("Aus aus", chunks.get(0).text());
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd dao && mvn -q -Dtest=NameChunkerTest test`
Expected: FAIL — `NameChunker` not found.

- [ ] **Step 3: Implement `NameChunker`**

```java
package life.catalogue.printer.diff;

import java.util.ArrayList;
import java.util.List;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;

/**
 * Computes a character-level diff of two short name labels and returns ordered EQUAL/DELETE/INSERT
 * chunks. Uses java-diff-utils with includeEqualParts=true so equal runs are returned too.
 */
public class NameChunker {

  public static List<Chunk> chunks(String before, String after) {
    List<Character> a = toChars(before);
    List<Character> b = toChars(after);
    Patch<Character> patch = DiffUtils.diff(a, b, true); // includeEqualParts
    List<Chunk> out = new ArrayList<>();
    for (AbstractDelta<Character> d : patch.getDeltas()) {
      switch (d.getType()) {
        case EQUAL -> append(out, ChunkOp.EQUAL, d.getSource().getLines());
        case DELETE -> append(out, ChunkOp.DELETE, d.getSource().getLines());
        case INSERT -> append(out, ChunkOp.INSERT, d.getTarget().getLines());
        case CHANGE -> {
          append(out, ChunkOp.DELETE, d.getSource().getLines());
          append(out, ChunkOp.INSERT, d.getTarget().getLines());
        }
      }
    }
    return merge(out);
  }

  private static List<Character> toChars(String s) {
    List<Character> list = new ArrayList<>(s.length());
    for (int i = 0; i < s.length(); i++) {
      list.add(s.charAt(i));
    }
    return list;
  }

  private static void append(List<Chunk> out, ChunkOp op, List<Character> chars) {
    if (chars.isEmpty()) return;
    StringBuilder sb = new StringBuilder(chars.size());
    for (Character c : chars) sb.append(c.charValue());
    out.add(new Chunk(op, sb.toString()));
  }

  private static List<Chunk> merge(List<Chunk> in) {
    List<Chunk> out = new ArrayList<>(in.size());
    for (Chunk c : in) {
      if (!out.isEmpty() && out.get(out.size() - 1).op() == c.op()) {
        Chunk prev = out.remove(out.size() - 1);
        out.add(new Chunk(c.op(), prev.text() + c.text()));
      } else {
        out.add(c);
      }
    }
    return out;
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd dao && mvn -q -Dtest=NameChunkerTest test`
Expected: PASS (all three).

- [ ] **Step 5: Commit**

```bash
git add dao/src/main/java/life/catalogue/printer/diff/NameChunker.java \
        dao/src/test/java/life/catalogue/printer/diff/NameChunkerTest.java
git commit -m "feat(diff): NameChunker character-level marking via java-diff-utils"
```

---

## Task 4: `ChangedMatcher` — pass‑2 pairing, healing, and shared assembly

**Files:**
- Create: `dao/src/main/java/life/catalogue/printer/diff/ChangedMatcher.java`
- Create: `dao/src/main/java/life/catalogue/printer/diff/NamesDiffEngine.java`
- Test: `dao/src/test/java/life/catalogue/printer/diff/ChangedMatcherTest.java`

> **DECISION (2026-07-10):** the "changed" similarity metric is **normalized whole-string
> Levenshtein**, NOT `ScientificNameSimilarity`. A new `NormalizedLevenshtein implements
> StringSimilarity` (in `life.catalogue.matching.similarity`) returns
> `100 * (1 - LevenshteinDistance.getDistance(a,b) / max(len))`. Reason: `ScientificNameSimilarity`
> and the tuned `DistanceUtils` score authorship/year *additions* at 0 (they penalise multi-char
> additions), which are the most common real change; normalized Levenshtein scores them 60–95.

**Interfaces:**
- Consumes: `NameChunker` (Task 3), `ChangedName` (Task 1), `StringSimilarity` + new `NormalizedLevenshtein` (`life.catalogue.matching.similarity`), `LevenshteinDistance.getDistance`.
- Produces:
  - `class NormalizedLevenshtein implements StringSimilarity` in `life.catalogue.matching.similarity` with `getSimilarity(x1,x2) = 100*(1 - getDistance/max(len))` (equal strings → 100; both empty → 100).
  - `class ChangedMatcher` with `record Result(List<ChangedName> changed, List<String> removed, List<String> added)` and `static Result match(List<String> removed, List<String> added, double threshold, StringSimilarity similarity)`.
  - `interface NamesDiffEngine { NamesDiff diff(DiffInput a, DiffInput b, DiffOptions opts); }` plus `static NamesDiff assemble(String label1, String label2, List<String> removed, List<String> added, DiffOptions opts)` (uses `new NormalizedLevenshtein()`).

- [ ] **Step 1: Write the failing test**

`ChangedMatcherTest.java`:
```java
package life.catalogue.printer.diff;

import life.catalogue.matching.similarity.NormalizedLevenshtein;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

public class ChangedMatcherTest {

  private static final NormalizedLevenshtein SIM = new NormalizedLevenshtein();

  @Test
  public void pairsSimilarNames() {
    var r = ChangedMatcher.match(
      List.of("Abies alba", "Zea mays L."),
      List.of("Abies alba Mill.", "Quercus robur"),
      50.0, SIM);
    assertEquals(1, r.changed().size());
    assertEquals("Abies alba", r.changed().get(0).before());
    assertEquals("Abies alba Mill.", r.changed().get(0).after());
    assertEquals(List.of("Zea mays L."), r.removed());
    assertEquals(List.of("Quercus robur"), r.added());
  }

  @Test
  public void identicalPairsAreHealedNotChanged() {
    var r = ChangedMatcher.match(List.of("Aus aus"), List.of("Aus aus"), 50.0, SIM);
    assertTrue(r.changed().isEmpty());
    assertTrue(r.removed().isEmpty());
    assertTrue(r.added().isEmpty());
  }

  @Test
  public void differentGenusNotPaired() {
    var r = ChangedMatcher.match(List.of("Aus aus"), List.of("Zea mays"), 50.0, SIM);
    assertTrue(r.changed().isEmpty());
    assertEquals(List.of("Aus aus"), r.removed());
    assertEquals(List.of("Zea mays"), r.added());
  }

  @Test
  public void assembleTruncates() {
    NamesDiff d = NamesDiffEngine.assemble("a", "b",
      new java.util.ArrayList<>(List.of("A a", "B b", "C c")),
      new java.util.ArrayList<>(List.of("X x", "Y y")),
      DiffOptions.defaults().setMaxItems(2));
    assertTrue(d.isTruncated());
    assertEquals(2, d.getRemoved().size());
    assertEquals(2, d.getAdded().size());
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd dao && mvn -q -Dtest=ChangedMatcherTest test`
Expected: FAIL — `ChangedMatcher` / `NamesDiffEngine` not found.

- [ ] **Step 3: Implement `ChangedMatcher`**

```java
package life.catalogue.printer.diff;

import life.catalogue.matching.similarity.StringSimilarity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pass 2 of the diff: pairs "removed" and "added" candidates that are similar enough to be a single
 * modification rather than an unrelated delete + insert. Candidates are blocked by their first token
 * (genus) so matching is near-linear instead of full quadratic. Identical pairs (similarity 100) are
 * "healed": dropped from all outputs, since an identical string on both sides is not a change (this
 * repairs rare local sort-order glitches from the attempts path).
 */
public class ChangedMatcher {

  public record Result(List<ChangedName> changed, List<String> removed, List<String> added) {}

  public static Result match(List<String> removed, List<String> added, double threshold, StringSimilarity similarity) {
    Map<String, List<Integer>> byBlock = new LinkedHashMap<>();
    for (int i = 0; i < added.size(); i++) {
      byBlock.computeIfAbsent(block(added.get(i)), k -> new ArrayList<>()).add(i);
    }
    boolean[] usedAdded = new boolean[added.size()];

    List<ChangedName> changed = new ArrayList<>();
    List<String> removedLeft = new ArrayList<>();

    for (String r : removed) {
      List<Integer> candidates = byBlock.getOrDefault(block(r), List.of());
      int bestIdx = -1;
      double bestSim = -1;
      boolean bestIdentical = false;
      for (int idx : candidates) {
        if (usedAdded[idx]) continue;
        String a = added.get(idx);
        if (r.equals(a)) { bestIdx = idx; bestIdentical = true; break; }
        double s = similarity.getSimilarity(r, a);
        if (s > bestSim) { bestSim = s; bestIdx = idx; }
      }
      if (bestIdx >= 0 && bestIdentical) {
        usedAdded[bestIdx] = true;           // healed: drop both, not a change
      } else if (bestIdx >= 0 && bestSim >= threshold) {
        usedAdded[bestIdx] = true;
        String a = added.get(bestIdx);
        changed.add(new ChangedName(r, a, NameChunker.chunks(r, a), bestSim));
      } else {
        removedLeft.add(r);
      }
    }

    List<String> addedLeft = new ArrayList<>();
    for (int i = 0; i < added.size(); i++) {
      if (!usedAdded[i]) addedLeft.add(added.get(i));
    }
    return new Result(changed, removedLeft, addedLeft);
  }

  /** Blocking key: the first whitespace-delimited token (genus), or the whole string if no space. */
  static String block(String name) {
    int sp = name.indexOf(' ');
    return sp < 0 ? name : name.substring(0, sp);
  }
}
```

- [ ] **Step 4: Implement `NamesDiffEngine` interface + `assemble`**

```java
package life.catalogue.printer.diff;

import life.catalogue.matching.similarity.NormalizedLevenshtein;
import life.catalogue.printer.NamesDiff;

import java.util.List;

public interface NamesDiffEngine {

  NamesDiff diff(DiffInput a, DiffInput b, DiffOptions opts);

  /**
   * Shared pass-2 + truncation used by all engines. Runs ChangedMatcher over the raw removed/added
   * candidate lists, then caps each output list at opts.maxItems (0 = unlimited), flagging truncation.
   */
  static NamesDiff assemble(String label1, String label2, List<String> removed, List<String> added, DiffOptions opts) {
    ChangedMatcher.Result m = ChangedMatcher.match(removed, added, opts.getChangedThreshold(), new NormalizedLevenshtein());
    NamesDiff diff = new NamesDiff(label1, label2);
    boolean truncated = false;
    truncated |= addCapped(diff.getRemoved(), m.removed(), opts.getMaxItems());
    truncated |= addCapped(diff.getAdded(), m.added(), opts.getMaxItems());
    truncated |= addCapped(diff.getChanged(), m.changed(), opts.getMaxItems());
    diff.setTruncated(truncated);
    return diff;
  }

  private static <T> boolean addCapped(List<T> target, List<T> src, int maxItems) {
    if (maxItems <= 0 || src.size() <= maxItems) {
      target.addAll(src);
      return false;
    }
    target.addAll(src.subList(0, maxItems));
    return true;
  }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd dao && mvn -q -Dtest=ChangedMatcherTest test`
Expected: PASS (all four).

- [ ] **Step 6: Commit**

```bash
git add dao/src/main/java/life/catalogue/printer/diff/ChangedMatcher.java \
        dao/src/main/java/life/catalogue/printer/diff/NamesDiffEngine.java \
        dao/src/test/java/life/catalogue/printer/diff/ChangedMatcherTest.java
git commit -m "feat(diff): ChangedMatcher pass-2 pairing/healing + shared assemble"
```

---

## Task 5: `StreamingMergeDiffEngine` — merge‑join pass 1

**Files:**
- Create: `dao/src/main/java/life/catalogue/printer/diff/StreamingMergeDiffEngine.java`
- Test: `dao/src/test/java/life/catalogue/printer/diff/StreamingMergeDiffEngineTest.java`

**Interfaces:**
- Consumes: `NamesDiffEngine`, `DiffInput`, `DiffOptions`, `NamesDiff` (Tasks 1–4).
- Produces: `class StreamingMergeDiffEngine implements NamesDiffEngine` (no‑arg ctor). Throws `IllegalStateException` (message contains "sorted") when pass‑1 candidate buffers exceed `opts.maxChangedCandidates`.

- [ ] **Step 1: Write the failing test**

`StreamingMergeDiffEngineTest.java`:
```java
package life.catalogue.printer.diff;

import life.catalogue.printer.NamesDiff;

import java.util.List;
import java.util.stream.Stream;

import org.junit.Test;

import static org.junit.Assert.*;

public class StreamingMergeDiffEngineTest {

  private final StreamingMergeDiffEngine engine = new StreamingMergeDiffEngine();

  private static DiffInput input(String label, String... lines) {
    return new DiffInput(label, () -> Stream.of(lines));
  }

  @Test
  public void addRemoveChange() {
    DiffInput a = input("a", "Abies alba", "Quercus robur", "Zea mays");
    DiffInput b = input("b", "Abies alba Mill.", "Quercus robur", "Zea mays");
    NamesDiff d = engine.diff(a, b, DiffOptions.defaults());
    assertEquals(1, d.getChangedCount());
    assertEquals("Abies alba Mill.", d.getChanged().get(0).after());
    assertEquals(0, d.getRemovedCount());
    assertEquals(0, d.getAddedCount());
  }

  @Test
  public void pureAddAndRemove() {
    DiffInput a = input("a", "Aus aus", "Cus cus");
    DiffInput b = input("b", "Bus bus", "Cus cus");
    NamesDiff d = engine.diff(a, b, DiffOptions.defaults());
    assertEquals(List.of("Aus aus"), d.getRemoved());
    assertEquals(List.of("Bus bus"), d.getAdded());
    assertEquals(0, d.getChangedCount());
  }

  @Test
  public void identical() {
    DiffInput a = input("a", "Aus aus", "Bus bus");
    DiffInput b = input("b", "Aus aus", "Bus bus");
    assertTrue(engine.diff(a, b, DiffOptions.defaults()).isIdentical());
  }

  @Test
  public void localInversionIsHealed() {
    // side1 has a local inversion vs code-point order; the merge produces a spurious remove+add of
    // the same string, healed by pass 2.
    DiffInput a = input("a", "Aus", "Aus zz", "Aus b a");
    DiffInput b = input("b", "Aus", "Aus b a", "Aus zz");
    NamesDiff d = engine.diff(a, b, DiffOptions.defaults());
    assertTrue("expected identical after healing but was " + d, d.isIdentical());
  }

  @Test
  public void candidateCapTrips() {
    DiffInput a = input("a", "A", "B", "C", "D");
    DiffInput b = input("b", "E", "F", "G", "H");
    DiffOptions opts = DiffOptions.defaults().setMaxChangedCandidates(3);
    try {
      engine.diff(a, b, opts);
      fail("expected candidate cap to trip");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().toLowerCase().contains("sorted"));
    }
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd dao && mvn -q -Dtest=StreamingMergeDiffEngineTest test`
Expected: FAIL — `StreamingMergeDiffEngine` not found.

- [ ] **Step 3: Implement `StreamingMergeDiffEngine`**

```java
package life.catalogue.printer.diff;

import life.catalogue.printer.NamesDiff;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Merge-join over two byte-ordered name streams. Pass 1 finds added/removed with O(1) memory over the
 * common set (only differences are buffered). Pass 2 (NamesDiffEngine.assemble) pairs similar
 * candidates into changed names. Local sort-order glitches surface as identical remove+add pairs and
 * are healed in pass 2; a runaway candidate count (comparator not matching the input order) trips
 * maxChangedCandidates and fails fast.
 */
public class StreamingMergeDiffEngine implements NamesDiffEngine {
  private static final Logger LOG = LoggerFactory.getLogger(StreamingMergeDiffEngine.class);

  @Override
  public NamesDiff diff(DiffInput a, DiffInput b, DiffOptions opts) {
    final Comparator<String> order = opts.getOrder();
    final List<String> removed = new ArrayList<>();
    final List<String> added = new ArrayList<>();
    boolean inversionSeen = false;

    try (Stream<String> sa = a.lines().get(); Stream<String> sb = b.lines().get()) {
      Iterator<String> ia = sa.iterator();
      Iterator<String> ib = sb.iterator();
      String x = ia.hasNext() ? ia.next() : null;
      String y = ib.hasNext() ? ib.next() : null;
      String prevX = null, prevY = null;

      while (x != null && y != null) {
        if (prevX != null && order.compare(prevX, x) > 0) inversionSeen = true;
        if (prevY != null && order.compare(prevY, y) > 0) inversionSeen = true;
        int c = order.compare(x, y);
        if (c == 0) {
          prevX = x; prevY = y;
          x = ia.hasNext() ? ia.next() : null;
          y = ib.hasNext() ? ib.next() : null;
        } else if (c < 0) {
          removed.add(x);
          prevX = x;
          x = ia.hasNext() ? ia.next() : null;
        } else {
          added.add(y);
          prevY = y;
          y = ib.hasNext() ? ib.next() : null;
        }
        guard(removed.size() + added.size(), opts);
      }
      while (x != null) { removed.add(x); x = ia.hasNext() ? ia.next() : null; guard(removed.size() + added.size(), opts); }
      while (y != null) { added.add(y); y = ib.hasNext() ? ib.next() : null; guard(removed.size() + added.size(), opts); }
    }

    if (inversionSeen) {
      LOG.warn("Diff inputs {} / {} were not strictly sorted under the configured order; relying on pass-2 healing",
        a.label(), b.label());
    }
    return NamesDiffEngine.assemble(a.label(), b.label(), removed, added, opts);
  }

  private static void guard(long candidateCount, DiffOptions opts) {
    if (candidateCount > opts.getMaxChangedCandidates()) {
      throw new IllegalStateException("Diff candidate buffer exceeded " + opts.getMaxChangedCandidates()
        + " entries; check that both inputs are sorted under the configured order");
    }
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd dao && mvn -q -Dtest=StreamingMergeDiffEngineTest test`
Expected: PASS (all five).

- [ ] **Step 5: Commit**

```bash
git add dao/src/main/java/life/catalogue/printer/diff/StreamingMergeDiffEngine.java \
        dao/src/test/java/life/catalogue/printer/diff/StreamingMergeDiffEngineTest.java
git commit -m "feat(diff): StreamingMergeDiffEngine merge-join with healing + candidate cap"
```

---

## Task 6: `MyersDiffEngine` + cross‑check

**Files:**
- Create: `dao/src/main/java/life/catalogue/printer/diff/MyersDiffEngine.java`
- Test: `dao/src/test/java/life/catalogue/printer/diff/MyersDiffEngineTest.java`
- Test: `dao/src/test/java/life/catalogue/printer/diff/DiffEngineCrossCheckTest.java`

**Interfaces:**
- Consumes: `NamesDiffEngine`, `DiffInput`, `DiffOptions`, `NamesDiff`, `DiffUtils` (java‑diff‑utils).
- Produces: `class MyersDiffEngine implements NamesDiffEngine` with ctor `MyersDiffEngine(int maxSize)` and default `MyersDiffEngine()` (maxSize=200_000). Throws `IllegalArgumentException` (message contains the maxSize) when either side exceeds `maxSize`.

- [ ] **Step 1: Write the failing tests**

`MyersDiffEngineTest.java`:
```java
package life.catalogue.printer.diff;

import life.catalogue.printer.NamesDiff;

import java.util.stream.Stream;

import org.junit.Test;

import static org.junit.Assert.*;

public class MyersDiffEngineTest {

  private final MyersDiffEngine engine = new MyersDiffEngine();

  private static DiffInput input(String label, String... lines) {
    return new DiffInput(label, () -> Stream.of(lines));
  }

  @Test
  public void addRemoveChange() {
    DiffInput a = input("a", "Abies alba", "Quercus robur", "Zea mays");
    DiffInput b = input("b", "Abies alba Mill.", "Quercus robur", "Zea mays");
    NamesDiff d = engine.diff(a, b, DiffOptions.defaults());
    assertEquals(1, d.getChangedCount());
    assertEquals(0, d.getRemovedCount());
    assertEquals(0, d.getAddedCount());
  }

  @Test
  public void sizeGuard() {
    MyersDiffEngine small = new MyersDiffEngine(2);
    DiffInput a = input("a", "A", "B", "C");
    DiffInput b = input("b", "A");
    try {
      small.diff(a, b, DiffOptions.defaults());
      fail("expected size guard");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("2"));
    }
  }
}
```

`DiffEngineCrossCheckTest.java`:
```java
package life.catalogue.printer.diff;

import life.catalogue.printer.NamesDiff;

import java.util.List;
import java.util.stream.Stream;

import org.junit.Test;

import static org.junit.Assert.*;

public class DiffEngineCrossCheckTest {

  private static DiffInput input(String label, List<String> lines) {
    return new DiffInput(label, () -> Stream.of(lines.toArray(new String[0])));
  }

  @Test
  public void enginesAgreeOnSmallInput() {
    List<String> s1 = List.of("Abies alba", "Betula pendula", "Cedrus deodara", "Zea mays L.");
    List<String> s2 = List.of("Abies alba Mill.", "Betula pendula", "Quercus robur", "Zea mays L.");
    NamesDiff m = new StreamingMergeDiffEngine().diff(input("a", s1), input("b", s2), DiffOptions.defaults());
    NamesDiff y = new MyersDiffEngine().diff(input("a", s1), input("b", s2), DiffOptions.defaults());
    assertEquals(m.getRemoved(), y.getRemoved());
    assertEquals(m.getAdded(), y.getAdded());
    assertEquals(m.getChangedCount(), y.getChangedCount());
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd dao && mvn -q -Dtest=MyersDiffEngineTest,DiffEngineCrossCheckTest test`
Expected: FAIL — `MyersDiffEngine` not found.

- [ ] **Step 3: Implement `MyersDiffEngine`**

```java
package life.catalogue.printer.diff;

import life.catalogue.printer.NamesDiff;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;

/**
 * Whole-list Myers (LCS) diff via java-diff-utils. Reads both sides fully into memory, so it is
 * guarded by maxSize and intended for tests, cross-checking StreamingMergeDiffEngine, and small
 * inputs — never for the 10M-name scale. Reuses the shared pass-2 assembly so its classification
 * matches the streaming engine.
 */
public class MyersDiffEngine implements NamesDiffEngine {
  public static final int DEFAULT_MAX_SIZE = 200_000;
  private final int maxSize;

  public MyersDiffEngine() { this(DEFAULT_MAX_SIZE); }

  public MyersDiffEngine(int maxSize) { this.maxSize = maxSize; }

  @Override
  public NamesDiff diff(DiffInput a, DiffInput b, DiffOptions opts) {
    List<String> la = collect(a, "side 1");
    List<String> lb = collect(b, "side 2");

    Patch<String> patch = DiffUtils.diff(la, lb, false);
    List<String> removed = new ArrayList<>();
    List<String> added = new ArrayList<>();
    for (AbstractDelta<String> d : patch.getDeltas()) {
      switch (d.getType()) {
        case DELETE -> removed.addAll(d.getSource().getLines());
        case INSERT -> added.addAll(d.getTarget().getLines());
        case CHANGE -> {
          removed.addAll(d.getSource().getLines());
          added.addAll(d.getTarget().getLines());
        }
        case EQUAL -> { /* unchanged */ }
      }
    }
    return NamesDiffEngine.assemble(a.label(), b.label(), removed, added, opts);
  }

  private List<String> collect(DiffInput in, String which) {
    try (Stream<String> s = in.lines().get()) {
      List<String> list = new ArrayList<>();
      var it = s.iterator();
      while (it.hasNext()) {
        list.add(it.next());
        if (list.size() > maxSize) {
          throw new IllegalArgumentException("MyersDiffEngine input " + which + " (" + in.label()
            + ") exceeds max size " + maxSize + "; use StreamingMergeDiffEngine");
        }
      }
      return list;
    }
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd dao && mvn -q -Dtest=MyersDiffEngineTest,DiffEngineCrossCheckTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add dao/src/main/java/life/catalogue/printer/diff/MyersDiffEngine.java \
        dao/src/test/java/life/catalogue/printer/diff/MyersDiffEngineTest.java \
        dao/src/test/java/life/catalogue/printer/diff/DiffEngineCrossCheckTest.java
git commit -m "feat(diff): MyersDiffEngine (java-diff-utils, guarded) + cross-check test"
```

---

## Task 7: Dataset name generator (pure SQL) + cursor streaming

**Files:**
- Create: `dao/src/main/java/life/catalogue/printer/diff/DiffNamesParam.java`
- Modify: `dao/src/main/java/life/catalogue/db/mapper/NameUsageMapper.java` (add method + import)
- Modify: `dao/src/main/resources/life/catalogue/db/mapper/NameUsageMapper.xml` (add select)
- Test: `dao/src/test/java/life/catalogue/printer/DatasetNamesGeneratorIT.java`

This task only *adds* code, so `dao` keeps compiling.

**Interfaces:**
- Consumes: existing `NameUsageMapper.xml` fragment `FROM_SIMPLE`; `is_synonym(status)` function; `::rank` casts.
- Produces:
  - `class DiffNamesParam` (bean getters/setters): `int datasetKey`, `Set<String> roots`, `Set<String> exclusion`, `Rank lowestRank`, `boolean synonyms`, `Set<Rank> rankFilter`, `boolean authorship`, `boolean parentName`, `Rank parentRank`, `boolean ignoreAmbiguousRanks`.
  - `NameUsageMapper.processDiffNames(@Param("param") DiffNamesParam param)` → `org.apache.ibatis.cursor.Cursor<String>` (byte‑ordered labels).

- [ ] **Step 1: Write the failing IT**

`DatasetNamesGeneratorIT.java`:
```java
package life.catalogue.printer;

import life.catalogue.dao.DaoTestBase;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.junit.TestDataRule;
import life.catalogue.printer.diff.DiffNamesParam;
import life.catalogue.printer.diff.DiffOptions;

import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.junit.Test;

import static org.junit.Assert.*;

public class DatasetNamesGeneratorIT extends DaoTestBase {

  public DatasetNamesGeneratorIT() {
    super(TestDataRule.tree());
  }

  // the dataset key staged by TestDataRule.tree(); reuse the same constant the diff ITs use
  private int datasetKey() {
    return TestDataRule.TREE.key;
  }

  private List<String> run(DiffNamesParam p) {
    List<String> out = new ArrayList<>();
    try (Cursor<String> c = mapper(NameUsageMapper.class).processDiffNames(p)) {
      c.forEach(out::add);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return out;
  }

  @Test
  public void wholeDatasetSortedByteOrder() {
    DiffNamesParam p = new DiffNamesParam();
    p.setDatasetKey(datasetKey());
    p.setSynonyms(true);
    p.setAuthorship(true);
    List<String> names = run(p);
    assertFalse(names.isEmpty());
    for (int i = 1; i < names.size(); i++) {
      assertTrue("not byte-sorted at " + i + ": " + names.get(i - 1) + " vs " + names.get(i),
        DiffOptions.CODEPOINT.compare(names.get(i - 1), names.get(i)) <= 0);
    }
  }

  @Test
  public void authorshipOffOmitsAuthors() {
    DiffNamesParam p = new DiffNamesParam();
    p.setDatasetKey(datasetKey());
    p.setSynonyms(false);
    p.setAuthorship(false);
    List<String> withAuth = run(new DiffNamesParam() {{ setDatasetKey(datasetKey()); setSynonyms(false); setAuthorship(true); }});
    List<String> noAuth = run(p);
    assertFalse(noAuth.isEmpty());
    // authorship-off labels are never longer than authorship-on for the same set
    assertTrue(String.join("", noAuth).length() <= String.join("", withAuth).length());
  }
}
```
Note: replace `TestDataRule.TREE.key` with the real constant exposed by `TestDataRule.tree()` — inspect `TestDataRule` and copy what `DatasetDiffServiceIT`/`SectorDiffServiceIT` use via `provideTestKey()`.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd dao && mvn -q -Dtest=DatasetNamesGeneratorIT verify`
Expected: FAIL — `DiffNamesParam` / `processDiffNames` do not exist.

- [ ] **Step 3: Create `DiffNamesParam`**

```java
package life.catalogue.printer.diff;

import org.gbif.nameparser.api.Rank;

import java.util.Set;

/** Parameters for the pure-SQL dataset name-label generator (NameUsageMapper.processDiffNames). */
public class DiffNamesParam {
  private int datasetKey;
  private Set<String> roots;         // null/empty = whole dataset (start at parent_id IS NULL)
  private Set<String> exclusion;     // taxonIDs to prune (incl. descendants)
  private Rank lowestRank;
  private boolean synonyms = true;
  private Set<Rank> rankFilter;      // ranks to exclude from output (row-level, keeps descendants)
  private boolean authorship = true;
  private boolean parentName = false;
  private Rank parentRank;           // null = direct parent; else ancestor at this rank
  private boolean ignoreAmbiguousRanks = false;

  public int getDatasetKey() { return datasetKey; }
  public void setDatasetKey(int datasetKey) { this.datasetKey = datasetKey; }
  public Set<String> getRoots() { return roots; }
  public void setRoots(Set<String> roots) { this.roots = roots; }
  public Set<String> getExclusion() { return exclusion; }
  public void setExclusion(Set<String> exclusion) { this.exclusion = exclusion; }
  public Rank getLowestRank() { return lowestRank; }
  public void setLowestRank(Rank lowestRank) { this.lowestRank = lowestRank; }
  public boolean isSynonyms() { return synonyms; }
  public void setSynonyms(boolean synonyms) { this.synonyms = synonyms; }
  public Set<Rank> getRankFilter() { return rankFilter; }
  public void setRankFilter(Set<Rank> rankFilter) { this.rankFilter = rankFilter; }
  public boolean isAuthorship() { return authorship; }
  public void setAuthorship(boolean authorship) { this.authorship = authorship; }
  public boolean isParentName() { return parentName; }
  public void setParentName(boolean parentName) { this.parentName = parentName; }
  public Rank getParentRank() { return parentRank; }
  public void setParentRank(Rank parentRank) { this.parentRank = parentRank; }
  public boolean isIgnoreAmbiguousRanks() { return ignoreAmbiguousRanks; }
  public void setIgnoreAmbiguousRanks(boolean v) { this.ignoreAmbiguousRanks = v; }
}
```

- [ ] **Step 4: Add the mapper method**

In `dao/src/main/java/life/catalogue/db/mapper/NameUsageMapper.java` add import
`import life.catalogue.printer.diff.DiffNamesParam;` and, near `processTreeSimple`:
```java
  /**
   * Streams a byte-ordered (C collation) list of name labels for a dataset, applying the diff
   * filters. Forward-only cursor: consume within an open session and close it.
   */
  Cursor<String> processDiffNames(@Param("param") DiffNamesParam param);
```
(`Cursor` and `@Param` are already imported in this mapper.)

- [ ] **Step 5: Add the SQL select**

In `dao/src/main/resources/life/catalogue/db/mapper/NameUsageMapper.xml` add. The label reproduces
`SimpleName.appendFullName`: `[†]name[ authorship][ name_phrase]` when authorship on, else just `name`.
Uses the recursive‑CTE traversal (parity with the current printer), carrying the ancestor‑at‑`parentRank`
name and the direct parent name forward for the optional suffix.
```xml
  <sql id="DIFF_LABEL">
    <choose>
      <when test="param.authorship">
        (CASE WHEN x.extinct THEN '†' ELSE '' END) || x.scientific_name
          || COALESCE(' ' || x.authorship, '') || COALESCE(' ' || x.name_phrase, '')
      </when>
      <otherwise>x.scientific_name</otherwise>
    </choose>
    <if test="param.parentName">
      <choose>
        <when test="param.parentRank != null">|| COALESCE(' &gt;&gt; ' || x.anc, '')</when>
        <otherwise>|| COALESCE(' &gt;&gt; ' || x.pname, '')</otherwise>
      </choose>
    </if>
  </sql>

  <select id="processDiffNames" parameterType="map" resultType="String"
          resultOrdered="true" fetchSize="10000" resultSetType="FORWARD_ONLY">
    WITH RECURSIVE x AS(
      SELECT u.id, u.parent_id, u.status, u.name_phrase, COALESCE(u.extinct, FALSE) AS extinct,
             n.scientific_name, n.authorship, n.rank,
             NULL::text AS pname,
             CASE WHEN <if test="param.parentRank != null">n.rank = #{param.parentRank}::rank</if><if test="param.parentRank == null">FALSE</if>
                  THEN n.scientific_name END AS anc
      FROM <include refid="FROM_SIMPLE"/>
      WHERE u.dataset_key=#{param.datasetKey}
      AND
      <choose>
        <when test="param.roots != null and !param.roots.isEmpty()">
          u.id IN <foreach item="id" collection="param.roots" open="(" separator="," close=")">#{id}</foreach>
        </when>
        <otherwise>u.parent_id IS NULL</otherwise>
      </choose>
      AND NOT is_synonym(u.status)
      <if test="param.exclusion != null and !param.exclusion.isEmpty()">
        AND u.id NOT IN <foreach item="id" collection="param.exclusion" open="(" separator="," close=")">#{id}</foreach>
      </if>
    UNION ALL
      SELECT u.id, u.parent_id, u.status, u.name_phrase, COALESCE(u.extinct, FALSE) AS extinct,
             n.scientific_name, n.authorship, n.rank,
             x.scientific_name AS pname,
             COALESCE(CASE WHEN <if test="param.parentRank != null">n.rank = #{param.parentRank}::rank</if><if test="param.parentRank == null">FALSE</if>
                           THEN n.scientific_name END, x.anc) AS anc
      FROM <include refid="FROM_SIMPLE"/>
        JOIN x ON x.id = u.parent_id
      WHERE u.dataset_key=#{param.datasetKey}
      <if test="!param.synonyms">AND NOT is_synonym(u.status)</if>
      <if test="param.exclusion != null and !param.exclusion.isEmpty()">
        AND u.id NOT IN <foreach item="id" collection="param.exclusion" open="(" separator="," close=")">#{id}</foreach>
      </if>
    )
    SELECT <include refid="DIFF_LABEL"/> AS label
    FROM x
    WHERE TRUE
    <if test="param.lowestRank">
      AND (x.rank &lt;= #{param.lowestRank}::rank OR x.rank = 'UNRANKED'::rank)
    </if>
    <if test="param.rankFilter != null and !param.rankFilter.isEmpty()">
      AND x.rank NOT IN <foreach item="r" collection="param.rankFilter" open="(" separator="," close=")">#{r}::rank</foreach>
    </if>
    ORDER BY label
  </select>
```
Notes:
- `FROM_SIMPLE` aliases the usage table `u` and name table `n` (verified in `NameUsageMapper.xml`). A trailing `name_match` join in that fragment is harmless.
- The CTE selects `n.*` aliased through `x`, so the outer `SELECT`/filters reference `x.scientific_name`, `x.rank`, etc.
- `lowestRank`/`rankFilter` are applied on the final projection (row-level, matching the printer's `setFilter`, which does not prune descendants).
- `ORDER BY label` sorts under the DB C collation = byte order (implicit; no COLLATE clause needed).
- Recursive term is `UNION ALL` (a tree has no duplicate ids; faster than `UNION`).

- [ ] **Step 6: Run the IT**

Run: `cd dao && mvn -q -Dtest=DatasetNamesGeneratorIT verify`
Expected: PASS. If a `::rank` cast or the `FROM_SIMPLE` alias differs, fix per the actual XML and the `rank` enum type name in `dbschema.sql`.

- [ ] **Step 7: Commit**

```bash
git add dao/src/main/java/life/catalogue/printer/diff/DiffNamesParam.java \
        dao/src/main/java/life/catalogue/db/mapper/NameUsageMapper.java \
        dao/src/main/resources/life/catalogue/db/mapper/NameUsageMapper.xml \
        dao/src/test/java/life/catalogue/printer/DatasetNamesGeneratorIT.java
git commit -m "feat(diff): pure-SQL byte-ordered dataset name generator (processDiffNames)"
```

---

## Task 8: Rewire the dao diff services (remove GNU diff/process)

Rewrites `BaseDiffService`, `SectorDiffService`, and `DatasetDiffService` **together in one commit** — removing `udiff()` from the base class breaks the other two until they are rewritten, so `dao` only compiles once all three are done.

**Files:**
- Modify: `dao/src/main/java/life/catalogue/printer/BaseDiffService.java` (rewrite)
- Modify: `dao/src/main/java/life/catalogue/printer/SectorDiffService.java` (constructor)
- Modify: `dao/src/main/java/life/catalogue/printer/DatasetDiffService.java` (rewrite)
- Modify: `webservice/src/main/java/life/catalogue/WsServer.java` (service ctor args)
- Modify: `dao/src/test/java/life/catalogue/printer/BaseDiffServiceIT.java` (rewrite)
- Modify: `dao/src/test/java/life/catalogue/printer/DatasetDiffServiceIT.java` (assert objects)

**Interfaces:**
- Consumes: `StreamingMergeDiffEngine`, `DiffInput`, `DiffOptions`, `NamesDiff` (Tasks 1–6); `FileMetricsDao.getNames(K,int)`; `NameUsageMapper.processDiffNames` (Task 7).
- Produces: `BaseDiffService.diff(K,String) → NamesDiff`; `DatasetDiffService.datasetNamesDiff(...) → NamesDiff`; constructors drop `int timeoutInSeconds`.

- [ ] **Step 1: Rewrite `BaseDiffServiceIT` (test first)**

`dao/src/test/java/life/catalogue/printer/BaseDiffServiceIT.java`:
```java
package life.catalogue.printer;

import life.catalogue.dao.DaoTestBase;
import life.catalogue.junit.TestDataRule;

import org.junit.Test;

import static org.junit.Assert.*;

public abstract class BaseDiffServiceIT<K> extends DaoTestBase {

  BaseDiffService<K> diff;

  public BaseDiffServiceIT() {
    super(TestDataRule.tree());
  }

  abstract K provideTestKey();

  @Test
  public void namesDiff() {
    NamesDiff d = diff.diff(provideTestKey(), "1..2");
    assertNotNull(d);
    assertEquals("dataset_" + provideTestKey() + "#1", d.getLabel1());
    assertEquals("dataset_" + provideTestKey() + "#2", d.getLabel2());
    assertFalse(d.isIdentical());
  }
}
```
Note: preserve however the current subclass ITs stage the `1-names.txt.gz` / `2-names.txt.gz` files for `provideTestKey()`; only the assertions change. If the current subclasses set `diff` in `@Before`, keep that.

- [ ] **Step 2: Rewrite `BaseDiffService`**

`dao/src/main/java/life/catalogue/printer/BaseDiffService.java`:
```java
package life.catalogue.printer;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.ImportAttempt;
import life.catalogue.dao.FileMetricsDao;
import life.catalogue.printer.diff.DiffInput;
import life.catalogue.printer.diff.DiffOptions;
import life.catalogue.printer.diff.NamesDiffEngine;
import life.catalogue.printer.diff.StreamingMergeDiffEngine;

import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

public abstract class BaseDiffService<K> {
  private static final Logger LOG = LoggerFactory.getLogger(BaseDiffService.class);

  private static final Pattern ATTEMPTS = Pattern.compile("^(\\d+)\\.{2,3}(\\d+)$");
  protected final SqlSessionFactory factory;
  protected final FileMetricsDao<K> dao;
  protected final NamesDiffEngine engine = new StreamingMergeDiffEngine();

  public BaseDiffService(FileMetricsDao<K> dao, SqlSessionFactory factory) {
    this.factory = factory;
    this.dao = dao;
  }

  /** Override to tune diff behaviour (thresholds, limits). */
  protected DiffOptions diffOptions() {
    return DiffOptions.defaults();
  }

  /**
   * Diff the stored names of two import attempts of the same key. The names files are already
   * byte-ordered (Postgres LC_COLLATE 'C'), so they stream straight into the merge engine.
   */
  public NamesDiff diff(K key, String attempts) {
    int[] atts = parseAttempts(key, attempts);
    DiffInput a = new DiffInput(label(key, atts[0]), () -> dao.getNames(key, atts[0]));
    DiffInput b = new DiffInput(label(key, atts[1]), () -> dao.getNames(key, atts[1]));
    LOG.info("Names diff for {} {} attempts {}..{}", dao.getType(), key, atts[0], atts[1]);
    return engine.diff(a, b, diffOptions());
  }

  abstract int[] parseAttempts(K key, String attempts);

  @VisibleForTesting
  protected int[] parseAttempts(String attempts, Supplier<List<? extends ImportAttempt>> importSupplier) {
    int a1;
    int a2;
    if (StringUtils.isBlank(attempts)) {
      List<? extends ImportAttempt> imports = importSupplier.get();
      if (imports.size() < 2) {
        throw new NotFoundException("At least 2 successful imports must exist to provide a diff");
      }
      a1 = imports.get(1).getAttempt();
      a2 = imports.get(0).getAttempt();
    } else {
      Matcher m = ATTEMPTS.matcher(attempts);
      if (m.find()) {
        a1 = Integer.parseInt(m.group(1));
        a2 = Integer.parseInt(m.group(2));
        if (a1 >= a2) {
          throw new IllegalArgumentException("first attempt must be lower than second");
        }
      } else {
        throw new IllegalArgumentException("attempts must be separated by a two dots ..");
      }
    }
    return new int[]{a1, a2};
  }

  protected String label(K key) {
    return label(key, null);
  }

  String label(K key, @Nullable Integer attempt) {
    return "dataset_" + key + (attempt == null ? "" : "#" + attempt);
  }
}
```

- [ ] **Step 3: Fix `SectorDiffService` constructor**

`dao/src/main/java/life/catalogue/printer/SectorDiffService.java` — change the constructor only:
```java
  public SectorDiffService(SqlSessionFactory factory, FileMetricsSectorDao dao) {
    super(dao, factory);
  }
```
(The rest of the class — `parseAttempts` — is unchanged.)

- [ ] **Step 4: Rewrite `DatasetDiffService`**

`dao/src/main/java/life/catalogue/printer/DatasetDiffService.java`:
```java
package life.catalogue.printer;

import life.catalogue.api.exception.TooManyRequestsException;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Page;
import life.catalogue.api.search.JobSearchRequest;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.dao.EntityDao;
import life.catalogue.dao.FileMetricsDatasetDao;
import life.catalogue.db.mapper.DatasetImportMapper;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.printer.diff.DiffInput;
import life.catalogue.printer.diff.DiffNamesParam;

import org.gbif.nameparser.api.Rank;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatasetDiffService extends BaseDiffService<Integer> {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetDiffService.class);

  private final EntityDao<Integer, Dataset, DatasetMapper> ddao;
  private final Set<Integer> userDiffs = ConcurrentHashMap.newKeySet();

  public DatasetDiffService(SqlSessionFactory factory, FileMetricsDatasetDao dao) {
    super(dao, factory);
    ddao = new EntityDao<>(false, factory, Dataset.class, DatasetMapper.class, null);
  }

  @Override
  int[] parseAttempts(Integer datasetKey, String attempts) {
    final JobSearchRequest req = new JobSearchRequest();
    req.setDatasetKey(datasetKey);
    req.setStates(Set.of(ImportState.FINISHED));
    return parseAttempts(attempts, () -> {
      try (SqlSession session = factory.openSession(true)) {
        return session.getMapper(DatasetImportMapper.class).list(req, new Page(0, 2));
      }
    });
  }

  /** Names diff between the current version of any two datasets, with optional roots/filters. */
  public NamesDiff datasetNamesDiff(int userKey, int key1, List<String> root1, int key2, List<String> root2,
                                    @Nullable Rank lowestRank, boolean inclAuthorship, boolean inclSynonyms, boolean showParent,
                                    @Nullable Rank parentRank, @Nullable Set<Rank> rankFilter) {
    if (key1 == key2) {
      throw new IllegalArgumentException("Diffs need to be between different datasets");
    }
    if (userDiffs.contains(userKey)) {
      throw new TooManyRequestsException("You can only run one diff at a time");
    }
    ddao.getOr404(key1);
    ddao.getOr404(key2);

    LOG.info("Start dataset diff between {} <-> {} by {}", key1, key2, userKey);
    try {
      userDiffs.add(userKey);
      DiffInput a = new DiffInput(label(key1),
        () -> namesStream(param(key1, root1, lowestRank, inclAuthorship, inclSynonyms, showParent, parentRank, rankFilter)));
      DiffInput b = new DiffInput(label(key2),
        () -> namesStream(param(key2, root2, lowestRank, inclAuthorship, inclSynonyms, showParent, parentRank, rankFilter)));
      return engine.diff(a, b, diffOptions());
    } finally {
      userDiffs.remove(userKey);
    }
  }

  private static DiffNamesParam param(int key, @Nullable List<String> roots, @Nullable Rank lowestRank,
                                      boolean authorship, boolean synonyms, boolean showParent,
                                      @Nullable Rank parentRank, @Nullable Set<Rank> rankFilter) {
    DiffNamesParam p = new DiffNamesParam();
    p.setDatasetKey(key);
    if (roots != null && !roots.isEmpty()) p.setRoots(new HashSet<>(roots));
    p.setLowestRank(lowestRank);
    p.setAuthorship(authorship);
    p.setSynonyms(synonyms);
    p.setParentName(showParent);
    p.setParentRank(parentRank);
    p.setRankFilter(rankFilter);
    return p;
  }

  /**
   * Opens a session + forward-only cursor and returns a Stream whose onClose closes both. The engine
   * closes the stream (try-with-resources), releasing cursor and session. Uses openSession(false) so
   * Postgres streams the sort result instead of buffering all rows.
   */
  private Stream<String> namesStream(DiffNamesParam param) {
    SqlSession session = factory.openSession(false);
    try {
      Cursor<String> cursor = session.getMapper(NameUsageMapper.class).processDiffNames(param);
      return StreamSupport.stream(cursor.spliterator(), false)
        .onClose(() -> {
          try { cursor.close(); } catch (Exception e) { LOG.warn("Failed to close diff cursor", e); }
          session.close();
        });
    } catch (RuntimeException e) {
      session.close();
      throw e;
    }
  }
}
```

- [ ] **Step 5: Update `WsServer` service construction**

`webservice/.../WsServer.java` (lines ~344‑345): drop `cfg.diffTimeout`:
```java
    DatasetDiffService dDiff = new DatasetDiffService(getSqlSessionFactory(), fmdDao);
    SectorDiffService sDiff = new SectorDiffService(getSqlSessionFactory(), fmsDao);
```
(Leave `cfg.diffTimeout` in `WsServerConfig` untouched; it is removed in no task, harmless.)

- [ ] **Step 6: Update `DatasetDiffServiceIT`**

Change the dataset-diff test(s) to assert a `NamesDiff` rather than udiff text. Keep the existing
`key1/root1/key2/root2` fixtures; only the return type/assertions change:
```java
  @Test
  public void datasetNamesDiff() {
    NamesDiff d = service.datasetNamesDiff(Users.TESTER, key1, root1, key2, root2,
      null, true, true, false, null, null);
    assertNotNull(d);
    assertEquals("dataset_" + key1, d.getLabel1());
    assertEquals("dataset_" + key2, d.getLabel2());
    assertFalse(d.isIdentical());
  }
```
Remove any `IOUtils.toString(br)` / `startsWith("--- dataset_")` assertions and the now-unused imports.
Keep the inherited `namesDiff` (attempts) test from `BaseDiffServiceIT`.

- [ ] **Step 7: Build dao + run the diff ITs**

Run: `cd dao && mvn -q -Dtest=DatasetDiffServiceIT,SectorDiffServiceIT verify`
Expected: PASS (attempts `namesDiff` for both, plus `datasetNamesDiff`). `dao` compiles cleanly.

- [ ] **Step 8: Commit**

```bash
git add dao/src/main/java/life/catalogue/printer/BaseDiffService.java \
        dao/src/main/java/life/catalogue/printer/SectorDiffService.java \
        dao/src/main/java/life/catalogue/printer/DatasetDiffService.java \
        dao/src/test/java/life/catalogue/printer/BaseDiffServiceIT.java \
        dao/src/test/java/life/catalogue/printer/DatasetDiffServiceIT.java \
        webservice/src/main/java/life/catalogue/WsServer.java
git commit -m "refactor(diff): dao diff services use the streaming engine + pure-SQL generator"
```

---

## Task 9: Endpoints → JSON, remove `DiffHealthCheck`

**Files:**
- Modify: `webservice/.../resources/dataset/AbstractDiffResource.java`
- Modify: `webservice/.../resources/dataset/DatasetDiffResource.java`
- Delete: `webservice/.../dw/health/DiffHealthCheck.java`
- Modify: `webservice/.../WsServer.java` (remove health‑check registration + import)

**Interfaces:**
- Consumes: `BaseDiffService.diff(K,String) → NamesDiff`, `DatasetDiffService.datasetNamesDiff(...) → NamesDiff` (Task 8).
- Produces: `AbstractDiffResource.diffNames(...) → NamesDiff` (`application/json`); `DatasetDiffResource.diffNames(...) → NamesDiff`.

- [ ] **Step 1: Update `AbstractDiffResource`**

```java
package life.catalogue.resources.dataset;

import life.catalogue.api.model.DSID;
import life.catalogue.printer.BaseDiffService;
import life.catalogue.printer.NamesDiff;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@SuppressWarnings("static-method")
@Produces(MediaType.APPLICATION_JSON)
public abstract class AbstractDiffResource<K> {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(AbstractDiffResource.class);
  private final BaseDiffService<K> diff;

  public AbstractDiffResource(BaseDiffService<K> diff) {
    this.diff = diff;
  }

  abstract K keyFromPath(DSID<Integer> dsid);

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public NamesDiff diffNames(@PathParam("key") int key,
                             @PathParam("id") int id,
                             @QueryParam("attempts") String attempts) {
    var dsid = DSID.of(key, id);
    return diff.diff(keyFromPath(dsid), attempts);
  }
}
```

- [ ] **Step 2: Update `DatasetDiffResource.diffNames` (the `{key2}` method)**

Change return type + drop the `throws IOException`; add `import life.catalogue.printer.NamesDiff;`
and remove `import java.io.Reader;` / `import java.io.IOException;` if now unused:
```java
  @GET
  @Path("{key2}")
  @Produces(MediaType.APPLICATION_JSON)
  public NamesDiff diffNames(@PathParam("key") Integer key,
                             @PathParam("key2") Integer key2,
                             @QueryParam("root") List<String> root,
                             @QueryParam("root2") List<String> root2,
                             @QueryParam("minRank") Rank lowestRank,
                             @QueryParam("authorship") @DefaultValue("true") boolean inclAuthorship,
                             @QueryParam("synonyms") boolean inclSynonyms,
                             @QueryParam("showParent") boolean showParent,
                             @QueryParam("parentRank") Rank parentRank,
                             @QueryParam("rankFilter") Set<Rank> rankFilter,
                             @Auth User user) {
    return service.datasetNamesDiff(user.getKey(), key, root, key2, root2, lowestRank, inclAuthorship,
      inclSynonyms, showParent, parentRank, rankFilter);
  }
```
`SectorDiffResource` needs no change (inherits the updated `AbstractDiffResource`).

- [ ] **Step 3: Remove `DiffHealthCheck`**

Delete `webservice/src/main/java/life/catalogue/dw/health/DiffHealthCheck.java`.
In `WsServer.java` remove the import `life.catalogue.dw.health.DiffHealthCheck;` (line ~26) and the two
registrations (lines ~507‑508):
```java
    env.healthChecks().register("dataset-diff", new DiffHealthCheck(dDiff));
    env.healthChecks().register("sector-diff", new DiffHealthCheck(sDiff));
```

- [ ] **Step 4: Compile webservice + check for dangling references**

Run: `cd webservice && mvn -q -DskipTests compile`
Expected: SUCCESS.
Run:
```bash
grep -rn "diffBinaryVersion\|udiff\|DiffHealthCheck\|UnixCmdUtils.sortUTF8" --include="*.java" webservice/src dao/src | grep -v /target/
```
Expected: no matches.

- [ ] **Step 5: Commit**

```bash
git add webservice/src/main/java/life/catalogue/resources/dataset/AbstractDiffResource.java \
        webservice/src/main/java/life/catalogue/resources/dataset/DatasetDiffResource.java \
        webservice/src/main/java/life/catalogue/WsServer.java
git rm webservice/src/main/java/life/catalogue/dw/health/DiffHealthCheck.java
git commit -m "feat(diff): diff endpoints return JSON NamesDiff; remove diff binary health check"
```

---

## Task 10: Full verification pass

**Files:** none (verification only).

- [ ] **Step 1: Build the affected modules with tests**

Run: `mvn -q -pl dao,webservice -am install`
Expected: BUILD SUCCESS — `diff` unit tests, `DatasetDiffServiceIT`, `SectorDiffServiceIT`,
`DatasetNamesGeneratorIT` all pass.

- [ ] **Step 2: Confirm the Process/GNU‑diff removal is complete**

Run:
```bash
grep -rn "Runtime.getRuntime\|ProcessBuilder\|gunzip -c\|sortUTF8\|\"diff " --include="*.java" \
  dao/src/main/java/life/catalogue/printer webservice/src/main/java/life/catalogue/resources/dataset
```
Expected: no matches.

- [ ] **Step 3: Confirm the engine + model are wired end-to-end**

Run:
```bash
grep -rn "StreamingMergeDiffEngine\|processDiffNames\|NamesDiff" --include="*.java" dao/src/main webservice/src/main | grep -v /target/
```
Expected: `BaseDiffService` → `StreamingMergeDiffEngine`; `DatasetDiffService` → `processDiffNames`;
resources return `NamesDiff`.

- [ ] **Step 4: Commit any final fixes**

```bash
git add -A
git commit -m "test(diff): full dao+webservice verification for names diff engine" || echo "nothing to commit"
```

---

## Notes for the implementer

- **Test data keys:** `TestDataRule.tree()` stages the tree dataset; get the exact key constant from `TestDataRule` (the existing `DatasetDiffServiceIT`/`SectorDiffServiceIT` obtain it via `provideTestKey()`). Do not hard‑code a literal without checking.
- **Attempts-path names files:** the `namesDiff` IT relies on stored `1-names.txt.gz` / `2-names.txt.gz` for `provideTestKey()`. The current ITs already stage these (they fed the old `udiff`); keep that staging and only change assertions.
- **`ApiModule.MAPPER`:** the project's configured Jackson mapper (records + enums supported). Use it in any resource/serialisation test.
- **Sector path:** only the *attempts* diff applies to sectors (`SectorDiffService` inherits `BaseDiffService.diff`); there is no sector‑to‑sector `datasetNamesDiff`, so no new sector SQL is needed.
- **Performance knob (out of scope):** if the whole‑dataset recursive CTE is too slow at 10M, add a flat‑`SELECT` variant guarded by `<choose>` when `roots`/`exclusion`/ancestor‑`parentRank` are all absent. Only if profiling demands it.
- **Future engine:** the `NamesDiffEngine` interface leaves room for a Panama/`libxdiff` implementation (needs JDK 22+/25); not built here.
```

