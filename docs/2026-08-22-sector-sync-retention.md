# Prune intermediate sector sync metrics and their names files, keeping every attempt a release or the project pins

Date: 2026-08-22

## Problem

`sector_import` is the largest history table in the system and the fastest growing, and it has a
matching file on disk for every row.

Measured against prod (`api.checklistbank.org`) on 2026-08-22:

| Bucket | Rows |
|---|---:|
| sector syncs (`sector_import`, all on project COL, key 3) | **6,934,297** |
| dataset imports (`dataset_import`) | 933,329 |
| exports (`dataset_export`) | 2,962 |

Sync states: 6,828,002 finished / 105,990 failed / 431 canceled.

COL has **62,946 sectors** and the whole fleet re-syncs weekly — paging back exactly 62,946 sync rows
lands exactly one week earlier. So the table grows by **~63k rows/week ≈ 3.3M/year**, and ~99% of all
job history growth is sector syncs.

Every one of those attempts also writes a gzipped names file at
`<metricsRepo>/dataset/<bucket>/<datasetKey>/sector/<sectorId>/<attempt>-names.txt.gz`. That is
**~6.9M small files across 62,946 directories** (~110 per directory) — an inode and backup problem as
much as a space one.

The rows cannot simply be deleted by age: release source metrics are computed live from them (see
below), so a naive prune would silently produce *wrong numbers* for every historical release rather
than an error.

## Goal

1. Prune `sector_import` rows and their names files that no release and not the project depends on,
   cutting ~6.9M rows to ~500k and stopping the growth at ~267k/year.
2. Stop writing, and clean up, the ~4.1M names files that contain no names at all — taking the file
   repo to ~200k.
3. Keep per-sector metrics for every release, so a release's sectors can still be inspected
   individually rather than only in aggregate.
4. Never delete the attempt the project currently points at, however old it is.
5. Keep the full sync *history* (what ran, when, whether it failed) available even for attempts whose
   metrics were pruned.
6. Change no metric value anywhere: `releaseMetrics` and `sourceMetrics` output must be byte
   identical before and after.

## Key facts (verified in code)

- **`sector.sync_attempt` is the pin, in both places.** `SectorRunnable` (line ~207) calls
  `SectorMapper.updateLastSync(sectorKey, attempt)` **only on successful completion**, gated on
  `updateSectorAttemptOnSuccess`. `SectorMapper.updateReleaseAttempts` copies the project's
  `sync_attempt` onto the release's own sector row at release time. So the project's sector row pins
  its current sync and each release's sector row pins the sync as of that release.
- **"Current" already means the pin, not the max attempt.** `SectorImportMapper.xml`'s `WHERE`
  fragment renders `current=true` as `AND si.attempt = s.sync_attempt`. `DatasetSourceDao.sourceMetrics`
  uses it for the non-release path.
- **Release metrics read the project's rows at the pinned attempt.** `DatasetSourceDao.releaseMetrics`
  and `addReleaseMetrics` iterate the *release's* copied `sector` rows and fetch
  `sim.get(DSID.of(projectKey, s.getId()), s.getSyncAttempt())` — from the **project**, not the
  release. Nothing is persisted on the release and there is no cache: metrics are recomputed on every
  API call, one row read per sector.
- **254,127 sector rows exist across all 50 COL releases** (240,952 from XReleases, 13,175 from the
  rest). That is the currently pinned set.
- **Release cadence is monthly**, one regular release plus one XRelease. Only XReleases carry the
  merge sectors (~21.6k); regular releases pin far fewer (several sampled pin none at all).
- **Sector modes are overwhelmingly MERGE**: 62,340 MERGE / 603 ATTACH / 3 UNION on the project.
- **Deletion primitives already exist.** `FileMetricsDao.deleteAttempt(key, attempt)` deletes one
  names file; `FileMetricsSectorDao.subdir` gives the sector directory. Note the base
  `deleteAttempt` deletes only the names file — `FileMetricsDatasetDao` overrides it to also drop the
  tree file, but **sectors have no tree file**, so the base behaviour is correct for us.
