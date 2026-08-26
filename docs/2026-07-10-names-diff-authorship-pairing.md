# Names diff engine — authorship-aware change pairing

Date: 2026-07-10 (revised 2026-07-11)
Status: Shipped — merged in `b2bcaa5bd`, released in v1.5.0
Area: `dao` — `life.catalogue.printer.diff`

## Problem

The streaming names-diff engine classifies each name-label string as `removed`, `added`,
or `changed`. Pass 2 (`ChangedMatcher`) is supposed to pair a `removed` with an `added`
when they are really one modification rather than an unrelated delete + insert.

A very common real modification is **adding or removing the entire authorship**. Today this
is detected only for names whose canonical part is long enough, because pairing uses a single
whole-label metric — normalized Levenshtein with a 50% threshold — and appended authorship is
a large fraction of a short label:

| before → after | result today |
|---|---|
| `Accipiter gentilis` → `Accipiter gentilis (Linnaeus, 1758)` | ✓ changed (sim 51%) |
| `Accipiter nisus` → `Accipiter nisus (Linnaeus, 1758)` | ✗ removed + added (sim 47%) |

Same edit, opposite outcome, purely because the canonical is two letters shorter.

### Root cause: whole-label similarity cannot separate the cases

Measured with the actual similarity classes on representative strings:

```
                                                       JaroWinkler  ModJW   NormLev
authorship ADD    Accipiter nisus → …(Linnaeus, 1758)      90.6      79.4     46.9
authorship REPLACE Abies alba Mill. → Abies alba L.        96.6      89.5     75.0
DIFFERENT species Accipiter nisus → Accipiter minor        94.6      83.7     73.3   ← trap
canonical TYPO    Accipiter nisus → Accipiter nisius       99.6      98.7     93.8
```

Appending an authorship is a *bigger* string edit than swapping one epithet, so a genuinely
different species in the same genus (`Accipiter nisus` → `Accipiter minor`) scores **higher**
under every whole-label metric than the authorship-add it is meant to catch. No whole-string
threshold — Levenshtein or JaroWinkler — can rank "authorship added" above "different species".
(The current 50% Levenshtein pass already mis-pairs different congeners: `nisus`/`minor` = 73%.)

The only signal that cleanly separates them is comparing the **canonical name with the
authorship stripped off**:

| case | normalized-canonical edit distance |
|---|---|
| authorship add / replace | 0 |
| canonical typo | 1 |
| different species (`nisus`/`minor`) | ≥ 2 |

## Design decisions (locked with the user)

1. **Replace** the whole-label fuzzy pass; do not run it as an extra pass. Its false positives
   (mis-paired congeners) are a known defect.
2. Pairing operates on the **authorship-stripped, normalized canonical**, using
   `SciNameNormalizer.normalize(String)` — a conservative, domain-specific normalizer that folds
   common misspellings and gender/stem variants.
3. Allow an **absolute edit distance of ≤ 1** between the two normalized canonicals (default,
   configurable). Normalization collapses the known variants; distance ≤ 1 absorbs residual typos.
4. A **1-in-1-out** same-canonical pairing is reported as a `changed` even when the authors are
   unrelated (e.g. `Mill.` → `L.`) — there is only one candidate it could pair with.
5. When several same-canonical variants are present on both sides (distinct synonyms differing
   only in authorship), authorship/year comparison is used **as a tie-break, not a gate**, so
   distinct synonyms are not mixed up.
6. **Keep the names file as one name string per row.** The authorship split is derived by parsing
   the (small) diff remainder at diff time, not by changing the stored file format. See
   *Alternatives considered*.

## Algorithm

Rewrites **pass B** of `ChangedMatcher.match`. Pass A (global exact-label healing) is unchanged:
any string identical on both sides — full `canonical + authorship` — is dropped from all outputs.

Only the **diff remainder** reaches pass B (already bounded by `DiffOptions.maxChangedCandidates`).

1. **Parse + cache once per candidate.** For each remaining removed/added label run
   `NameParser.PARSER.parse(label)` and cache:
   - `normCanonical = SciNameNormalizer.normalize(name.getScientificName())`
   - `authorship` = the parsed `org.gbif.nameparser.api.Authorship` (combination authorship; year included)
   - Unparsable label → `normCanonical = SciNameNormalizer.normalize(rawLabel)`, `authorship = null`.
2. **Block by genus** — unchanged `block()`: first whitespace-delimited token. Deliberately
   coarser than the canonical, so an epithet typo (distance 1) still shares a block with its partner.
3. **Pair within each block.** For each removed `r` (stable order), compute the eligible added set:
   unused `a` in the same block with `LevenshteinDistance.getDistance(normCanonical(r),
   normCanonical(a)) ≤ canonicalMaxDistance`.
   - **exactly one** eligible `a` → pair `r`↔`a` as `changed` (covers `+authorship`,
     `Mill.`→`L.`, epithet typo).
   - **two or more** eligible → rank by (1) smaller canonical distance, then (2)
     `AuthorComparator.compare(authorship(r), authorship(a))` preferring `EQUAL` > `AMBIGUOUS`;
     pair with the best candidate whose result is **not `DIFFERENT`**. If every candidate is
     `DIFFERENT`, leave `r` unpaired (→ `removed`). Distinct-author / distinct-year synonyms
     therefore stay apart.
     - Null authorship on one side (e.g. a bare canonical vs an authored variant) is treated as
       `AMBIGUOUS`, not `DIFFERENT`, so the authorship-add case can still pair when contested.
4. Unpaired removed → `removed`; unused added → `added`.
5. **Chunks & display similarity** are still computed from the *raw* labels (`NameChunker`,
   unchanged). `ChangedName.similarity` becomes purely informational (normalized Levenshtein on
   the raw labels); it no longer gates anything.

