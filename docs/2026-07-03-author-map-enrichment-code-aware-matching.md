# Author-map enrichment & code-aware author matching

Date: 2026-07-03
Status: Design approved, pending spec review

## Problem

`api/src/main/resources/authorship/authormap.txt` (~4,957 rows) drives
`AuthorComparator` / `AuthorshipNormalizer` when comparing scientific-name
authorships. Two limitations:

1. **Coverage & provenance.** The file is essentially the IPNI / Brummitt &
   Powell "Authors of Plant Names" botanical dataset. It is botany-only, has one
   abbreviation + one full name per author, and there is no committed pipeline to
   refresh or extend it.
2. **Code-blind expansion.** Botanical author citations use standardized
   abbreviations (`L.` = Linnaeus); zoological citations use full surnames plus a
   mandatory year and have no abbreviation registry. The current lookup path
   (`AuthorshipNormalizer.lookup(...)`) ignores `NomCode`, so botanical
   abbreviations can misfire on zoological names — e.g. a zoological surname
   colliding with a botanical abbreviation string such as `Bunge` or `Amor`.

## Goals

- **(a)** Grow the map well beyond the current botanical set: more abbreviation
  variants, more full-name/transliteration aliases, and **zoological** authors —
  produced by a committed, re-runnable generator.
- **(b)** Make abbreviation expansion `NomCode`-aware so botanical rules are not
  applied to zoological names, and zoological comparison relies on the signals
  that actually discriminate there (full surname + year).

## Current state (reference)

Format today: `canonical <TAB> alias1 <TAB> alias2 …` where **col0 is the value**
and every other column is a **key** pointing to it. The loader
(`AuthorshipNormalizer.createWithAuthormap`) re-normalizes both keys and value
(ASCII-fold, strip punctuation, initials-to-front, lowercase) and already
supports an unlimited number of alias columns (commit `744fb28b9`). `lookup()`
takes no `NomCode` even though `normalize(Authorship, code)` already uses the
code for ex-author ordering.

## Design

### 1. File format — add an explicit code column

```
canonical            code   alias1        alias2                       alias3 …
A A Achverdov        BOT    Achv.         Agazi Asaturovich Achverdov
C Linnaeus           BOT    L.            Carl Linnaeus                Linné
G Cuvier             ZOO    Cuvier        Georges Cuvier
J F Gmelin           ANY    Gmelin        Johann Friedrich Gmelin
```

- `code` ∈ `{BOT, ZOO, ANY}`. `ANY` = aliases safe under any code (full-name
  aliases, transliteration variants; authors who published under both codes,
  e.g. Gmelin, Fabricius).
- Loader change: read **col1 as the code tag**, cols 2..n as aliases, preserve
  the unlimited-alias behavior. The existing file is migrated so every current
  row becomes `BOT`.
- Single reviewable file rather than parallel per-code files that drift.

### 2. Generator & sources — part (a)

A committed, re-runnable generator (in a build/tools script or module, **off the
app runtime path**) that merges the following sources into the canonical keyed
form and emits `authormap.txt`:

1. **Manual curation file** — a committed, hand-maintained TSV in the same
   `canonical / code / aliases…` format. **Highest precedence:** it supplements
   sources with authors they lack and overrides them on conflict. This is the
   home for corrections and for preserved authors (see §4).
2. **IPNI authors** — refresh the botanical base (standard form, abbreviation,
   full name). `BOT`.
3. **Wikidata (SPARQL)** — `P428` (botanist abbreviation → `BOT`), `P835`
   (zoologist author-citation → `ZOO`), plus `label`/`aliases` for full-name and
   transliteration variants (`ANY`). The primary source of zoological coverage
   and of multi-variant aliases.
4. **HUH (Harvard Index of Botanists)** — optional botanical cross-check/fill.
   `BOT`.

Merge rules:

- Group by canonical author identity; union aliases; dedupe after normalization.
- Precedence on conflicting canonical/code: **manual > IPNI > Wikidata > HUH**
  (manual always wins).
- Conflicting canonicals reuse the loader's existing duplicate-key warning
  mechanism and are written to the build report.

ZooBank and VIAF are deferred — low value / high noise for v1.

### 3. Code-aware comparison — part (b)

Thread `NomCode` through the lookup path — `AuthorshipNormalizer.lookup(...)` and
its callers in `AuthorComparator.compareAuthorteam`:

- **Botanical or unknown code:** apply `BOT` + `ANY` entries. This is today's
  aggressive abbreviation expansion (`L.` → Linnaeus). Unchanged.
- **Zoological code:** apply only `ZOO` + `ANY` entries. **Do not** expand
  botanical abbreviations. This removes false positives from zoological surnames
  colliding with botanical abbreviation strings, and leans on full surname + the
  mandatory year (already handled by `YearComparator` in `compareStrict`).

The fuzzy surname/initials logic in `AuthorComparator.compare(Author, Author, …)`
is unchanged. Interpretation confirmed with the user: zoology has no abbreviation
registry to expand *into*, so "cleverer for zoology" means *stop misapplying
botanical abbreviations and rely on surname + year*.

### 4. Removal/diff report & preservation — part (a) follow-through

After the first generator run, before replacing the committed file:

1. Compute the diff between the **old** `authormap.txt` and the **newly
   generated** one, at two levels:
   - canonical entries (col0) present in old but absent in new;
   - alias keys that previously resolved to a canonical value and no longer do.
2. Emit this as a human-readable report (committed under the build/tools
   artifacts or printed).
3. Curators review the report and move authors worth keeping into the **manual
   curation file** so they survive this and future regenerations.
4. Only then is the regenerated `authormap.txt` committed.

This makes enrichment non-lossy: nothing silently disappears.

### 5. Testing

- `AuthorshipNormalizerTest` / `AuthorComparatorTest`:
  - `L.` expands to Linnaeus under `BOTANICAL`/unknown but **not** under
    `ZOOLOGICAL`.
  - a zoological surname colliding with a botanical abbreviation no longer
    false-matches under `ZOOLOGICAL`.
  - representative Wikidata/zoo entries resolve correctly.
- File-validation test on the generated map: well-formed rows, valid code tag in
  col1, no canonical key mapping to two different values.

## Open questions

None outstanding — design approved with the manual-source and diff-report
additions.

## Out of scope

- ZooBank / VIAF ingestion (deferred).
- Changes to the fuzzy Jaro/common-substring comparison thresholds.
- Runtime/online lookups; the map stays a build-time static resource.

## Outcome

Salvaged from the implementation plan's self-review before it was retired.

- **Deviation from this spec:** IPNI "refresh" and HUH are implemented as **downloaded-dump
  readers** rather than live scrapers — there was no verified bulk endpoint, and dumps keep the tool
  reproducible and testable. The committed `authormap.txt` serves as the IPNI base.
