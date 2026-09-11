# Hierarchy sync: stable ids and repair of dangling parents

Date: 2026-09-11
Status: implemented on branch `feat/hierarchy-stable-ids`, stacked on
[#1583](https://github.com/CatalogueOfLife/backend/pull/1583), not yet released
Follows: [2026-09-11-hierarchy-sync-demote-to-missing-accepted.md](2026-09-11-hierarchy-sync-demote-to-missing-accepted.md)

## Problem

A hierarchy sync starts by deleting everything its sector imported (`deleteOld`, a plain `DELETE` per
`SectorProcessable` mapper) and then imports it again, until now under new random ids. Untagged rows
keep pointing at the deleted ones:

- accepted usages rewired under an imported ancestor, and name matches placed under one,
- usages demoted to a synonym of an imported accepted (since the fix for backend#1582),
- a curator's edits under an imported taxon, attach sector targets, estimates,
- vernacular names and other extensions a merge sector adds to imported taxa.

Usages matched again on the next run are re-pointed; everything else dangles. The partitioned
`parent_id` self reference is not enforced in prod (PostgreSQL before 17.5 missed the catalog entries
for partitions created after the constraint), so nothing stops it. What a dangling parent does:

- `ProjectRelease` aborts: `IdProvider` walks every classification through `UsageMatcherStore.analyze`,
  which throws on a missing parent. Only `XRelease.flagLoops` repaired missing parents.
- Indexing a dataset aborts on a synonym without its accepted (`NameUsageProcessor`).
- Tree views, taxon metrics and subtree exports walk from the roots and silently lose the subtree.

## Goal

- Re-import a taxon the source still has under the project id it had before, so references stay valid
  after a successful run.
- After every sync - failed ones included - set the parent of usages pointing at a missing row to null
  and flag them `PARENT_ID_INVALID` (accepted) or `ACCEPTED_ID_INVALID` (synonym), with a sync warning.
- Run the same repair on the project before `ProjectRelease` maps ids, and reuse it in `XRelease`.

## Non-goals

- The window during a sync, between `deleteOld` and the re-import, stays. Every sector sync has it, and
  a single transaction around it is ruled out by the 15 minute idle-in-transaction limit in prod.
- Rematching attach sector targets and estimates whose taxon the source dropped. Stable ids keep them
  valid as long as the taxon exists; broken targets are already flagged and have rematch endpoints, and
  `HierarchySync` has no estimate dao wired.
- Recreating the parent FK so postgres enforces it.

## Design

- **Stable ids.** Before `deleteOld` the sync streams its sector's verbatim sources and usages and keeps
  `source id -> project id`. Every imported usage carries a verbatim source with its source id, so this does
  not depend on an identifier scope being configured. `insertAncestorsTopDown` and `copySynonymies` pass an
  id supplier to `CopyUtil.copyUsage` that hands out each previous id at most once and falls back to a new
  random one. COL ids are stable across releases, so this survives a newer source release.
- **Repair.** `TreeRepair.fixMissingParents` in the dao module lists `NameUsageMapper.listMissingParentIds`,
  points each at the fallback parent (null for projects) and flags it through `IssueAdder`. `HierarchySync`
  runs it from the `finally` of `doWork` once the old imports are deleted; a failure of the repair is logged
  and never masks the sync's own error.
- **Releases.** `ProjectRelease.prepWork` runs the repair on the project before building the `IdProvider`,
  next to the existing orphan removal. `XRelease.flagLoops` calls it with the incertae sedis taxon.

## Rejected alternatives

- **Relink foreign children around the delete** like `SectorSync.relinkForeignChildren`. There is no valid
  temporary parent for a synonym, and every other kind of reference needs its own rematch.
- **Diff instead of delete and re-insert.** Would avoid the window too, but rewrites the repeatability contract
  every sector mode shares.
- **Only flag, keep the dangling parent.** Releases and indexing would still fail on it.
- **Fail the sync.** The half-synced sector guard would then block releases until a curator fixes it by hand.

## Outcome

- Two assumptions were checked with failing tests before any code changed:
  - `ProjectRelease` really aborts on a missing parent: `NotFoundException: NameUsage 3:13 does not exist` from the
    id provider (`ProjectReleaseIT.releaseRepairsMissingParents`).
  - A vernacular name a merge sector attached to an imported taxon **fails the next hierarchy sync** outright:
    `vernacular_name.taxon_id` is enforced, unlike the `parent_id` self reference
    (`HierarchySyncIT.foreignVernacularOnImportedTaxonSurvivesResync`). So the sync now takes those names off its
    imports before deleting them and attaches them again after phase 1 - to the re-import, or to the project usage
    it was deduplicated against. Names of taxa no longer imported are dropped with a sync warning; if a sync fails
    midway, names that could not be attached are lost until their merge sector syncs again.
  - `SectorSync.deleteOld` deletes the same way, so an attach or union sector with merge sector vernaculars on its
    usages presumably fails likewise. Not addressed here.
- Merge sectors only ever add vernacular names to usages they do not own (`TreeMergeHandler`), so no other
  extension type needs the same treatment.
- `XRelease.flagLoops` now repairs through `TreeRepair`: a synonym whose accepted is missing is flagged
  `ACCEPTED_ID_INVALID` instead of `PARENT_ID_INVALID`.
- Tests: `TreeRepairTest` (dao), `HierarchySyncIT` (ids kept across re-syncs, a curator's usage under an import,
  a merge sector vernacular, repair after a dropped source taxon and after a failed sync), `ProjectReleaseIT`; the
  existing `XReleaseIT` and `XReleaseBasicIT` pass unchanged.
- Deferred as stated in the non-goals: rematching attach sector targets and estimates of dropped taxa.
