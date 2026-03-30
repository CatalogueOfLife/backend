# XRelease Pipeline Documentation

Extended releases (XRelease) build upon a base public release by merging additional source datasets via sectors. The result is a comprehensive taxonomic dataset with stable IDs across versions.

## Architecture Overview

XRelease extends `ProjectRelease` (template method pattern defined in `AbstractProjectCopy.runWithLock()`):

```
initJob() → prepWork() → copyData() → finalWork() → metrics() → postMetrics() → index() → onFinishLocked()
```

The key innovation is a **two-phase copy**: base release → temporary project (for merging) → final release (with stable ID mapping).

## Pipeline Steps

### 1. `initJob()` — Setup
- Creates the final release dataset (`newDatasetKey`)
- Creates a **temporary project** (`tmpProjectKey`) as merge workspace
- Creates DB sequences and ID mapping tables for the temp project
- Initializes an in-memory `UsageMatcher` against the (still empty) temp project

### 2. `prepWork()` — The Main Work

#### 2a. Pre-merge checks
- **`syncFactory.assertComponentsOnline()`** — fail early if infrastructure is down
- **`licenseCheck()`** — verify all merge sector source licenses are compatible with the project license
- **`RematchMissing`** — asynchronously re-matches unmatched names in the base release (runs in parallel with steps below)

#### 2b. Sector preparation
- **`updatePublisherSectors()`** — creates new merge sectors from publisher configurations (auto-discovers new datasets from known publishers)
- **`loadMergeSectors()`** — loads all `MERGE` mode sectors from the project, removes sectors whose source dataset was deleted, re-targets sectors to `tmpProjectKey`

#### 2c. Base copy
- Copies all data from the base release into `tmpProjectKey`, preserving original identifiers
- `SectorPublisher` entities are copied from the project (not base release)

#### 2d. Merge infrastructure setup
- Creates `TreeMergeHandlerConfig` — sets up incertae sedis placeholder taxon, blocked name filters
- Loads the `UsageMatcher` store with data from the now-populated temp project
- Creates `XIdProvider` for stable ID generation, removes existing IDs from temp dataset

#### 2e. `mergeSectors()` — Sector Sync
For each sector (ordered by priority):
1. Creates sector in temp project if missing, re-matching target taxon
2. Runs `SectorSync` via `SyncFactory.release()` which traverses the source dataset tree:
   - Matches each source taxon against existing data via `UsageMatcher`
   - Creates new usages or updates existing matches
   - Applies editorial decisions (BLOCK, MERGE, etc.)
   - Uses `TreeMergeHandlerConfig` for incertae sedis placement and name blocking
3. On success: copies merge decisions, records sync attempt
4. On failure: increments `failedSyncs`, optionally throws if `failOnSyncErrors=true`

The single `UsageMatcher` instance is shared across all sector syncs for efficiency.

#### 2f. `homotypicGrouping()` — Post-Merge Consolidation
- **`HomotypicConsolidator`** — detects basionym groups per family, synonymizes lower-priority duplicates using `SectorPriority`
- **Misspelling consolidation** (optional) — fuzzy-matches names within families (Damerau-Levenshtein ≤ 1)
- **`flagDuplicatesAsProvisional()`** (optional) — marks accepted homonyms (same name, different author) from lower-priority sectors as `PROVISIONALLY_ACCEPTED`
- **`moveSynonymChains()`** — fixes synonym-of-synonym chains created during consolidation

#### 2g. `validateAndCleanTree()` — Validation & Metrics
Traverses the entire accepted name tree depth-first:
- **Name validation** — parsing issues, code compliance
- **Classification integrity** — parent/child rank order, genus/species mismatches, publication dates
- **`TaxonMetricsBuilder`** — builds per-taxon counts (species, synonyms, etc.) during traversal
- Flags issues to `VerbatimSource` records via `IssueAdder`

#### 2h. `flagLoops()` — Structural Integrity
Detects and fixes four categories of structural problems:
1. **Chained synonyms** — synonym pointing to another synonym → repoint to ultimate accepted parent
2. **Parent synonyms** — accepted taxon under a synonym parent → repoint to synonym's accepted parent
3. **Classification cycles** — creates a "cycle parent placeholder" under incertae sedis, repoints cycle members
4. **Missing parents** — usages referencing non-existent parent IDs → repoint to incertae sedis (or null)

