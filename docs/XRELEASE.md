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
- Creates `XIdProvider` for ID generation, removes existing IDs from temp dataset

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

An existing name only takes over the publishedIn reference of a source name if it has none. If it cites a different
reference, `PublishedInIdentity` compares both on DOI, year and page and the name gets only what is missing - page and
page link, e.g. from BHL - when they cite the same page or work; the DOI of an article goes onto the existing
reference when both share title and year. An author & year stub like `Benth. (1842).` is replaced by the source
reference for that name alone. Any contradiction merges nothing, see
[2026-09-23-merge-published-in-links.md](2026-09-23-merge-published-in-links.md).

#### 2f. `homotypicGrouping()` — Post-Merge Consolidation
Runs on the temporary project once all sectors are merged. Wherever sources compete, `SectorPriority` ranks them:
data managed in the project first, then the sectors of the base release, then the merge sectors by their `priority`
(lower is more trusted; merge sectors without one come last).

**`HomotypicConsolidator`** (`homotypicConsolidation`) works family by family, `homotypicConsolidationThreads`
families at a time. Names outside any family are never grouped. Within a family it:
1. Collects all names of species rank or below, accepted and synonyms, except autonyms, unparsed names, identifiers
   and the epithets `basionymExclusions` lists for the family, keyed by their normalized terminal epithet, which ignores
   gender endings.
2. Merges orthographic variants of an epithet into one key: names whose authorships compare strictly equal and whose
   full names are at least 92% similar (`ScientificNameSimilarity`), e.g. *Aphanizomenon holsaticum* / *holtsaticum*
   Richter.
3. Splits each key into groups by basionym author, or combination author for names without brackets
   (`BasionymSorter`). The most trusted original name of a group becomes its basionym; among equally trusted ones the
   alphabetically first label, so the choice does not depend on the order the names are read in. A name by the author
   before an "ex" (Desf. in Desf. ex F.Dietr.) joins that group as the name it is based on, chosen the same way.
   Original names of one author in different genera stay separate groups when a single source lists them separately,
   i.e. with the same priority and not linked by synonymy or a name relation. Without that evidence they are lumped,
   since a missing bracket is the likelier explanation. A recombination joins one of those separate groups only if it
   shares that group's genus or the data links it to that group alone; otherwise it forms a group of its own without
   a basionym.
4. Picks the primary usage of each group from its most trusted source. If that source holds several usages they must
   point to one accepted name, or exactly one of them must be accepted, or one accepted name must be left carrying the
   group's epithet once a species is preferred over its own autonym. Otherwise the group is left alone: its accepted
   names get `HOMOTYPIC_CONSOLIDATION_UNRESOLVED` and no relations are created.
5. Creates the missing name relations, all with `createdBy` 14 (`Users.HOMOTYPIC_GROUPER`): `BASIONYM` from every
   recombination to the basionym, `HOMOTYPIC` between the recombinations of a group without one, `BASED_ON` from the
   name with the "ex" author to the name it is based on. Every further original name is related to the basionym or
   based on name as well:
   - an orthographic variant - same genus and rank, only the terminal epithet spelled differently, gender endings
     included - gets a `SPELLING_CORRECTION` from the basionym to the variant, i.e. from the most trusted source's
     spelling to the others. The grouper cannot know which spelling is correct, source priority decides.
   - anything else - the same name at another rank missing its brackets, another species or subgenus, or a name of
     another genus lumped by step 3, a misspelled genus included - gets `HOMOTYPIC` towards the basionym.
   - duplicates, the same name and rank maybe cited with a different authorship, get no relation at all.
6. Turns every usage of a less trusted source, accepted or synonym, into a synonym of the primary's accepted name,
   moving its descendants along and flagging `HOMOTYPIC_CONSOLIDATION`. A converted name identical to that accepted
   name or to one of its synonyms is deleted. Usages from a source as trusted as the primary's are kept as they are.

Issues are written once all families are done.

**Misspelling consolidation** (`misspellingConsolidation`) runs in the same per family task afterwards. It walks the
family's accepted names in alphabetical order and compares each one with the 10 before it: same rank, identical
authorship string, full names at most one edit apart (`ModifiedDamerauLevenshtein`), code compliant name types only.
The less trusted name becomes a synonym of the other with `MISSPELLING_CONSOLIDATION`; no relation is created. Two equally
trusted names from the project or from the base release are both kept, see Known Issues for merge sources. As only
alphabetical neighbours are compared, a misspelling in the first letters is never found.

