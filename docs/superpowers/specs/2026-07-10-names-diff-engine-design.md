# Names Diff Engine — Design

Date: 2026-07-10
Branch: `feature/names-diff-engine`
Status: approved design, pending implementation plan

## 1. Motivation

`dao/.../printer/BaseDiffService` currently produces a diff between two name lists by
shelling out to the GNU `diff` binary through a `bash -c` subprocess, writing a unified
diff (udiff) into a temp file and streaming it back as `text/plain`. The dataset‑to‑dataset
path additionally shells out to unix `sort` (`UnixCmdUtils.sortUTF8`).

Problems:

- Depends on external binaries (`diff`, `sort`) whose availability and collation behaviour
  differ across OSes (the code even has a `diffBinaryVersion()` health check for this).
- Spawns and manages OS processes, with timeout / zombie‑process handling and abort‑on‑HTTP‑disconnect issues.
- The udiff *text* output is awkward for the UI to consume — it must be parsed to know what
  was added / removed, and it has no notion of a *changed* name (a rename shows as an
  unrelated delete + insert).

### Goal

Replace the udiff text with a structured **diff object** that classifies names as
**added**, **removed** or **changed**, where a *changed* name carries the before/after
strings and marks the part that changed. The core is a pluggable **diff engine interface**
so multiple implementations can coexist (and a future `libxdiff`‑via‑Panama engine can be
added without touching callers).

Scale target: **up to 10 million names** per side. The engine must not hold whole name
lists in JVM memory.

## 2. Scope & decisions (agreed)

- **API change is a replacement, not additive.** The existing diff endpoints switch from
  `text/plain` udiff to `application/json` diff objects. The checklistbank UI adopts the new
  shape in lockstep. The GNU `diff` / `sort` / `Process` code is removed.
- **Engines built now:** `StreamingMergeDiffEngine` (production, scales to 10M) and
  `MyersDiffEngine` (java‑diff‑utils, whole‑list, size‑guarded — for tests / cross‑check /
  small inputs). A Panama/libxdiff engine is *designed for* (interface leaves room) but **not**
  built now; stays on Java 21.
- **Sort order is trusted, and it is byte‑exact.** The CoL database is created
  `LC_COLLATE 'C' LC_CTYPE 'C'` (`PgUtils.java:98`), so Postgres sorts by raw byte order.
  The comparator must reproduce **UTF‑8 byte order**, which equals Unicode **code‑point**
  order for well‑formed text — *not* Java's default `String.compareTo`, which is UTF‑16
  code‑unit order and disagrees for supplementary (astral) characters. For BMP‑only
  scientific names the difference never bites, but the comparator is pinned to code‑point /
  UTF‑8‑byte order to be exactly correct. No `java.text.Collator`, no re‑sort.
- **Both diff paths produce Postgres‑ordered (C‑collation) name streams.**
  - *Attempts path*: the stored `{attempt}-names.txt.gz` files are already written from
    `NameMapper.processNameStrings` (`ORDER BY n.scientific_name, n.authorship`) under C
    collation — streamed as‑is.
  - *Dataset path*: replaced by a **pure‑SQL name generator** that emits the label, applies
    all filters, and `ORDER BY label` under C collation, streamed via a forward‑only cursor.
    No printer, no temp file, no `sort` process.

## 3. Data model (the JSON payload)

New/updated types in `life.catalogue.printer` (or a new `life.catalogue.printer.diff`
subpackage — decided in the plan).

```java
class NamesDiff {                 // evolved from the current set-based class
  String label1;                  // e.g. "dataset_2296#7"  (attempt) or "dataset_1010" (dataset key)
  String label2;
  List<String> removed;           // present in side 1, not side 2 (was "deleted")
  List<String> added;             // present in side 2, not side 1 (was "inserted")
  List<ChangedName> changed;      // paired removed->added that are "similar"
  int removedCount, addedCount, changedCount;
  boolean truncated;              // true if any list was capped by DiffOptions.maxItems
  boolean identical();            // removed & added & changed all empty
}

class ChangedName {
  String before;                  // full old label (side 1)
  String after;                   // full new label (side 2)
  List<Chunk> chunks;             // ordered EQUAL/DELETE/INSERT segments spanning before->after
  double similarity;              // 0..1, from the distance pass
}

enum ChunkOp { EQUAL, DELETE, INSERT }
record Chunk(ChunkOp op, String text) {}
```