#### 2i. Cleanup & ID Stabilization
- **`removeOrphans()`** — deletes names and references not linked to any usage
- **`mapTmpIDs()`** — `XIdProvider.mapTempIds()` replaces temporary UUIDs (for names without authorship) with stable IDs
- **`updateMetadata()`** — updates release description with source counts using Freemarker templates

### 3. `copyData()` — Final Copy with ID Mapping
Second invocation copies from `tmpProjectKey` → `newDatasetKey` (final release) with `map=true`, translating all temporary IDs to stable release IDs via the ID mapping tables.

### 4. `finalWork()` — Post-Copy
- `usageIdGen.report()` — generates reports on created/deleted/resurrected IDs
- Drops temp project DB sequences
- Parent class: removes orphan sectors/decisions, archives source metadata, aggregates authors, flushes caches

### 5. `metrics()` — Statistics
- **`buildSectorMetrics()`** — updates each merge sector's import metrics with final counts (post-consolidation)
- Parent class: rebuilds overall dataset statistics

### 6. `index()` — Elasticsearch
Indexes all name usages for search.

## Key Classes

| Class | Role |
|-------|------|
| `XRelease` | Orchestrator — extends ProjectRelease |
| `XReleaseConfig` | Config: consolidation flags, blocked names, exclusions, thread counts |
| `TreeMergeHandlerConfig` | Merge config: incertae sedis setup, blocked name patterns |
| `XIdProvider` | Stable ID generation using NameIndex canonical lookups |
| `SyncFactory` | Creates SectorSync instances for release-mode merging |
| `SectorSync` | Executes a single sector merge (tree traversal + matching) |
| `TreeMergeHandler` | Per-usage merge logic: match, create/update, apply decisions |
| `UsageMatcher` / `UsageMatcherFactory` | Name matching against existing dataset |
| `HomotypicConsolidator` | Post-merge basionym grouping and deduplication |
| `SectorPriority` | Resolves conflicts: lower priority number = higher authority |
| `TreeCleanerAndValidator` | Tree validation, issue flagging, metrics building |
| `IssueAdder` | Writes issues to VerbatimSource records |
| `IdProvider` | Base class for stable ID mapping across releases (score matrix) |

## Configuration (XReleaseConfig)

Key options loaded from the project's `XRELEASE_CONFIG` setting URI:

| Option | Default | Purpose |
|--------|---------|---------|
| `failOnSyncErrors` | true | Abort release on any sector sync failure |
| `homotypicConsolidation` | true | Enable basionym grouping |
| `homotypicConsolidationThreads` | 4 | Thread count for consolidation |
| `misspellingConsolidation` | true | Detect/fix misspellings per family |
| `flagDuplicatesAsProvisional` | true | Mark lower-priority homonyms as provisional |
| `removeEmptyGenera` | true | Remove genera with no species after merge |
| `sourceDatasetExclusion` | null | Dataset keys to exclude from publisher sectors |
| `blockedNames` / `blockedNamePatterns` | empty | Names/patterns to exclude globally |
| `basionymExclusions` | empty | Per-family epithet exclusions for basionym grouping |
| `issueExclusion` | empty | Issues that trigger usage exclusion during merge |

## Known Issues / Technical Debt

1. **Dead code**: `synonymizeMisspelledBinomials()` (line ~700) is never called and is nearly identical to `flagDuplicatesAsProvisional()`. Should be removed.
2. **Unimplemented**: `cleanImplicitTaxa()` only logs a warning — placeholder for future work.
3. **Typo in mapper**: `detectParentSynonyms` method name has a typo (should be `detectParentSynonyms`).
4. **Double semicolons**: Lines 148, 239 have `;;` — cosmetic.
5. **`newDatasetKey` dual use**: The field is temporarily reassigned to `tmpProjectKey` during `prepWork()` (line 180) and restored later (line 210). This implicit state mutation makes the code fragile and hard to follow — methods called between these lines must be aware which dataset `newDatasetKey` currently refers to.