### Worked outcomes

| case | multiplicity | outcome |
|---|---|---|
| `Accipiter nisus` → `Accipiter nisus (Linnaeus, 1758)` | 1-in-1-out | CHANGED |
| `Poa annua L.` → `Poa annua (Mill.) Comb.` | 1-in-1-out | CHANGED |
| `Accipiter nisus` vs `Accipiter minor` (both present) | canonical dist ≥ 2 | stay removed/added |
| `Abax ellipticus (Cuvier,1833)` / `…Porta,1901` / `…Schauberger,1927` | multi, distinct years | stay apart (`YearComparator` = DIFFERENT) |
| `Statice scoparia Pall. ex Willd.` / `…C.A.Mey. ex Boiss.` | multi, distinct authors | stay apart unless authors compare EQUAL |
| `Accipiter nisus` → `Accipiter nisius` (typo) | dist 1 | CHANGED |

## Components & files

- `dao/.../printer/diff/ChangedMatcher.java` — rewrite pass B (steps 1–4 above); add the
  parse/normalize cache and the multiplicity-aware pairing. `block()` unchanged.
- `dao/.../printer/diff/NamesDiffEngine.java` (`assemble`) — stop injecting
  `NormalizedLevenshtein`; construct the matcher with a `NameParser` + `AuthorComparator` +
  `canonicalMaxDistance` from opts.
- `dao/.../printer/diff/DiffOptions.java` — remove the gating `changedThreshold`; add
  `int canonicalMaxDistance = 1`.
- Dependencies are already on the `dao` classpath: `parser` module (`pom.xml`),
  `AuthorComparator`/`YearComparator` (`dao .../matching/authorship`), `SciNameNormalizer` (`api`).
  No new module dependency. The names-file format and its writer/reader (`FileMetricsDao`) are
  **unchanged**.

## Performance

- Pairing parses only the diff remainder, not the full (up to 10M) name stream, and the remainder
  is already capped by `maxChangedCandidates` (default 1,000,000).
- The per-candidate `parse(...)` is the cost center. Mitigations: parse lazily per populated block,
  cache results, and rely on the existing cap. A **batch parse API** is the natural next
  optimization and aligns with the in-progress Rust parser rewrite (≈2–3× faster); structure the
  matcher so parsing is a single collect-then-parse step over each block, ready to swap in a batch
  call. Authorship parsing for the multi-variant tie-break can reuse the same parsed result.

## Alternatives considered

- **Swap the similarity metric (JaroWinkler / ModifiedJaroWinkler) on the whole label.** Rejected:
  the measured numbers show a different congeneric species outscores an authorship-add under every
  whole-label metric, so no threshold separates them (see *Root cause*).
- **Store a 2-column TSV (`scientificName` \t `authorship`) so the split is free.** Tempting because
  the DB already holds the two columns separately. Rejected in favour of long-term simplicity: it
  forces a persisted-file format migration and a dual-path (legacy 1-col vs new 2-col) reader for
  every historical attempt, permanently. The parse cost it saves is bounded to the diff remainder
  and shrinking with the Rust/batch parser — and it would not even remove parsing, since the
  year-based tie-break still needs a parsed `Authorship`. One name per row stays the format.

## Testing

Extend `dao/.../printer/diff/ChangedMatcherTest.java` and add cases:
- authorship add on a short name → CHANGED (`Accipiter nisus` → `…(Linnaeus, 1758)`).
- authorship replace 1-in-1-out (`Mill.` → `L.`) → CHANGED.
- epithet typo distance 1 → CHANGED; distance ≥ 2 (`nisus`/`minor`) → NOT paired.
- multi same-canonical synonyms with distinct years/authors (`Abax ellipticus` ×3,
  `Statice scoparia` ×2) → not mixed up.
- unparsable label falls back to whole-label normalized comparison without throwing.
- `DiffEngineCrossCheckTest` (Myers vs streaming) still agrees — both go through `assemble`.

## Open questions / risks

- `AuthorComparator` instance/config to use (default `AuthorshipNormalizer`-backed) and the
  `yearDifferenceAllowed` tolerance for the multiplicity tie-break — pick sensible defaults, expose
  only if needed.
- Greedy pairing within a block is order-sensitive when 1-in-1-out candidates are contested; rare,
  accepted for now.
- Blocking is by genus (first token), so a typo in the *genus* itself puts the partners in different
  blocks and they never pair, even at canonical distance 1. Deliberate: the coarse genus block keeps
  epithet typos together, and a changed genus is far rarer than a changed epithet.

## Outcome

Salvaged from the implementation plan's self-review before it was retired.

- **Decision coverage.** Every locked design decision landed: 1 (replace the fuzzy pass) and 4
  (1-in-1-out) in the `ChangedMatcher` pairing core, 2 (`SciNameNormalizer.normalize`) in its
  `parse` step, 3 (distance ≤ 1) as the new `canonicalMaxDistance` option on `DiffOptions`
  replacing `changedThreshold`, 5 (author/year tie-break) as the multiplicity tie-break, and 6
  (file format unchanged) by touching no file writer or reader. Each row of the worked-outcomes
  table has a test: short add, replace, congener, *Abax* multi, *Statice* multi, typo.
- **Deliberately left out of scope.** `yearDifferenceAllowed` relies on `AuthorComparator`'s
  built-in 11-year window rather than a new setting, and the greedy within-block ordering is
  accepted as-is. `canonicalMaxDistance` is the only new config surface.
- **Integration tests.** `BaseDiffServiceIT` / `DatasetDiffServiceIT` / `SectorDiffServiceIT` are
  DB-backed and unaffected by the signature changes (they go through `assemble`), so they run in CI
  rather than alongside the unit-level work.