- **`CleanFileRepoCmd` already carries this gap as a TODO**: `// we dont store deleted sectors in the
  db  // TODO: go through the files`.
- **`SectorImportMapper` has no per-attempt delete.** Its `delete(key)` removes *all* attempts of a
  sector. A new mapper method is required.
- **`SectorDiffService.parseAttempts` defaults to the last 2 finished sector imports**
  (`new Page(0, 2)`), serving `SectorDiffResource`. Any retention rule must leave something to diff.

## Design

### The retention rule

Delete a `sector_import` row **and** its `<attempt>-names.txt.gz` if and only if **both**:

1. its `(sector_key, attempt)` does **not** appear as a `sector.sync_attempt` across the project or
   any of its releases, **and**
2. its `started` is non-null and strictly older than the cutoff, where the cutoff is
   `max(dataset.created)` over the project's releases.

A row with a null `started` (queued but never run) is always kept by condition 2 — it cannot be
meaningfully compared to the cutoff, and there are few of them.

Expressed as the set to keep:

```sql
-- pinned by the project (current sync) or by any release
SELECT DISTINCT s.id AS sector_key, s.sync_attempt AS attempt
FROM sector s
WHERE s.sync_attempt IS NOT NULL
  AND (s.dataset_key = :projectKey
       OR s.dataset_key IN (SELECT key FROM dataset
                            WHERE source_key = :projectKey
                              AND origin IN ('RELEASE','XRELEASE')))
```

Condition 1 satisfies goals 3 and 4 at once: the project's own sector row is in that set, so **the
latest successful sync is always kept regardless of age**, and every release keeps its per-sector
metrics.

Deleted releases behave correctly without special handling, but not for the reason one might assume.
Dataset deletion is soft (the row survives with `deleted` set; only temporary datasets are removed
physically), and `DatasetDao` deliberately **keeps** `sector` rows for deleted *public* releases —
`// We want to keep the sector and sector_publisher entries for deleted, public release !!!` — while
removing them for private ones. So a deleted public release keeps its pins and therefore its
per-sector metrics, matching the existing intent to preserve public release provenance; a deleted
private release drops its sector rows and its pins with them. The query needs no `deleted` predicate
either way.

Condition 2 is the "keep everything since the last release" window — recent syncs stay available for
debugging even when unpinned.

The rule also resolves the diff requirement without a special case: because each release pins an
attempt, a sector retains one row per release it appeared in, so `SectorDiffService`'s default last-2
always has something to compare once a sector has been through two releases.

### Expected effect

| | rows |
|---|---:|
| pinned by a release | ~254k |
| pinned by the project (current sync per sector) | ≤63k, mostly overlapping the above |
| unpinned but newer than the last release (~4 weekly syncs × 62,946) | ~250k |
| **kept** | **~500k** |
| **pruned** | **~6.43M (93%)** |

Ongoing growth becomes ~267k/year (12 XReleases × ~21.6k + 12 regular × ~600) plus a rolling
window that does not accumulate — down from ~3.3M/year.

Files fall further than rows, because the empty-file rule applies on top and cuts *kept* attempts
too: of the ~500k retained rows, ~60% have `nameCount == 0` and so keep no file. The repo goes from
~6.9M names files to roughly **200k** — and stops accruing ~63k empty files a week.

### Empty names files

Separately from the row retention rule, most names files hold nothing at all.

Measured over 6,000 sampled syncs, **`nameCount == 0` in 59.9%**, correlating perfectly with
`usagesCount == 0` — the cross-tab has only the two diagonal cells populated (3,593 both zero, 2,407
both non-zero), no mixed cases. `FileMetricsDao.updateNames` opens its `NamesWriter` in
try-with-resources *before* consuming any rows, so each of those still writes a gzip-header-only
file: **~4.1M files carrying nothing the metrics row does not already record.**