Notes:
- `removed`/`added` rename the current `deleted`/`inserted` fields; the semantic mapping is
  the same (side1 = attempt1 / key1, side2 = attempt2 / key2). Field renames are part of the
  breaking API change.
- `chunks` is the "mark the changed part": the UI reconstructs the old string from
  `EQUAL+DELETE` chunks and the new string from `EQUAL+INSERT` chunks, highlighting the
  non‑`EQUAL` runs.
- `NamesDiff.identical()` replaces the current `isIdentical()`.

## 4. Engine interface

```java
public interface NamesDiffEngine {
  NamesDiff diff(DiffInput a, DiffInput b, DiffOptions opts);
}

// a labelled, re-openable source of names (one label per line, already sorted for merge engines)
record DiffInput(String label, Supplier<Stream<String>> lines) {}

class DiffOptions {
  Comparator<String> order;       // default: code-point comparator (matches C collation)
  double changedThreshold = 0.5;  // min similarity to treat a removed/added pair as "changed"
  int maxItems = 0;               // 0 = unlimited; else cap each list and set truncated=true
  int maxChangedCandidates;       // safety cap on pass-1 candidate buffers (OOM guard)
}
```

- `lines` is a `Supplier<Stream<String>>` so the streaming engine opens it once and the
  caller owns closing (try‑with‑resources on the returned stream). Each returned `Stream`
  must be closed by the engine after consumption.
- `order` is injected so both paths share one comparator and tests can vary it.

## 5. `StreamingMergeDiffEngine` (production)

### Pass 1 — merge‑join (added / removed)

Walk both sorted streams with a merge‑join using `opts.order`:

```
advance a, b to first lines
while both have lines:
  c = order.compare(a, b)
  if c == 0: both advance                     // common name, discarded (never buffered)
  else if c < 0: removed.add(a); a advances    // a-only
  else: added.add(b); b advances               // b-only
drain remaining a -> removed, remaining b -> added
```

