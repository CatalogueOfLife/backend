# Keep unchanged imports out of the way, not out of the record

Date: 2026-08-27
Status: shipped

## Problem

Turning on `ContinuousImporter` floods the job history with no-op imports. When the freshly downloaded
archive has the same MD5 as the last attempt, `ImportJob.prepareSourceData` returns false, the whole
import body is skipped, and `dao.delete(datasetKey, attempt)` removes the `dataset_import` row again.
Since `da0db59f9` the job reports `step="unchanged"` from `onFinish`, because otherwise nothing at all
would record that we looked.

Measured against the live APIs on 2026-08-26:

| | prod | dev |
|---|---:|---:|
| IMPORT lane job rows | 932,787 | 329,760 |
| of those finished | 616,181 | 278,915 |

The last 500 finished import jobs on dev span ~31h, i.e. **~390/day**. Of those 500:

| step | count | what it is |
|---|---:|---|
| `downloading` | 374 | unchanged, pre-`da0db59f9` rendering |
| `unchanged` | 93 | unchanged, current rendering |
| `null` | 23 | a real import (the step is cleared on success) |
| `finished` | 10 | a real import, legacy rendering |

**93% of finished imports are no-ops.** `ContinuousImporter` walks the whole external fleet on a 7-day
`defaultFrequency`, so this is the steady state and not a burst. The 33 real imports are unfindable.

## Goals

1. Make it possible to see only the imports that did something.
2. Stop the no-ops accumulating for the default 90 days.
3. Keep the record that the scheduler looked at a source and when.
4. Change nothing about what the API returns by default.

## Decisions

### The row is kept

It is the only evidence a source was checked, which is how a feed that has been dead for months gets
noticed. `da0db59f9` restored that signal deliberately after `ImportState.UNCHANGED` was dropped.
Deleting the row instead would also orphan the gz log `JobAppender` had already written, and would have
to fight `BackgroundJob`'s lifecycle, which calls `persist()` *after* `onFinish`. No job in the codebase
declines to record itself; the established pattern is a terminal `step`, which is what this already does.

### The discriminator is the missing metrics row, not the step

`job.step` is documented as free text, has no index, and has rendered this one outcome three different
ways over time. Matching on it would have missed 374 of the 500 rows sampled above, and would break
again the next time the wording changes.

An unchanged import deletes its own `dataset_import` row, so the property that actually holds is:

```sql
j.lane = 'IMPORT' AND j.status = 'FINISHED'
AND NOT EXISTS (SELECT 1 FROM dataset_import di WHERE di.job_key = j.key)
```

Only `ImportJob` uses the IMPORT lane, and `dataset_import(job_key)` is indexed. Failed and cancelled
imports finalize through `updateImportFailure`/`updateImportCancelled` and so keep their metrics row;
among *finished* import jobs, no metrics row means unchanged and nothing else. This is the same semi-join
shape as the existing `format` filter and as `deleteOld`'s three guards, both of which deliberately key
off the satellite tables rather than a class-name list "which would drift as soon as a new job type
starts writing metrics".

Exposed as `JobSearchRequest.unchanged` (`?unchanged=true|false`), unset by default.

### The API default is unchanged; the client opts out

`/job/search` keeps returning everything. `dbschema.sql` already states this intent for the lane filter
— *"sector syncs alone are ~88% of the rows... the lane filter is the UIs default"* — and the
checklistbank Jobs presets already send `lane` for exactly this reason. No endpoint in this API silently
drops rows and this one should not start.

### Retention needed no change at all, only a faster sweep

`deleteOld` only removes job rows that no metrics row refers to, so a `retentionDaysByClass` entry for
`ImportJob` reaches the no-ops and **cannot** reach a real import, which its metrics row protects. The
deploy configs already carry exactly that — dev `retentionDays: 30` with `ImportJob: 7`, prod
`retentionDays: 180` with `ImportJob: 30` — so nothing needed setting. (The `webservice/config-*.yaml`
in this repo are gitignored local copies and are not what any environment runs.)

The reason it was not biting is the sweep interval: a 7-day retention swept every 30 days is nearly
inert, leaving up to ~37 days of no-ops in the history. `JobCleanup` therefore moved from monthly to
**daily**. Its old comment — *"the table grows slowly enough that a daily pass would find nothing to do
most days"* — is precisely what stops being true once the scheduler is on.

One edge worth knowing: an `ImportJob` that fails before `DatasetImportDao.createWaiting` has no metrics
row either, so its record is reaped on the `ImportJob` age rather than the default. Rare, and accepted.

### Job logs are deleted with the row

`JobCleanup` deleted rows only, so every job ever run left two files behind forever:

- `logDir/job-<key>.log.gz`, written live by `JobAppender.newAppender`
- `downloadDir/<xx>/<key>.log.gz`, the copy `JobAppender.copyToDownloads` makes when the job ends, and
  what `JobResource` 302-redirects to

Both are addressed by the job key alone, so the moment the row goes they are unreachable. At ~390
imports/day that is ~140k unreachable files a year per environment — the same inode problem the sector
names files had.

`JobMapper.deleteOld` now returns the reaped keys instead of a count (`DELETE ... RETURNING j.key` inside
a `<select>`, as `NameUsageMapper.deleteSubtree` already does), and `JobCleanup` deletes both files for
each. One statement, so there is never a window where a row is gone but its key unknown. Per-file
try/catch counting deleted vs failed, mirroring `SectorImportRetentionJob.deleteFile`, so one bad file
never aborts a sweep that has already deleted rows. This applies to every job class the cleanup removes,
not just imports.

## Outcome

Shipped as described. Verified on dev that `unchanged=true` and `unchanged=false` partition the IMPORT
lane exactly, with the legacy `downloading` rows landing on the unchanged side — the point of not
matching on the step.

Deliberately left out:

- **The checklistbank UI change.** `src/pages/Jobs/presets.js` needs `unchanged: false` on the `all` and
  `imports` presets, plus `"unchanged"` added to the key list in `presetOf` or the segmented control
  stops highlighting. Separate repo, separate PR; without it nothing changes for the user.
- **Result archives of reaped jobs.** `result_deleted` is only ever set through `DatasetExportMapper`, so
  exports are covered, but `AbstractMatchingJob` and `TaxonomicAlignJob` also produce a download zip and
  have no satellite table protecting their row. Reaping one leaves `downloadDir/<xx>/<key>.zip` behind.
  Same fix shape as the logs.
- **`ImportJob.fireCallback()`** still POSTs a `DatasetImport` for an attempt whose row was deleted on the
  unchanged path. Pre-existing and harmless to the history, but wrong.

See also [2026-08-22-sector-sync-retention.md](2026-08-22-sector-sync-retention.md), the sibling of this
on the sync side.