**`flagDuplicatesAsProvisional()`** (`flagDuplicatesAsProvisional`) groups the accepted names by scientific name,
ignoring case and authorship, if the names of a group share one rank and code. All but the most trusted become
`PROVISIONALLY_ACCEPTED` with `DUPLICATE_NAME`, and several equally trusted ones are all kept. This catches homonyms
with different authors as well as duplicates the consolidation left alone. A failure is logged and ignored.

**`moveSynonymChains()`** repoints synonyms of synonyms to their accepted name, in up to 10 passes.

#### 2g. `validateAndCleanTree()` — Validation & Metrics
Traverses the entire accepted name tree depth-first:
- **Name validation** — parsing issues, code compliance
- **Classification integrity** — parent/child rank order, genus/species mismatches, publication dates
- **`TaxonMetricsBuilder`** — builds per-taxon counts (species, synonyms, etc.) during traversal
- Flags issues to `VerbatimSource` records via `IssueAdder`, which never stores an issue twice
- Skips accepted taxa below a synonym parent and their descendants, which `flagLoops()` repoints next.
  The parent stack only tracks accepted taxa, so they used to abort the traversal after the first root.
- A failure fails the release. It used to be logged and swallowed, which left most of the tree unvalidated.

A plain `ProjectRelease` runs the same validation on its copied data in `finalWork()`, after dropping the issues
copied from the project. The XRelease overrides that as a no-op, since it validates here already.

#### 2h. `flagLoops()` — Structural Integrity
Detects and fixes four categories of structural problems:
1. **Chained synonyms** — synonym pointing to another synonym → repoint to ultimate accepted parent
2. **Parent synonyms** — accepted taxon under a synonym parent → repoint to synonym's accepted parent
3. **Classification cycles** — creates a "cycle parent placeholder" under incertae sedis, repoints cycle members
4. **Missing parents** — usages referencing non-existent parent IDs → repoint to incertae sedis (or null)

#### 2i. Cleanup & ID Stabilization
- **`removeOrphans()`** — deletes names and references not linked to any usage
- **`mapTmpIDs()`** — `XIdProvider.mapTempIds()` maps every usage whose id is not yet a stable release id
  (`IdProvider.isStableId`: at most 7 LATIN29 characters) to a stable ID. Every merged usage arrives here with a
  temporary ShortUUID: `XIdProvider.issue()` mints nothing but temp ids, so the whole canonical group is scored at once
  by this single pass rather than usage by usage in whatever order the sectors happened to be merged. It also runs
  after `removeOrphans`, so no stable id is burnt on a usage that is dropped again in the same run. The base release's
  own ids are stable already and are therefore skipped, on top of being held out of the pool by
  `removeIdsFromDataset`. Usages without a names index match cannot be given a stable ID and keep their id in the
  release; they are listed in `temporary.tsv` in the release report directory and logged as a warning.
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

## Stable IDs shared with base releases

[`IDENTIFIER.md`](IDENTIFIER.md) describes the identifier rules for data users; this section covers only what is
specific to extended releases.

All releases of a project, base and extended, draw their IDs from one archive (`name_usage_archive`).
`XIdProvider` removes the IDs of the base release from its pool, so an extended release never takes over a base
release ID. The next base release (`ProjectRelease` → `IdProvider`) however sees every archived ID, including those
only ever issued in extended releases to names merged from other sources.

An ID only ever issued in an extended release is **junior** to one a base release has used: it loses any tie the
evidence left, see `IdCandidate`. That is what stops a duplicate merged from another source from taking over an old
base release ID, while still letting a name that moves from the extended release into the base release keep its ID -
an XR-only ID is never ruled out, it only ranks below. Releases missing from the release list, e.g. deleted or private
ones, never count as extended releases.

This replaced an `XR_ONLY_PENALTY` of 7 points subtracted from a flat score, sized to exceed the 6 points an
authorship match was worth. That penalty compensated for the archive keeping the *first* version of every ID, so an
old base release ID often carried an authorship its name had changed since and lost to a younger duplicate carrying
today's.

## Key Classes

