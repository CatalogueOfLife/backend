# Guards against a half-synced sector reaching a release

Date: 2026-09-09
Status: implemented

## The incident

The COL26.9 draft base release (dataset 316263) held ~18.7k fewer synonyms than the published COL26.8
(316115), and the per-source metrics page showed the decrease nowhere.

The whole delta was one sector. Sector 64441 (ITIS `Apoidea` superfamily attached under `Aculeata`,
ATTACH mode, project 3) held 42,000 usages in project 3 instead of 67,712. Its sync attempt 13, started
2026-09-01T17:00:31, failed with
`UnavailableException: The NameIndexChronicleStore is currently not available`.

The arithmetic closed exactly: -21,420 synonyms from 64441 against +2,680 from every other source
= -18,740. Usages: -25,712 / +6,849 / -39 (the sector-less management classification) = -18,902.
`Lasioglossum` went from 1,844 children to 44, `Megachile` from 2,881 to 1,531, `Apis` from 530 to 360 -
a tree copy stopped partway, not a taxonomic revision.

### How the data was lost

A blue-green redeploy, not a names-index swap:

```
17:00:31  ~43 ITIS sector syncs queued; 64441 starts running
18:30:08  last committed batch of 64441 (usage #42,000 - 42 batches of 1000)
18:32:39  all 42 still-queued syncs cancelled at the same instant   <- JobExecutor.stop()
          64441 (running) fails on the stopped names index
18:33:32  ReconcileJob                                              <- new app starting
```

`ManagedService.stopAll()` reverses the `Component` enum, so it stopped `JobExecutor` and then
`NamesIndex`. `JobExecutor.stop()` calls `ExecutorUtils.shutdownNow(exec, MILLIS_TO_DIE = 12_000ms)`,
which grants running jobs twelve seconds to notice their interrupt and returns `false` if they do not.
That `false` was discarded and `stopAll()` stopped `NamesIndex` anyway. A sector sync in the middle of
`processTree()` does not unwind in twelve seconds; it then hit `assertOnline()` on the dead store.

`deploy/nidx-swap.sh` had already been hardened for exactly this - pause, await quiesced, then stop -
but `deploy/redeploy.sh` called `stopAllComponents $PREVPORT` directly.

The partial write is guaranteed rather than incidental. `SectorSync.deleteOld()` runs on an autocommit
session, `TreeBaseHandler` commits every 1000 usages (`:258-263`) and `TreeBaseHandler.close()` commits
both sessions on the exception path too (`:821-826`). Nothing rolls a sync back.

### How it stayed invisible

Three independent layers, each individually defensible:

1. `SectorRunnable` called `SectorMapper.updateLastSync` only on the success path, so
   `sector.sync_attempt` stayed on attempt 12. `DatasetSourceDao.addReleaseMetrics` resolves a release's
   source metrics purely through `s.getSyncAttempt()`, so ITIS reported attempt 12's 322,234 usages in
   *both* releases - byte identical - while the release actually held 296,522.
2. The failed attempt's `sector_import` row was created up front and closed in `SectorRunnable`'s
   `finally`, but with NULL counts: `doMetrics()` is never reached on a failure.
3. `SectorImportRetentionJob` ran on project 3 at 2026-09-05T21:33:42 and deleted that row - nothing
   pinned it and it predated the Sep 4 release. The sync history endpoint lists
   `FROM sector_import LEFT JOIN job`, so the failed attempt vanished from
   `/project/3/sector/sync?sectorKey=64441` entirely. Only the `job` row survived.

And nothing re-ran it. `JobExecutor.start()` builds its stale list from `JobMapper.cancelStale`
(`WHERE status IN ('WAITING','BLOCKED','RUNNING')`) and hands it to registered handlers; the only
handler was `ImportManager`'s, which skips everything that is not an `ImportJob`. 64441 had already
recorded itself FAILED before the JVM went down, and its 42 queued siblings had been written CANCELED,
so none of them were stale to begin with.

Rarity: of 228 failed `SectorSync` jobs on project 3 since 2026-01-01, 226 failed before doing any work
("already queued or running", "SyncManager is currently not available") and one at `preparing`. Only
64441 died after `deleteOld()`.

## Goals

- A sync that destroys a sector's content and then fails must not leave metrics claiming otherwise.
- Such a sector must repair itself without anyone noticing the damage first.
- Failing that, it must not reach a release silently.
- The evidence must survive long enough to be found.

## Non-goals

- **A transactional sector sync.** Wrapping `deleteOld()` and `processTree()` in one transaction was
  rejected: the copy reads its own writes (`TreeCopyHandler.findExisting` commits the batch session
  precisely so recent inserts are visible), it runs for hours on the large sectors, and a single
  transaction of that size would hold locks and bloat across a whole ITIS sync. The design accepts that
  a failed sync leaves partial data and makes that state loud instead.
- **A live count comparison at release time.** Recounting every sector against its metrics would catch
  more than an attempt check does - including damage from causes we have not seen - but 3,000 sectors x
  three counts is a real cost on every release, and the primitives
  (`SectorImportMapper.countTaxon`/`countSynonym`/`countBareName`) are today reachable only from a
  successful sync. Deferred; the attempt check covers the failure mode we actually have.
- **A hard release block.** The refusal is overridable. A curator who has looked at the sector and
  decided its current content is acceptable must not be forced to re-run a multi-hour sync first.
- **Blocking a deploy on a running job.** `stopAll()` waits, but bounded. A deploy that cannot proceed
  is worse than a sync that has to be re-run, and the downstream guards make the re-run automatic.