Three changes, none of which touch a metric value:

1. `updateNames` writes no file when the writer emitted zero names. It already counts them
   (`nHandler.counter`), so this is a check after the consume, deleting the file it just opened.
2. The retention job deletes an existing names file whenever the row it keeps has `nameCount == 0`.
   This is independent of the keep/delete decision for the row itself — a *kept* row can still have
   its empty file removed.
3. `FileMetricsDao.streamFile` currently throws `AttemptMissingException` (a `NotFoundException`)
   for a missing file, which would break `getNames` and the diff endpoints for exactly this 60%.
   `getNames` must instead return an empty stream when the file is absent **and** a `sector_import`
   row for that attempt records `nameCount == 0`, and 404 otherwise. No new column is needed: the
   retained row carries `nameCount`. When the row itself was pruned, 404 remains correct — that
   attempt really is gone.

### Why metric rows are not pruned on emptiness

An earlier version of this design proposed deleting "empty" metric rows and recording a magnitude on
the job row instead. Measurement killed it:

- Only **2.9%** of syncs are empty across *every* numeric and map metric (n=7,000). The 61.4% with
  `usagesCount == 0` overwhelmingly still carry `ignoredByReasonCount` (3,996 of 4,093 such rows),
  `typeMaterialCount` / `typeMaterialByStatusCount` (1,996 each) and `referenceCount` (476).
- **30.8% of all syncs have `ignoredByReasonCount` as their only metric**, dominated by `rank`
  (5,075 of 5,160 ignores), typically 2-4 per sync — e.g. `{'rank': 4}`. That is the diagnostic
  explaining why a Plazi article contributed nothing, and `ImportMetrics.add` sums it
  (line 559), so deleting such rows would visibly change every release's aggregate ignored counts.
  Unlike an all-zero row, whose deletion is provably a no-op, these are load-bearing.
- Merge-only work *is* recorded, via `secondarySourceByInfoCount` — populated by `SectorImportDao`
  from `verbatim_source_secondary` grouped by `InfoGroup`, i.e. the added-authorship-from-a-secondary-
  source count. A sync that only updated existing usages is therefore not indistinguishable from a
  no-op, as an earlier draft assumed.

A 2.9% win does not justify a schema change and a read-path change. The release-pinning rule already
discards the weekly repeats, which is where the real redundancy lives.

### Components

**`SectorImportRetentionJob`** — a `BackgroundJob` (so it gets a job row, a log file and shows up in
the job history like everything else), taking a project key and a `dryRun` flag.

1. Resolve the cutoff: `max(dataset.created)` over the project's releases, private and soft-deleted
   ones included — counting them only ever moves the cutoff later and so keeps more. If the project
   has no release at all, the job does nothing and says so: there is no safe cutoff.
2. Load the pinned set with the query above into an in-memory set of `(sectorKey, attempt)`. At ~254k
   pairs of two ints this is a few MB; no need to stream it.
3. Page through `sector_import` for the project ordered by `(sector_key, attempt)`, selecting only
   `sector_key, attempt, started` — never the metric columns, which are the bulk.
4. For each row failing both keep-conditions, collect it. Delete in batches (a few thousand rows per
   transaction) via a new `SectorImportMapper.deleteAttempts` method, and delete the corresponding
   names file with `FileMetricsSectorDao.deleteAttempt(DSID.of(projectKey, sectorKey), attempt)`.
5. Report counts: rows examined, rows deleted, files deleted, files already missing.

Deleting the DB row and the file for the same attempt happens in the same batch, DB first. If the
file delete fails, `deleteOrWarn` already logs and continues — an orphaned file is harmless and gets
picked up by a later run only if the row still exists, so the job should log the count of files it
could not delete rather than fail.

**`dryRun`** performs steps 1-3 and reports exactly what step 4 would delete, changing nothing. This
is how the rule gets validated against prod before anything is destroyed.

