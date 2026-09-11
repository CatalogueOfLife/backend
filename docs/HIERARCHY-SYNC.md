# Hierarchy Sync

The aim is to provide a service for projects to delegate the management of the higher classification of names to another target project or external dataset.
In case of projects, which I see as the main targets, we never sync directly with the target project but the latest public (X)Release that exists at the time of syncs.
The project will only sync genera, species and lower taxa using the existing life.catalogue.assembly.SectorSync infrastructure.

Synced name usages are expected to already carry identifiers from the target dataset if they can be matched.
With these it should be possible to figure out which higher taxa need to be carried over from the target taxonomy.

As the target taxonomy might be much larger than needed to organise small datasets, the hierarchy sync should only copy over a subset of the target taxonomy:
 - the entire parent classification of the corresponding project name usage for each name usage in the project (upwards sync)
 - change the status of a name usage in the project according to the target taxonomy, i.e. an accepted name in the project might become a synonym or vice versa
 - copy the entire synonymy of all accepted taxa in the project as a last step once we have established the final taxonomic status for each
 - optionally enrich the authorship of matched project names from the target (controlled by the sector's `authorshipUpdate` setting), tracking the target as an `AUTHORSHIP` secondary source

The hierarchy sync process must be repeatable, i.e. data from previous syncs must be removed (identified via sectorKey) first.

I am uncertain if we need to watch out for creating duplicates and rather merge existing names in the project in some cases. Having no higher classification in the project prevents this mostly,
but on the genus level we might still have problems.

---

# Pipeline Documentation

The remainder of this document is the implementation reference for the hierarchy sync pipeline,
following the conventions of [`XRELEASE.md`](XRELEASE.md).

## Architecture Overview

A hierarchy sync is modeled as a `life.catalogue.api.model.Sector` with **`Sector.Mode.HIERARCHY`**
living on the project. The sector carries:

| field | meaning |
|---|---|
| `datasetKey` | the project that delegates its higher classification |
| `subjectDatasetKey` | the configured target dataset (project, release or external) |
| `useXRelease` | when the configured target is a project, pick its latest X-Release vs latest plain Release |
| `mode = HIERARCHY` | distinguishes from ATTACH / UNION / MERGE |
| `id` | the sectorKey tagged on every record produced by the sync |

Records produced by the sync (imported taxa of genus rank or higher, accepted taxa below genus imported
so a project name can be demoted to them, and the synonyms copied for all of these) carry that
`sector_key` and `sector_mode = HIERARCHY`, so a previous run is wiped through the standard
`SectorProcessable.MAPPERS` deletion path before a new one starts. Project usages that were merely
*rewired* (parent_id or status updated) are not tagged — that keeps user data outside the sector's
deletion footprint.

## Pipeline Steps

The sync extends `SectorRunnable`, so it goes through the usual
`PREPARING → DELETING → INSERTING → MATCHING → INSERTING → ANALYZING → INDEXING → FINISHED`
state machine. The HierarchySync-specific work happens in `doWork()` across four passes followed
by three semantic phases.

### 0. `init()` — Target resolution

`HierarchySync.init()` resolves the effective source dataset:

- if `sector.subjectDatasetKey` is of origin **PROJECT**, calls
  `LatestDatasetKeyCache.getLatestRelease(projectKey, sector.useXRelease)`. Throws
  `NotFoundException` if no public release of the requested kind exists.
- if it is **RELEASE**, **XRELEASE**, or **EXTERNAL**, used as-is.
- any other origin is rejected with `IllegalArgumentException`.

The resolved key is stored in `sourceDatasetKey` and used by every subsequent pass. It is also written
into `verbatim_source.source_dataset_key` for every usage the sync inserts, which is the only record of
which release was read — the sector keeps naming the configured project, and `sector.dataset_attempt`
holds that project's attempt.

### 0b. What a release records as the source

The project's sector keeps pointing at the project: that is the configuration, and it has to keep
resolving to the latest release on the next sync. The copy in a **release** is a historical record
instead, so `SectorMapper.copyDataset` swaps a PROJECT subject for the release that was actually read,
recovering it from `verbatim_source` and taking `dataset_attempt` from that release as well. Both have
to move together — a release's attempt never changes, so the two stay equal and
`DatasetSourceMapper.listProjectSources` reads live metadata rather than looking for an archived copy
of the project, which `dataset_archive` never holds (only `PgImport` writes it, for reimported
external datasets). A release therefore cites the concrete (X)Release, with its DOI and version,
rather than the moving project. A sector without such provenance keeps its project subject.
See [`2026-09-05-release-source-provenance.md`](2026-09-05-release-source-provenance.md).

### 1. `deleteOld()` — Wipe previous run

Calls `SectorProcessable#deleteBySector(DSID)` on every mapper in `SectorProcessable.MAPPERS`,
deleting in the order required by foreign keys (vernacular → distribution → media →
species_interaction → name_usage → name → reference → verbatim_source). Anything tagged with this
sector's id from a previous run is gone before phase 1 starts, so the sync is repeatable.

### 2. Phase 1 — Upward classification copy

Implemented as four passes inside `syncHigherClassification()`:

#### 2a. `discoverMatches`

Streams every project usage via `NameUsageMapper.processDataset(projectKey, null, null)`. For each
usage, scans `NameUsageBase.identifier` for entries whose scope (resolved via
`IdentifierScopeResolver.resolve(sourceDatasetKey)`) matches the target and takes the first id that
still resolves in the source. Matches populate the in-memory maps used by phases 2 to 4:

- `projectMatches: projectId → targetId`
- `projectStatuses: projectId → TaxonomicStatus`

Usages already tagged with this sector's key are skipped defensively.

> Identifiers on `Name` are intentionally **not** consulted — only `NameUsageBase.identifier`.

Accepted usages without a usable identifier are then matched by name (see
[What the name-match fallback refuses to do](#what-the-name-match-fallback-refuses-to-do)). An
`EXACT` or `VARIANT` match that the matcher did not snap, to a source usage no other project usage
claimed, is **as good as an identifier**: it joins `projectMatches` and therefore goes through phases
2 to 4, and phase 1 adds the source identifier to the usage once the rewiring is done. Without that
identifier a usage demoted to a synonym would be lost on the next run, as synonyms are never name
matched. All other accepted name matches are used for placement only.

#### 2b. `collectAncestors`

For each match walks the source classification from the lazily filled `UsageCache` - the matched usage
first, then its parents up to the root. Ancestors of rank `GENUS` or higher are unioned into a single
map to import. The chain is recorded for rewiring only for usages that are accepted **both** in the
project and the source; a project usage the source has as a synonym is left to phase 2, which demotes
it. Moving it under the ancestors of its source accepted first is what put *Gyraulus crista* into the
genus *Armiger* ([backend#1582](https://github.com/CatalogueOfLife/backend/issues/1582)).

When the matched source usage is a synonym whose accepted taxon is ranked below genus and matched by no
project usage, that **accepted taxon is imported as well**, together with its source ancestor chain.
This applies to a project usage that is accepted (to be demoted) as much as to one that is a synonym
already - which is what a demoted usage is on the next run, after `deleteOld` removed the accepted it
pointed at.

#### 2c. `insertAncestorsTopDown`

Inserts the collected ancestors parents-before-children using a **topological sort over
`parent_id`** — rank-ordinal sorting is unreliable because UNRANKED and OTHER have the highest
ordinals despite sitting anywhere in the tree.

The loop pulls every taxon whose `parent_id` is null *or* whose parent is no longer in the pending
set (already inserted, or never in the required set), inserts that batch via
`CopyUtil.copyUsage(...)`, and repeats. If a round produces no insertable taxa we log a parent_id
cycle warning and skip the rest rather than spin forever.

For each insertion:

- `Taxon.sectorKey` and `Taxon.sectorMode` set to this sector's id and `HIERARCHY`; the same
  values are propagated to the `Name`.
- `parent_id` resolves to the new project id of the parent ancestor (or `null` if the parent is
  not in the imported set, making this ancestor a new project root).
- Reference linkage is dropped (`ref → null`) and no extension entities are copied.
- A target-dataset identifier (`new Identifier(targetScope, originalTargetId)`) is appended to the
  new project usage via `NameUsageMapper.addIdentifier(...)` so future runs can match by id.

The map `targetToProject: targetAncestorId → newProjectId` is built as a side effect and shared
with phases 2 and 3.

An imported accepted taxon below genus waits until no ancestor on its source chain is pending, as its
direct source parent (a species or subgenus) is usually never collected. It is placed under the
closest ancestor on that chain the project holds. Before importing anything the sync looks for an
equivalent accepted project usage (by identifier, then by name and rank) and reuses it; a project usage
that phase 2 is about to demote is never reused as its own accepted.

#### 2d. `rewireProjectParents`

Calls `NameUsageMapper.updateParentId(...)` for every matched project usage that is accepted in the
project and the source, re-anchoring it under the closest ancestor the project now holds. Synonyms and
usages the source has as synonyms are not rewired in this pass; phase 2 handles them. Afterwards
promoted name matches receive the source identifier.

Each move is checked with `wouldCreateCycle(...)` first, the same guard phases 2 and 5 use. Two
usages of this pass can otherwise be rewired onto each other — each move legal on its own, together
closing a 2-cycle — and the reindex at the end of the sync would then walk that cycle. Blocked moves
are logged and counted as `cycle-blocked`.

### 3. Phase 2 — Status realignment

`realignStatus()` iterates `projectMatches` twice, promotions first, and for each pair loads the
target usage to compare statuses. Promoting first matters for inverted synonymy: the accepted a
demotion needs may only exist as a project synonym that is about to be promoted. Decisions:

| project | target | action |
|---|---|---|
| accepted | accepted | no-op (phase 1 already rewired) |
| accepted | synonym | demote — `updateParentAndStatus(projectId, projectEquivOf(target.parentId), target.status)` |
| synonym | accepted | promote — `updateParentAndStatus(projectId, projectEquivOf(target.parentId), target.status)` |
| synonym | synonym | retarget the synonym's accepted parent (and align subtype if it differs) |

`projectEquivOf(targetId)` first checks `targetToProject` (newly imported ancestors, including
accepted taxa below genus imported for exactly this purpose) and falls back to a lazily-built reverse
of `projectMatches`. If neither resolves, the project usage is left untouched - we'd rather skip a
record than orphan it. Such demotions and promotions are logged at WARN, counted separately, and
summarised with a few example names as a warning of the sector sync.

Each successful update writes back into the in-memory `projectStatuses` so phase 3 can read the
post-realignment status without a re-query.

### 4. Phase 3 — Synonymy copy

`copySynonymies()` builds the universe of accepted (project, target) pairs:

- every entry of `targetToProject` (ancestors of genus rank or higher, and accepted taxa below genus
  imported for a demotion, all accepted by construction), plus
- every matched project usage whose effective `projectStatuses` value is `isTaxon()` (originally
  accepted, or promoted by phase 2). That includes full name matches.

For each accepted pair, `SynonymMapper.listByTaxon(DSID(sourceDatasetKey, targetAcceptedId))`
returns every synonym of the target's accepted taxon. Synonyms whose target id is already
represented in the project (`projectMatches.values()` ∪ `targetToProject.keySet()`) are skipped to
avoid creating duplicates of usages we already account for. Each remaining synonym is copied via
`CopyUtil.copyUsage(...)` with `parent = (projectKey, projectAcceptedId)`, tagged with the sector,
and identified back to the source.

Pre-existing project synonyms are intentionally not touched — they don't carry this sector's key,
so `deleteBySector` won't wipe them on the next run, and any local edits stick.

### 4b. Phase 4 — Authorship enrichment

`enrichAuthorship()` copies the authorship of the matched source name onto the existing project
name for every entry of `projectMatches` (regardless of taxonomic status). It is gated by the
sector's `authorshipUpdate` setting (`Sector.AuthorshipUpdate`):

| `authorshipUpdate` | behaviour |
| --- | --- |
| `NONE` (default) | phase skipped entirely — authorship never changes |
| `MISSING` | the source authorship is applied only when the project name has none yet |
| `ALWAYS` | the source authorship overwrites the project name's whenever the source has one |

Parsed authorship is copied as the structured combination/basionym/sanctioning parts and the cached
string rebuilt via `Name.rebuildAuthorship()`; an unparsed source only contributes its raw
authorship string. The provenance is recorded as an `InfoGroup.AUTHORSHIP` **secondary source** on
the project name's verbatim source (`VerbatimSourceMapper.insertSources(...)`), pointing back to the
source usage. Because the matched names are pre-existing project data **not** tagged with this
sector, they survive `deleteBySector`; any verbatim source created here for a name that lacked one
is left sector-less and the `AUTHORSHIP` secondary source is simply re-pointed on every run
(idempotent).

### 5. `doMetrics()` + `updateSearchIndex()`

Standard `SectorImport` metrics update via `SectorImportDao.updateMetrics(...)` and project
re-index via `NameUsageIndexService.indexSector(...)` — same as `SectorSync`.

## Triggering

A hierarchy sync runs through the existing sector REST surface. The dispatch lives in
`SyncManager.syncSector(...)` which loads `Sector.Mode` via `SectorMapper.getMode(...)` and routes
HIERARCHY-mode sectors to `SyncFactory.hierarchy(...)`; everything else goes to
`SyncFactory.project(...)` as before. So:

```
POST /dataset/{projectKey}/sector/{id}/sync
```

works for HIERARCHY-mode sectors with no new endpoint. The cancel path
(`DELETE /dataset/{projectKey}/sector/{id}/sync`) and `SectorImport` history work the same way.

## Repeatability

The combination of `deleteBySector` + sectorKey tagging means re-running the sync produces the same
end state regardless of how many times it has run. Concretely:

- imported ancestors, imported accepted taxa below genus and copied synonyms are wiped and re-imported
  with the same content **and the same ids**. Before `deleteOld` the sync reads its verbatim sources and
  keeps `source id -> project id`; the copy hands each previous id out again (at most once) for the same
  source id, and a new random one otherwise. Everything pointing at an import - children, demoted
  synonyms, a curator's edits, sector targets, estimates - is valid again once the run is done.
- vernacular names a merge sector attached to an import carry the merge sector's key, and their foreign
  key to `name_usage` *is* enforced, so they would make `deleteOld` fail. The sync takes them off its
  imports before deleting them and attaches them again to whatever represents the same source id after
  phase 1 - the re-import or an existing project usage it deduplicated against. Names whose source taxon
  is gone are dropped with a warning; the merge sector brings them back on its next sync.
- project usages that were rewired or had their status flipped are *not* tagged with the sector;
  the new run simply re-applies the same rewire / flip if the target still says so. A usage demoted
  to an imported accepted is a project synonym on the next run; it is found again by its source
  identifier - which is why promoted name matches gain one - and stays attached to the re-imported
  accepted.
- when the source dropped a taxon, whatever still points at its former import is repaired after the
  sync, failed syncs included: `TreeRepair.fixMissingParents` sets the parent to null and flags the usage
  `PARENT_ID_INVALID`, or `ACCEPTED_ID_INVALID` for a synonym, and the sync reports a warning with the count.
  `ProjectRelease` runs the same repair on the project before it maps ids, `XRelease` with its incertae
  sedis taxon as the new parent. See
  [`2026-09-11-hierarchy-sync-stable-ids.md`](2026-09-11-hierarchy-sync-stable-ids.md).

## Limitations / Future work

The two items deferred from v1 — the name-match fallback and project-side dedup of imported
ancestors — were both implemented on 2026-06-26 (see
[`2026-06-26-hierarchy-sync-name-match-fallback.md`](2026-06-26-hierarchy-sync-name-match-fallback.md)).
Phase 1 runs a name-match sub-pass after the same `discoverMatches` scan: accepted project usages
with no usable source identifier are matched against the source dataset. Full matches are promoted to
identifier matches (since 2026-09-11, see
[`2026-09-11-hierarchy-sync-demote-to-missing-accepted.md`](2026-09-11-hierarchy-sync-demote-to-missing-accepted.md));
the others are placed under their closest genus-or-higher anchor, flagged `Issue.MATCHING_HIGHERRANK`. No `TODO(hierarchy-sync)`
markers remain in the source.

### What the name-match fallback refuses to do

Four constraints, all added after it placed a project's unranked container `Biota` under the plant
genus *Platycladus* via the botanical genus synonym *Biota* D.Don ex Endl.
(see [backend#1575](https://github.com/CatalogueOfLife/backend/issues/1575)):

- **Unranked and OTHER names are not candidates.** `UsageMatcher` skips its rank filter outright for
  a null or `UNRANKED` query and its nomenclatural code filter needs a suprageneric rank, so such a
  name matches any canonical homonym at any rank in any kingdom. `TreeMergeHandler` guards the same
  shape for merge sectors.
- **Sector targets are not candidates.** A taxon other sectors attach into is a structural anchor of
  the project — usually a hand made container — and moving it drags every sector's output with it.
  Being at the project *root* is not disqualifying: placing exactly those names is the point.
- **`AMBIGUOUS` matches are rejected.** `UsageMatch.isMatch()` is only "a usage came back" and is
  true for `AMBIGUOUS`, so the accepted types are named explicitly:
  `EXACT`, `VARIANT`, `CANONICAL`, `HIGHERRANK`.
- **The query carries the usage's own project classification**, so `UsageMatcher` applies its
  taxonomic group filter — it only runs when the query has a classification. The scan therefore
  collects the whole project tree as `SimpleName`s first and matches afterwards; it is still a
  single pass over the project.

A source identifier that no longer resolves in the source does **not** count as an identifier match.
Sources delete and reissue ids, and trusting the mere presence of one shadowed the name fallback and
left the usage unplaced on every subsequent run. Such usages are counted and logged at WARN. A usage can
therefore carry a stale id next to the current one, added by a later full name match; the first id that
still resolves wins.

These constraints guard every name match, including the full ones that get promoted to an identifier
match. Only `EXACT` and `VARIANT` are promoted, and only when the matcher did not snap to the usage: a
snap is its pick among several candidates, for example two source synonyms of the same accepted taxon.
`CANONICAL`, `HIGHERRANK` and snapped matches stay placement only and flag the usage
`MATCHING_HIGHERRANK`; see [the design record](2026-09-11-hierarchy-sync-demote-to-missing-accepted.md).

What is still open, mirroring the javadoc on `HierarchySync`:

- **The window during a sync.** Between `deleteOld` and the re-import the imported rows are gone, so
  the project serves dangling references for the minutes a sync runs, like with every other sector sync.
- **Dropped source taxa lose more than their children's parent.** The repair only covers `parent_id`.
  An attach sector targeting a taxon the source dropped is left with a broken target and estimates with
  a broken reference - both are flagged as such and can be rematched.
- **Wrong full name matches stick.** A promoted name match gains the source identifier, which
  `deleteBySector` does not remove from untagged usages, so later runs follow that identifier.
- **Snapped matches stay placement only.** When a name matches several source synonyms of the same
  accepted taxon (e.g. *Gyraulus crista* and *Gyraulus (Armiger) crista*) the matcher snaps to one of
  them and the usage is not demoted by name.
- **Bad placements are not self-healing.** A rewire is not tagged with the sector, so a re-run does
  not undo one — `placeNameMatches` sees the parent already equals the target and re-affirms it. A
  wrong placement has to be corrected in the project by hand.
- **Performance batching.** Phase 2 / 3 do per-match `NameUsageMapper.get` and
  `SynonymMapper.listByTaxon` calls. For very large projects these can be batched via `listByIds`
  or a streaming join.
- **Phase-1 name-match cost and memory.** The fallback runs a per-usage source-matcher lookup for
  every accepted usage lacking a source identifier, and the scan holds one `SimpleName` per project
  usage so classifications can be walked in memory. Both are sized for the projects this feature
  targets — small checklists delegating their higher classification.

## Important Files

| Purpose | Path |
|---|---|
| Sync runnable | `core/src/main/java/life/catalogue/assembly/HierarchySync.java` |
| Mode + useXRelease on the sector model | `api/src/main/java/life/catalogue/api/model/Sector.java` |
| Schema additions (enum value + column) | `dao/src/main/resources/life/catalogue/db/dbschema.sql` |
| Mapper round-trip of `use_x_release` | `dao/src/main/resources/life/catalogue/db/mapper/SectorMapper.xml` |
| Factory wiring | `core/src/main/java/life/catalogue/assembly/SyncFactory.java` |
| Mode dispatch | `core/src/main/java/life/catalogue/assembly/SyncManager.java` |
| Latest-release lookup (reused) | `dao/src/main/java/life/catalogue/cache/LatestDatasetKeyCacheImpl.java` |
| Identifier scope resolution (reused) | `core/src/main/java/life/catalogue/matching/IdentifierScopeResolver.java` |
| Classification walk (reused) | `dao/src/main/java/life/catalogue/dao/TaxonDao.java` + `TaxonMapper.xml` |
| Copy primitive (reused) | `dao/src/main/java/life/catalogue/dao/CopyUtil.java` |
| Sector-scoped deletion contract (reused) | `dao/src/main/java/life/catalogue/db/SectorProcessable.java` |
| Integration test | `core/src/test/java/life/catalogue/assembly/HierarchySyncIT.java` |
