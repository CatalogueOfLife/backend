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

Records produced by the sync (imported above-genus taxa + their copied synonyms) carry that
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

The resolved key is stored in `targetDatasetKey` and used by every subsequent pass.

### 1. `deleteOld()` — Wipe previous run

Calls `SectorProcessable#deleteBySector(DSID)` on every mapper in `SectorProcessable.MAPPERS`,
deleting in the order required by foreign keys (vernacular → distribution → media →
species_interaction → name_usage → name → reference → verbatim_source). Anything tagged with this
sector's id from a previous run is gone before phase 1 starts, so the sync is repeatable.

### 2. Phase 1 — Upward classification copy

Implemented as four passes inside `syncHigherClassification()`:

#### 2a. `discoverIdentifierMatches`

Streams every project usage via `NameUsageMapper.processDataset(projectKey, null, null)`. For each
usage, scans `NameUsageBase.identifier` for an entry whose scope (resolved via
`IdentifierScopeResolver.resolve(targetDatasetKey)`) matches the target. Matches populate two
in-memory maps used by phases 2 and 3:

- `projectMatches: projectId → targetId`
- `projectStatuses: projectId → TaxonomicStatus`

Usages already tagged with this sector's key are skipped defensively.

> Identifiers on `Name` are intentionally **not** consulted — only `NameUsageBase.identifier`.

#### 2b. `collectAncestors`

For each match calls `TaxonMapper.classification(DSID(targetDatasetKey, targetId))`. The recursive
CTE returns the parent chain ordered immediate-parent-up-to-root, excluding the start node.
Ancestors with rank strictly higher than `GENUS` (`Rank.higherThan(GENUS)`) are unioned into a
single `Map<targetId, Taxon>`. The first such ancestor becomes the project usage's
**immediate above-genus ancestor** — but only recorded for **accepted** project usages; synonyms
must keep `parent_id` pointing at an accepted taxon, not at a higher-rank ancestor.

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

#### 2d. `rewireProjectParents`

Calls `NameUsageMapper.updateParentId(...)` for every **accepted** matched project usage,
re-anchoring it under its newly-imported immediate above-genus ancestor. Synonyms are not rewired
in this pass; phase 2 handles them.

### 3. Phase 2 — Status realignment

`realignStatus()` iterates `projectMatches` and, for each pair, loads the target usage to compare
statuses. Decisions:

| project | target | action |
|---|---|---|
| accepted | accepted | no-op (phase 1 already rewired) |
| accepted | synonym | demote — `updateParentAndStatus(projectId, projectEquivOf(target.parentId), target.status)` |
| synonym | accepted | promote — `updateParentAndStatus(projectId, projectEquivOf(target.parentId), target.status)` |
| synonym | synonym | retarget the synonym's accepted parent (and align subtype if it differs) |

`projectEquivOf(targetId)` first checks `targetToProject` (newly imported ancestors) and falls back
to a lazily-built reverse of `projectMatches`. If neither resolves, the project usage is left
untouched and counted as `unresolved` in the run summary — we'd rather skip a record than orphan
it.

Each successful update writes back into the in-memory `projectStatuses` so phase 3 can read the
post-realignment status without a re-query.

### 4. Phase 3 — Synonymy copy

`copySynonymies()` builds the universe of accepted (project, target) pairs:

- every entry of `targetToProject` (above-genus ancestors imported in phase 1 — accepted by
  construction), plus
- every matched project usage whose effective `projectStatuses` value is `isTaxon()` (originally
  accepted, or promoted by phase 2).

For each accepted pair, `SynonymMapper.listByTaxon(DSID(targetDatasetKey, targetAcceptedId))`
returns every synonym of the target's accepted taxon. Synonyms whose target id is already
represented in the project (`projectMatches.values()` ∪ `targetToProject.keySet()`) are skipped to
avoid creating duplicates of usages we already account for. Each remaining synonym is copied via
`CopyUtil.copyUsage(...)` with `parent = (projectKey, projectAcceptedId)`, tagged with the sector,
and identified back to the source.

Pre-existing project synonyms are intentionally not touched — they don't carry this sector's key,
so `deleteBySector` won't wipe them on the next run, and any local edits stick.

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

- imported above-genus ancestors are wiped and re-imported (with the same content; new ids).
- imported synonyms (phase 3) are wiped and re-imported.
- project usages that were rewired or had their status flipped are *not* tagged with the sector;
  the new run simply re-applies the same rewire / flip if the target still says so.

## Limitations / Future work

These are deliberately deferred from v1 and tracked inline in the source as
`TODO(hierarchy-sync)`:

- **Name-match fallback.** Project usages without a target identifier are currently skipped. The
  infrastructure (`nameIndex`, `matcherSupplier`) is already injected and ready to run unmatched
  usages through `UsageMatcher` against `targetDatasetKey`.
- **Project-side dedup of imported ancestors.** When the project already has an equivalent family
  or order, phase 1 inserts a new copy. A canonical-name + rank lookup against the project before
  copying would let us reuse existing nodes (without tagging them with this sector — we don't want
  `deleteBySector` to wipe user data on re-run).
- **Performance batching.** Phase 2 / 3 do per-match `NameUsageMapper.get` and
  `SynonymMapper.listByTaxon` calls. For very large projects these can be batched via
  `listByIds` or a streaming join.

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