**Triggering.** The job is submitted after a release completes, not run inside it.
`AbstractProjectCopy.runWithLock()` has a `postMetrics()` hook, but running deletion there would let a
failed prune fail a release and a long prune stretch it. Instead the release submits the retention job
to the executor on success. It is also invocable manually from the admin API for the initial run.

**Sync history from `job`.** Pruning removes per-attempt *metrics*, and the sync history endpoint
(`GET /dataset/{key}/sector/sync`) is driven by `sector_import`, so pruned attempts would vanish from
the API entirely. Since the unified-jobs work keeps a slim `job` row for every sync forever
(~250 bytes with indexes), point that endpoint at `job` filtered by `sector_key` so the full history
survives with status, timings and error — just without metrics.

### Rejected alternative: aggregate release metrics into a snapshot table

The original plan (and the premise of issue #1562) was to snapshot aggregated metrics per
`(release, source, mode)` at release time, decouple `DatasetSourceDao` from `sector_import`, and then
prune freely. Rejected because:

- it throws away per-sector detail per release, which is worth keeping (goal 3);
- it needs a new table mirroring ~60 metric columns, aggregation code at release time, a backfill job
  for the 50 existing releases, and a changed read path — versus a single retention job here;
- a stored aggregate can silently drift from what live aggregation would produce, and there is no
  cheap way to detect that.

Keeping the pinned rows gets a 93% reduction with none of that machinery. **Issue #1562 must be
rewritten**, as its stated premise ("release metrics must be decoupled before anything can be
pruned") is wrong: they only need to be *not deleted*.

## Out of scope

- Retention for `dataset_import` and its names/tree files (933k rows, ~1.9M files). Same shape of
  problem, an order of magnitude smaller, and imports have no equivalent of the release pin — a
  separate decision.
- Retention for the `job` table itself. Job rows are deliberately kept forever here so history
  survives; at ~250 bytes and ~3.3M/year that is ~800MB/year, revisitable later.
- Range-partitioning `job` by `created`.
- Any change to how release or project source metrics are computed. The read path is untouched.

## Testing

- **Retention rule unit tests** over a fixture project with sectors, several attempts each, and two
  releases pinning different attempts. Assert: pinned-by-release kept; pinned-by-project kept even
  when older than the last release; unpinned-and-old deleted; unpinned-but-newer-than-last-release
  kept; a sector whose latest attempts all failed keeps its last successful (pinned) attempt.
- **No-release guard**: a project with no releases deletes nothing.
- **File/row pairing**: after a run, every remaining `sector_import` row with `nameCount > 0` has its
  names file, every row with `nameCount == 0` has none, and every deleted row has none. Covers the
  `FileMetricsSectorDao` path layout.
- **Empty names file is not written**: a sync producing zero names leaves no file, and one producing
  names does.
- **`getNames` on an empty attempt returns an empty stream, not 404**, when the row records
  `nameCount == 0` and the file is absent; it still throws `AttemptMissingException` when no row
  exists for the attempt. Both branches tested, since this is the regression risk for the diff
  endpoints.
- **Sector names diff across an empty attempt** still succeeds end to end via `SectorDiffService`.
- **Idempotence**: running the job twice deletes nothing the second time.
- **Dry run changes nothing**: row and file counts identical before and after, with the same delete
  count reported as a real run.
- **Metrics unchanged after pruning** — the load-bearing test: capture `releaseMetrics` and
  `sourceMetrics` output for every release in the fixture, run the job, assert byte-identical results.
  This is what proves the rule is safe.
- **Integration**: extend the existing sector sync IT setup rather than building a new fixture.

## Phasing

Three commits, in order, because step 3 is irreversible:

1. Sync history served from `job`. No deletion; independently useful.
2. `SectorImportRetentionJob` with dry-run only, plus the admin endpoint. Verifiable against prod.
3. Actual deletion and the post-release trigger, once the dry-run numbers are confirmed.