| Class | Role |
|-------|------|
| `XRelease` | Orchestrator — extends ProjectRelease |
| `XReleaseConfig` | Config: consolidation flags, blocked names, exclusions, thread counts |
| `TreeMergeHandlerConfig` | Merge config: incertae sedis setup, blocked name patterns |
| `XIdProvider` | Temporary ids during the merge; the batch stable-id pass inherited from `IdProvider` |
| `SyncFactory` | Creates SectorSync instances for release-mode merging |
| `SectorSync` | Executes a single sector merge (tree traversal + matching) |
| `TreeMergeHandler` | Per-usage merge logic: match, create/update, apply decisions |
| `UsageMatcher` / `UsageMatcherFactory` | Name matching against existing dataset |
| `HomotypicConsolidator` | Post-merge basionym grouping and deduplication |
| `SectorPriority` | Resolves conflicts: lower priority number = higher authority |
| `TreeCleanerAndValidator` | Tree validation, issue flagging, metrics building |
| `IssueAdder` | Writes issues to VerbatimSource records |
| `IdProvider` | Base class for stable ID mapping across releases (`NameIdentity` evidence + `IdCandidate` ordering) |

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

Note that `blockedNamePatterns` is matched with an unanchored `find()` against `Name.getLabel()`,
which includes the authorship. A pattern therefore cannot be restricted to the name portion, and one
broad enough to catch a rank marker will also hit real authors and book citations - `Willd., Sp. Pl.`
or `Sp. Bate, 1856` for a `sp.` pattern. Prefer a typed check in code over a pattern for anything
that has to distinguish an author from a marker.

### Always-on filters

Independent of the config, `TreeBaseHandler.ignoreUsage` drops these from every sector - merge,
attach and union alike - counting them under an `IgnoreReason` in the sector import metrics:

| Filter | IgnoreReason |
|--------|--------------|
| Cultivars (cultivar epithet, `CULTIVARS` code, or a cultivar rank) | `INCONSISTENT_NAME` |
| Parsable but indetermined names, e.g. `Panthera sp.` | `INDETERMINED` |
| Names whose *authorship* is only an indetermination marker, e.g. `Berkeleyia` + `sp.` | `INDETERMINED` |