## What was implemented

### 1. A failed sync pins its own attempt

`SectorRunnable` gained `markDataDestroyed()` and `pinFailedAttempt()`. `SectorSync.doWork()` and
`HierarchySync.doWork()` - both delete the sectors previous content before inserting the new one - call
`markDataDestroyed()` immediately after their `deleteOld()`; if the job then throws, `sector.sync_attempt`
is moved onto the failed attempt rather than left on the last successful one.

The consequence is deliberate: source metrics now resolve to a row with no counts, so the sector reports
nothing rather than something false. That over-reports the damage - the sector does hold 42,000 usages -
but "unknown" is the honest answer for a tree nobody measured, and it makes the drop visible on the very
page an operator looks at.

A sync that fails *before* `deleteOld()` does not call `markDataDestroyed()` and so changes nothing.
That distinction is what keeps the guard quiet: it is the difference between the two failures in 2026
that mattered and the 226 that did not.

`SectorDelete`/`SectorDeleteFull` deliberately keep `updateSectorAttemptOnSuccess = false` and are not
covered - an emptied sector is already visible through `SectorSearchRequest.withoutData`, and their
metrics are kept on purpose (see issue #986).

### 2. Retention keeps the newest attempt of every sector

`SectorImportRetentionJob` now adds `SectorImportMapper.listNewestAttempts` to its pinned set. The newest
attempt of a sector is kept whether or not the project or a release pins it.

The failed attempt is exactly the row nobody pins, and it is the only durable record that a sector is half
synced. Reaping it left a sync history of nothing but successful attempts over a project that had lost
25,712 usages.

### 3. `SyncScheduler` repairs half-synced sectors

`SectorMapper.listUnfinishedSyncs(projectKey)` returns the sectors whose `sync_attempt` points at a job
that did not finish. `SyncScheduler.fetch()` now unions it with `listOutdatedSectors`, deduplicated by
sector id.

`listOutdatedSectors` alone was never going to help: it asks whether the *source* published a newer
import. ITIS had not re-imported since, so sector 64441 sat broken for eight days.

The union is deliberately not narrowed by `SYNC_SCHEDULER_SOURCES`. That allowlist governs routine
re-syncing; these sectors are damaged and also block every release until repaired, so honouring it would
leave a project permanently unreleasable with nothing able to fix it.

### 4. Interrupted syncs are resubmitted

`SyncManager` now registers `jobExecutor.onStaleJobs(this::rescheduleInterrupted)`, resubmitting the
`SectorSync` jobs a previous server run left queued or running - the hook `ImportManager` has had for
imports all along.

Only `SectorSync` is resubmitted. Re-running a `SectorDelete` the operator may since have reconsidered is
destructive in a way re-running a sync is not, and a half-deleted sector is caught by (3) instead.

It is uncapped on purpose. A cap could only silently drop syncs, which is the failure this hook exists to
fix; the executor serialises them per project through `getSerialBy()`, so a large batch costs time rather
than load.

### 5. `stopAll()` quiesces the executor first

`ManagedService.stopAll()` now pauses the job executor and waits `DEFAULT_QUIESCE_SECONDS` (120) for
running jobs to finish before it stops any component. Pausing also stops queued jobs from starting into a
shutdown; `stop()` still discards them as CANCELED, and (4) resubmits the syncs among them.

Fixing it here rather than in `deploy/redeploy.sh` covers every caller - the deploy script, the admin UI
and a manual curl. If the executor does not quiesce in time the components go down anyway, naming the
still-running jobs at ERROR level.

### 6. A release refuses to build on a half-synced sector

`ProjectCopyFactory.assertNoUnfinishedSyncs(factory, projectKey)` throws `IllegalArgumentException`
listing the offending sectors. `buildRelease` and `buildExtendedRelease` call it unless `force`, which
`POST /dataset/{key}/release?force=true` and `POST /dataset/{key}/xrelease?force=true` set.

Checking in the factory rather than inside `AbstractProjectCopy` means the caller gets a 400 on the POST
instead of a job that fails later, without threading a flag through four constructors.

## Outcome

Assumptions that needed checking during implementation:

- **`listUnfinishedSyncs` needs the `job` row, not just `sector_import`.** `sector_import` has no status
  column of its own; status, step and error come from a join on `job.key = sector_import.job_key`. A
  `SectorRunnable.run()` outside the executor writes no job row (no `JobDao` persister), so the query
  finds nothing there. In production `JobExecutor.submit` always creates the row, so this only shapes the
  tests: the mapper behaviour is covered in `SectorMapperTest#listUnfinishedSyncs`, which builds a real
  job row, while `SectorSyncFailureIT` covers the pinning.
- **The existing retention fixture was unaffected by (2).** In `SectorImportRetentionJobIT` the newest
  attempt of the seeded sector (6) already postdated the release cutoff and was kept, so
  `exactRetentionOutcome`, `filesMatchRows`, `dryRunChangesNothing` and `idempotent` still see exactly
  attempt 3 deleted. A second sector had to be seeded to exercise the new keep at all.

Left out deliberately: nothing in the plan was dropped, but see Non-goals for the two stronger variants
(transactional sync, live count comparison) that were considered and rejected rather than deferred by
accident.

Cross-links: [`2026-09-01-job-component-consolidation.md`](2026-09-01-job-component-consolidation.md),
whose "nothing cascades" design this hardens, and
[`2026-09-04-per-environment-components.md`](2026-09-04-per-environment-components.md).
