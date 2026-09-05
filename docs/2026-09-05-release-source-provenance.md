# A release archives the release it read, not the project it was configured against

Date: 2026-09-05
Status: shipped

## What went wrong

`ProjectRelease #14` of project 315784 (Dutch bio-archaeological taxa list) failed on prod on
2026-09-04:

```
ERROR: null value in column "key" of relation "dataset_source"
### The error may involve life.catalogue.db.mapper.DatasetSourceMapper.create-Inline
```

The project has a HIERARCHY sector whose subject is the **COL project** (dataset 3), synced when COL
was on attempt 623. COL has since released three more times and sits at 626. With
`src.attempt (623) != src.dattempt (626)`, `DatasetSourceMapper.listProjectSources` took its archived
metadata branch:

```sql
LEFT JOIN dataset_archive d ON d.key = src.key AND d.attempt = src.attempt
```

`dataset_archive` is only ever written by `PgImport.updateMetadata()`, which archives the *previous*
attempt when an **external dataset is reimported**. A project is never imported, so dataset 3 has no
archive rows at all — on prod `GET /dataset/3/623`, `/3/626` and `/3/620` all answered 404, while
`GET /dataset/53677/23` (an external source that had been reimported) answered 200. The left join
matched nothing, every `d.*` column came back NULL, and `ProjectRelease.finalWork()` inserted that
all-NULL `SourceDataset` verbatim. Release #12 on Sept 1 had worked only because COL's attempt was
still 623 then.

## The decision

Two things were wrong, and they are worth keeping apart.

**A release should name the release it read.** `HierarchySync.resolveSourceDatasetKey()` resolves a
PROJECT subject to a concrete (X)Release and reads from that, and it already records which one:
`verbatim_source.source_dataset_key`. On prod,
`/dataset/316239/taxon/ch3gWUbN_TNz2ta5WQaus2/source` returned `sourceDatasetKey: 315834`, i.e.
COL26.7 XR (attempt 607, DOI `10.48580/dgykv`), while the sector said `subjectDatasetKey: 3,
datasetAttempt: 623`. Only the sector level metadata still pointed at the project. The release is what
was actually read and what should be cited — it has a DOI, a version and an issued date.

**Configuration and history are different facts.** The project's own sector must keep
`subject_dataset_key = <project>`: that is what the curator configured, and overwriting it would pin
the sector to one release forever and make `useXRelease` meaningless. The sectors copied into a
release are an immutable record of what that release contains, so the swap belongs there and only
there.

Swapping also dissolves the crash rather than papering over it: a release's `attempt` is set once and
never moves, so `src.attempt = src.dattempt` always holds and the archive branch is never entered.

## Goals

- A release's `dataset_source` names the (X)Release a hierarchy sector really read.
- The project keeps its configuration, so the next sync still resolves to the latest release.
- No query can hand out a `Dataset` with a null key again.
- No schema migration and no data backfill — the fix has to work on the prod data as it stands, since
  a release was blocked on it.

## Non-goals

- Changing what `sector.dataset_attempt` means. `SectorMapper.listOutdatedSectors` compares the
  *subject's* attempt to flag stale sectors, and for a hierarchy sector on COL that is exactly right:
  a new COL release should mark it outdated. Only the release's copy takes the resolved attempt.
- Making the live project view name a release. `/dataset/<project>/source` legitimately shows
  "Catalogue of Life", because the project is configured against the project and will re-sync to newer
  releases. It now shows live project metadata instead of a null-key entry.

## Rejected alternatives

- **A `sector.source_dataset_key` column**, set at sync time next to `sync_attempt`/`dataset_attempt`
  and copied into releases. Explicit and a single indexed lookup, and robust even if `verbatim_source`
  were ever incomplete. Rejected because the fact is already persisted per usage in
  `verbatim_source.source_dataset_key`, and `AbstractProjectCopy` already copies that table into the
  release — the column would be a denormalised second copy needing a migration *and* a backfill from
  `verbatim_source` anyway, while the release on prod was blocked.
- **Overwriting `subject_dataset_key` on the project's own sector.** Destroys the configuration.
- **Re-resolving the latest release at archive time.** The latest release may have moved on since the
  sync, so the release would cite something it never read.
- **Archiving project metadata into `dataset_archive`** whenever a project's attempt advances,
  mirroring `PgImport`. Fixes the missing row but keeps citing the project, and does nothing for the
  attempts already lost.

## What shipped

- `SectorMapper.copyDataset` swaps `subject_dataset_key` and `dataset_attempt` together for PROJECT
  subjects, resolved by a `LEFT JOIN LATERAL` into the *project's* `verbatim_source` (so it does not
  depend on `copyTable(Sector…)` running before `copyTable(VerbatimSource…)`), `LIMIT 1` on the
  existing `(dataset_key, sector_key)` index. No provenance ⇒ the project key is kept, as before.
- The archive branch of `listProjectSources`, `listProjectSourcesSimple`, `getProjectSource` and
  `getProjectSourceSimple` now **inner** joins `dataset_archive`, and the live branch picks up any
  source whose archive row is missing. The branches stay mutually exclusive and total.
- `ProjectRelease.finalWork()` skips and logs a source with a null key instead of failing the insert.

## Outcome

Verified red-to-green: with the mappers reverted, `SectorMapperTest` reports
`expected:<1001> but was:<1000>` (the project key, unswapped),
`DatasetSourceMapperTest` reports `expected:<1000> but was:<null>`, and
`ProjectReleaseIT.releaseArchivesTheResolvedReleaseAsSource` fails with the production error verbatim,
`null value in column "key" of relation "dataset_source"`.

Deviations from the plan:

- The release IT needed `sector_key` on the usages of the shared `project` test fixture
  (`core/src/test/resources/test-data/project/name_usage_3.csv`) — without it no sector counts as a
  source at all, so the release archived nothing and the path was never exercised. The names in that
  fixture already carried `sector_key=1`. `IdProviderIT`, the only other user of the fixture, is
  unaffected.
- Not fixed, noticed in the same loop: `ProjectRelease.finalWork()` passes the **release job's**
  attempt to `cm.createRelease(d.getKey(), newDatasetKey, attempt)`, but
  `CitationMapper.createRelease` reads `dataset_archive_citation` keyed by the *source's* attempt, so
  source bibliographies are copied only when the two numbers coincide. It looks like it should be
  `d.getAttempt()`. Currently invisible on prod: `dataset_citation` was empty for every dataset
  sampled, including all 160 sources of the latest COL release.