The last one exists because sources - Plazi treatment archives above all - supply the marker in their
own authorship column while the scientific name stays a clean genus. Such a name parses without any
issue and is not `isIndetermined()`, so it needs its own check (`NameValidator.isIndetAuthorship`),
which also raises `Issue.AUTHORSHIP_INDET_MARKER` at import time. See
[backend#1510](https://github.com/CatalogueOfLife/backend/issues/1510).

An indetermination marker reaches a merge in three shapes, and each is stopped somewhere else:

| Shape | Example source fields | Stopped by |
|-------|----------------------|------------|
| whole string | `scientificName = Panthera sp.` | the name parser types it `INFORMAL` + `INDETERMINED` |
| split authorship | `scientificName = Berkeleyia`, `authorship = sp.` | `NameValidator.isIndetAuthorship` in `ignoreUsage` |
| atomised epithet | `genericName = Scoloplos`, `specificEpithet = sp. 1` | `NameInterpreter` letting the parsed type beat the atoms |

The third shape is the one Plazi ColDP archives use most. `PREFER_NAME_ATOMS` has the interpreter
build the name from the atoms and stamp it `SCIENTIFIC`; the reconstructed label is re-parsed only as
a sanity check. That check used to adopt the parsed name only when its type was *unparsable*, so an
`INFORMAL` verdict - which is parsable - was discarded and the name stayed `SCIENTIFIC` with
`specificEpithet = "sp. 1"`. It now adopts the parsed name whenever the parser disagrees with the
type the atoms assumed, which lets both the `SECTOR_NAME_TYPES` filter and the `INDETERMINED` filter
above do their job. See [data#1568](https://github.com/CatalogueOfLife/data/issues/1568).

Merge sectors drop two more things in `TreeMergeHandler.ignoreUsage`:

| Filter | IgnoreReason |
|--------|--------------|
| Unranked names, unless they are OTU style codes like BOLD BINs or UNITE SH codes (`IDENTIFIER`) | `RANK` |
| Ranked names of type `OTHER`, unless they carry the `VIRUS` code | `NAME_OTHER` |

The first used to exempt `OTHER` too, which is how name-parser v4 typed OTU codes. Every source merged
today stores them as `IDENTIFIER`, and all the exemption still let through were unparsable synonyms
like `R ogas eurinus` or `A[mpelis] rufaxilla`, 246 of them in the September 2026 COL XR. It holds
whatever the sector's `nameTypes` say.

The second applies only to sectors without their own `nameTypes` filter, and a `REVIEWED` decision
overrides it. Viruses are exempt because the parser types every virus name `OTHER` and sets the
`VIRUS` code. Everything else of type `OTHER` is a string the parser could not understand, like
`0` or `=Papilio dorylas Denis & Schiffermüller, 1775`. Creating one does harm beyond the junk name:
a merged match below it patches the classification of the existing base usage, so TaiCOL's family
`0` pulled the Species Fungorum genus *Yamadazyma* below itself. The children of a dropped name attach
to its closest matched ancestor instead. See [data#1730](https://github.com/CatalogueOfLife/data/issues/1730).

### Genus homonyms

Two genus usages sharing a canonical name but carrying **different authorship** are decided by lineage
in `UsageMatcher.filterCandidates`, not by the family rank alone:

1. Compare the two classifications at the **lowest rank they share between FAMILY and ORDER**
   (`UsageMatcher.isEvidenceRank`). Equal there means one and the same genus published under another
   author citation - the candidate is kept and, being the only survivor, becomes a `snap` match: reused
   as the parent for the incoming children, never updated, so the target keeps its own authorship.
   Different there means real homonyms and the candidate is dropped, which creates a second genus.
2. Sharing no rank in that window leaves it to the **taxonomic group**: a disparate `TaxGroup` still
   means different taxa, so a new genus is created.
3. If the groups do not contradict each other either, the merge is genuinely undecidable. The name is
   then **skipped together with its whole subtree** rather than inserted - a fabricated duplicate genus
   splits the species of a real one across two entries, which is worse than omitting one source's copy.
   `TreeMergeHandler` logs a warning and counts it under `IgnoreReason.AMBIGUOUS_HOMONYM` in the sector
   import metrics; descendants are counted under `IGNORED_PARENT`. Watch that counter after a release -
   it is the only measure of what the skip cost.

The window exists because family is the best indicator but is regularly absent: Flora e Funga do Brasil
files its fungal genera straight under the order, which is what produced a duplicate *Amanita* in the
2026-09 XR ([data#1718](https://github.com/CatalogueOfLife/data/issues/1718)). Above ORDER the evidence
is too thin to act on - every beetle genus shares a kingdom with every other one. It is deliberately the
lowest *shared* rank rather than the lowest *agreeing* one (`lowestClassificationMatch`): *Mycetochara*
in Tenebrionidae and in Staphylinidae agree at ORDER, and letting that stand in for the conflicting
FAMILY would merge two genera that are genuinely different.

Species and below are untouched by this and keep the stricter rule that authorship must compare EQUAL -
two same-named species in one genus are classic homonyms. See `txtree/genushomonyms/readme.md` for the
worked scenario.

## Known Issues / Technical Debt

1. **Dead code**: `synonymizeMisspelledBinomials()` (line ~675) is never called. Despite its javadoc it does not synonymize misspellings but is a verbatim copy of `flagDuplicatesAsProvisional()`. Should be removed.
2. **Unimplemented**: `cleanImplicitTaxa()` only logs a warning — placeholder for future work.
3. **Typo in mapper**: `detectParentSynonyms` method name has a typo (should be `detectParentSynonyms`).
4. **Double semicolons**: Lines 148, 239 have `;;` — cosmetic.
5. **Misspelling ties**: in misspelling consolidation two names from the same merge source are equally trusted, yet one of them is still synonymized - the one sorting later. Two distinct species of one author one letter apart, e.g. *Aus bus* L. and *Aus cus* L., are lumped that way. The `isConsolidated()` guard on the comparison window never fires either, as `ConsolidationName.consolidatedId` is never set.
6. **`newDatasetKey` dual use**: The field is temporarily reassigned to `tmpProjectKey` during `prepWork()` (line 180) and restored later (line 210). This implicit state mutation makes the code fragile and hard to follow — methods called between these lines must be aware which dataset `newDatasetKey` currently refers to.