- **Memory:** O(1) for the walk plus O(#differences). The 10M common names are never
  materialised — only actual differences are buffered.
- **Monotonicity guard:** track the previous line per side; if `order.compare(prev, cur) > 0`
  the input is not sorted under `order` (comparator ≠ file collation). Log a warning and,
  if candidate buffers exceed `maxChangedCandidates`, fail fast with a clear
  "names not sorted consistently with configured order" error rather than corrupting results
  or OOM‑ing. With C collation + code‑point comparator this should never fire in practice.

### Pass 2 — distance matching + marking (changed)

Only the pass‑1 candidate sets (`removed` × `added`) are considered — small relative to 10M.

1. **Block** candidates by a cheap key (first token / genus of the name) so matching is not
   full quadratic. Candidate lists are already sorted, so blocks are contiguous.
2. Within a block, score `removed_i` vs `added_j` with a **whole-string normalized
   Levenshtein** similarity — `100 * (1 - editDistance / maxLen)` via
   `life.catalogue.matching.similarity.LevenshteinDistance.getDistance` (new class
   `NormalizedLevenshtein implements StringSimilarity`) — greedily pairing best matches with
   `similarity >= changedThreshold` (default 50). **Decision (2026-07-10):** the plan first
   chose `ScientificNameSimilarity`, but an empirical probe showed it (and the tuned
   `DistanceUtils.convertEditDistanceToSimilarity`) scores an authorship or year *addition*
   at 0 — the most common real change — because it penalises multi-char additions. Normalized
   Levenshtein scores those 60–95 and pairs them correctly.
3. **Distance‑0 (identical) pairs are reclassified as *unchanged* and dropped from both
   lists** — this self‑heals any rare local ordering glitch from the attempts‑path
   column‑vs‑concatenated ordering (see §7).
4. A matched pair becomes a `ChangedName`; unmatched remainders stay in `removed` / `added`.
5. **Marking:** compute the intra‑name diff of `before` vs `after` with **java‑diff‑utils**
   (`com.github.difflib`) at character (or token) granularity → the ordered `List<Chunk>`.
   Inputs are two short strings, so cost is negligible.

### Complexity

- Pass 1: O(n) time, O(1)+O(d) memory (n = total names, d = differences).
- Pass 2: O(sum over blocks of |removed_block| × |added_block|) — bounded by blocking; plus
  a tiny char‑diff per matched pair.

## 6. `MyersDiffEngine` (java‑diff‑utils, alternative)

- Reads both sides fully into `List<String>` and runs `DiffUtils.diff` (Myers).
- Adjacent `DELETE`+`INSERT` deltas whose lines pass the similarity threshold become
  `ChangedName` (reusing the same marking code); standalone deltas become added/removed.
- Loads both lists into memory, so it is **guarded by a max‑size** (e.g. reject > ~200k names)
  and is used for: unit tests, cross‑checking the streaming engine on small inputs, and
  callers that know inputs are small. **Never used at 10M scale.**
- Purpose: a second, independently‑implemented engine to validate the streaming engine and to
  exercise the interface with a genuinely different algorithm.

## 7. Sort‑order correctness (why "trust stored order" is safe here)

- DB is C collation → Postgres `ORDER BY` == byte order == code‑point order. The Java
  code‑point comparator matches exactly. No locale guessing.
- **Attempts‑path — made byte‑exact (Decision 2026-07-10).** Historically stored files used
  `ORDER BY scientific_name, authorship` (two columns), which disagrees with the byte order of
  the space‑joined line the engine compares whenever one scientific name is a byte‑prefix of
  another (~1% of lines; e.g. `"Abaris Dejean, 1831"` vs `"Abaris (Abaridius) splendidula …"`).
  Pass‑2 healing absorbs the resulting local inversions, but to remove the risk entirely rather
  than rely on healing:
  - `processNameStrings` now does `ORDER BY concat_ws(' ', scientific_name, authorship) COLLATE "C"`
    → all newly written files are byte‑exact.
  - `dao/src/main/scripts/migrate-names-byte-order.sh` re‑sorts every existing `*-names.txt.gz`
    to byte order (`LC_ALL=C sort`; idempotent; only line order changes, the line set is
    preserved). Run once per app host holding the file‑metrics repo.
  After the migration every diff (old/new in any combination) has both sides byte‑ordered, so no
  inversions occur. Healing/fuzzy pairing stay in place as a safety net (for the migration window
  and any un‑migrated host) and because they are needed for "changed" detection regardless.
- **Dataset‑path has no caveat:** it sorts the exact line it compares (`ORDER BY label`).

## 8. Dataset path — pure‑SQL name generator

Replaces `DatasetDiffService.printAndSort` (tree printer + `UnixCmdUtils.sortUTF8`).

A new MyBatis mapper method (on `NameUsageMapper` or a dedicated diff mapper) streams labels
for one dataset via a forward‑only `Cursor<String>`, mirroring `processNameStrings`
(`resultOrdered="true" fetchSize=... FORWARD_ONLY`). Postgres sorts (spilling to disk for
10M) and streams rows out.

The generated label reproduces `SimpleName.appendFullName` (authorship on) / `getName`
(authorship off):

```sql
-- authorship = true
(CASE WHEN u.extinct THEN '†' ELSE '' END)
  || n.scientific_name
  || COALESCE(' ' || n.authorship, '')
  || COALESCE(' ' || u.name_phrase, '')
-- authorship = false
n.scientific_name
-- optional showParent suffix
  || COALESCE(' >> ' || <parent-or-ancestor-at-rank name>, '')
```

Filters map directly:

| Option | SQL |
|---|---|
| `root` / `root2` (taxonIDs), possibly multiple | recursive CTE descending from the roots (existing `processTreeSimple` pattern); omitted entirely when no root → plain filtered `SELECT` |
| `exclusion` (taxonIDs incl. descendants) | excluded in the recursive descent |
| `synonyms` (bool) | `WHERE status ...` |
| `lowestRank` | `WHERE rank <= lowestRank` |
| `rankFilter` (exclude ranks) | `WHERE rank NOT IN (...)` — row‑level only, does not prune descendants (matches current printer `setFilter` behaviour) |
| `parentRank` (ancestor at rank) | `taxon_metrics.classification SIMPLE_NAME[]` + `classification_sn()` / `sn2text_array()`, or self‑join to parent for the direct‑parent case |
| sort | `ORDER BY <label> COLLATE "C"` (C is the DB default, so the collation clause is implicit) |

Rationale: the diff only needs **both sides generated identically**, so this SQL generator
need not match the Java tree printer byte‑for‑byte. Its label format is pinned by tests.

A `TEMP TABLE` staging step is optional; the default is a single ordered cursor query. Use a
temp table only if profiling shows the recursive CTE benefits from being materialised once.

## 9. Wiring & removals

- `BaseDiffService`
  - Remove `udiff(...)` (both overloads), `input(...)`, `diffBinaryVersion()`, the bash
    `diff` process, and the `timeoutInSeconds` process handling.
  - `diff(K key, String attempts)` → resolve the two attempts, open the two gzipped names
    streams via `dao.getNames(key, attempt)` (returns `Stream<String>`), build two
    `DiffInput`s, call the engine, return `NamesDiff`.
  - Keep `parseAttempts(...)` and label helpers.
  - The deprecated in‑memory set `diff(...)` is superseded by the engine and removed (or kept
    only if a test still needs it — prefer removal).
- `DatasetDiffService.datasetNamesDiff(...)` → build two `DiffInput`s backed by the pure‑SQL
  generator (per side, with that side's roots/options), call the engine, return `NamesDiff`.
  Remove `printAndSort`, `appendRoot`, `createTempFile`, `UnixCmdUtils.sortUTF8` usage.
- Endpoints
  - `AbstractDiffResource.diffNames`, `DatasetDiffResource.diffNames`,
    `SectorDiffResource` → `@Produces(APPLICATION_JSON)` returning `NamesDiff` instead of
    `text/plain Reader`.
- Health check
  - Remove `DiffHealthCheck` and its registration in `WsServer` (no binary to version).
- `NamesDiffEngine` + implementations live in `dao` (where `java-diff-utils` and the
  similarity utils already are). `java-diff-utils` is already a `dao` dependency (currently
  unused) — no new dependency needed.

## 10. Testing

- **Engine unit tests** (no DB) over crafted line lists:
  - pure add / pure remove / pure change / mixed;
  - accents & non‑ASCII ordering under the code‑point comparator;
  - the space‑boundary ordering edge case → verify pass‑2 healing yields no false "changed";
  - `changedThreshold` boundaries; `maxItems` truncation sets `truncated`;
  - `chunks` correctness (reconstruct before/after from chunks).
- **Cross‑check test:** `StreamingMergeDiffEngine` and `MyersDiffEngine` agree on small inputs.
- **Monotonicity guard test:** deliberately mis‑sorted input triggers the guard.
- **SQL generator tests:** pin the label format (extinct symbol, authorship on/off, phrase,
  parent suffix) and each filter, on a small imported test dataset.
- **IT adaptation:** `DatasetDiffServiceIT` / `BaseDiffServiceIT` change from asserting udiff
  text (`startsWith("--- dataset_")`) to asserting `NamesDiff` contents.

## 11. Out of scope / future

- Panama/`libxdiff` engine (needs JDK 22+/25 and native packaging).
- Paging of very large `added`/`removed` lists beyond the simple `maxItems` cap.
- UI changes in the checklistbank repo (tracked separately; must land with the API switch).

## 12. Risks

- **Breaking API:** the UI must switch to JSON in lockstep. Coordinate the deploy.
- **SQL label divergence:** the pure‑SQL generator duplicates `appendFullName` rules; pinned
  by tests, and only needs internal consistency, but must be kept in sync if label rules change.
- **Comparator ≠ collation** would break the merge; mitigated by the C‑collation guarantee,
  the code‑point comparator, and the monotonicity guard + pass‑2 healing.
- **Large diffs** (e.g. first import vs latest) can still produce large JSON; `maxItems`
  provides a bound with `truncated` signalling.
