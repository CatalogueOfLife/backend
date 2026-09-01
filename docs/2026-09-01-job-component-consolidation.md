# Make the startable components mean something, and let the job executor drain

Date: 2026-09-01
Status: Shipped. Backend side complete; the deploy repo still has to stop naming the removed
components and start pausing the job executor (see [Outstanding](#outstanding)).

## Problem

After the move to unified background jobs, `JobExecutor`, `ImportManager` (`DatasetImporter`) and
`SyncManager` (`SectorSynchronizer`) were still three separate startable `Component`s, and
`UsageMatcher` a fourth. The question was whether that separation still earned its keep, or whether
pausing the job executor would be enough for maintenance.

It did not, and the separation was actively misleading:

- **Stopping `DatasetImporter` or `SectorSynchronizer` stopped no work.** Both `stop()` methods said
  so in their own comments: the running and queued jobs live in the shared executor. They were
  submit gates duplicating the executor's own gate, so an operator who stopped them to quiesce the
  server got a false sense of having done it.
- **`UsageMatcher` was not even a gate.** There is no `assertOnline()` on the matcher factory
  anywhere, and `hasStarted()` was read only by the `/admin/component` state map and one unit test.
  Matching, sector sync, XRelease and every `/matcher` endpoint worked with it stopped.
- **`nidx-swap.sh` therefore raced.** It stops `NamesIndex DatasetImporter SectorSynchronizer` and
  then runs `DROP TABLE public.names_index`. It never stops the `JobExecutor`, so in-flight imports
  and syncs kept running, and releases, XReleases, `MatchingJob` and the matcher reconcile were
  never stopped at all.

Three different things had been conflated under one mechanism: exclusive ownership of a file across
two JVMs during a blue-green deploy, silencing work the app starts by itself, and gating submissions.
Only the first two need a component.

## Goals

1. Every remaining component earns its place under one stated rule.
2. Quiescing the server for a resource swap becomes possible and waitable.
3. Fix the lifecycle bugs found on the way.

## Non-goals

- **A true read-only window.** The CRUD API writes on the request thread and never touches a job, so
  no amount of work on the executor gates it. That needs an HTTP-layer filter and is a separate
  concern. `Maintenance` (`/admin/maintenance`) remains what it is: a JSON status file for a UI
  banner that blocks nothing.
- **Enforcing one writer per matcher `storageDir`.** Still deployment discipline. A running
  `MatcherBuildJob` lives in the executor, so no component stop was ever going to enforce it.

## Decision

**`start-all`/`stop-all` already is the binary read/write switch.** What was missing is that the
component list contained things that gate nothing, that `stop` did not stop work, and that nothing
waited for a drain. A component now earns its place by being one of three things:

| Kind | Components | Why |
|---|---|---|
| Exclusive resource | `NamesIndex` | chronicle map opened read-write, handed over between JVMs |
| Work engine | `JobExecutor` | the drain point |
| Autonomous writer | `CronExecutor`, `DoiUpdater`, `ImportScheduler`, `SyncScheduler`, `GBIFRegistrySync`, `Feedback` | acts without an HTTP request behind it |

With those stopped the app does nothing of its own accord. `DatasetImporter`, `SectorSynchronizer`
and `UsageMatcher` are gone, kept only as deprecated no-op values so the deploy scripts survive the
release.

**Two verbs on the executor, because blue-green and maintenance want different things.** `stop()`
rejects submissions, interrupts what runs and discards the queue. `pause()` leaves the running jobs
to finish, starts nothing new, keeps the queue and still accepts submissions, so a maintenance
window costs no user request. `POST /admin/jobs/pause?await=<seconds>` blocks until nothing is
running and answers 409 if a job outlives the deadline.

### Rejected: a job → component dependency graph

A declarative `Set<Component> requires()` on `BackgroundJob`, with jobs parked while a required
component is offline. Its only payoff was keeping exports running during a nidx swap — an operation
that is `ANALYZE`, a metadata-only `ALTER TABLE ... SET SCHEMA` and a `DROP TABLE`, so minutes.
Against that: declarations on ~10 job classes, an offline set, a held map, park/release semantics, a
`ComponentListener`, and moving the `Component` enum down into `api` purely so `BackgroundJob` could
name it. A review of the mechanism found three races and a deadlock in the park/release alone.
Pausing the whole executor for the window is the better trade.

### Rejected: collapsing to a single binary switch

Removing the per-component endpoints entirely. The granularity is already paid for, `NamesIndex`
genuinely needs individual addressing so a swap can release the file while the server keeps serving
reads, and `start-dev-components.sh` uses a deliberate subset (no DOIs, no cron, no GitHub issues,
no continuous import) — though that one is config-shaped and belongs in the dev `config.yml`.

### Rejected pause mechanisms

- **Refuse and resubmit** via the existing `DatasetBlockedException` path: hot-spins, writing job
  table transitions every cycle.
- **Throw from `beforeExecute`** to skip a task: per the JDK contract `afterExecute` is then not
  called and the worker thread dies, leaking the `futures` entry and the serial key.

A **wait in `beforeExecute`** was rejected for the `requires()` variant — blocking a worker deadlocks
a lane when the job at its head is held and unrelated jobs behind it should still run — but that
objection does not apply to a global pause, where nothing behind it should run. It is what shipped.

## Outcome

Implemented in five commits on `feat/job-components`. Deviations from the plan worth recording:

- **The pause is a `beforeExecute` wait, not a held map.** The plan carried the held map over from
  the `requires()` design. Once holding became global, the map, the park/unpark and the surgery on
  the serial gate — where the review had found its races — were all unnecessary.
- **`isQuiesced()` is new next to `isIdle()`, rather than `isIdle()` being redefined.** The plan had
  the operator wait on `isIdle()`, but that also requires an empty queue and so would never go true
  while a paused executor deliberately holds work. `isQuiesced()` asks only whether a job is
  executing. It reads the job statuses rather than `ThreadPoolExecutor.getActiveCount()`, because a
  worker parked on the pause gate counts as active while its job has not begun.
- **Queue accounting moved off the queues and onto the job statuses.** The pause gate exposed a
  pre-existing gap: a task already taken from its `PriorityBlockingQueue` but not yet started was in
  neither the queue nor the running set, so `getQueue()`, `queueSize()`, the duplicate check and
  `ImportManager.importJob()` could not see it. Reading `futures` by status covers a task wherever
  it sits — a lane queue, the serial gate or the pause gate.
- **Both schedulers share `AbstractPollingScheduler`, but keep their own polling policy.** Only the
  thread, the lifecycle and the never-die contract are shared; the differing sleeps and preconditions
  are not flattened.
- **The two schedulers stayed two components.** Their polling is configured independently, so a
  merged `hasStarted()` would report a half-dead scheduler as running.
- **The reported dead `matching.chronicle` config key does not exist.** Only the names index
  `type: chronicle` is present in the deploy configs, and that is still valid. Nothing to remove.
- **Not done: an HTTP-level test of `/admin/component` and `/admin/jobs/*`.** The webservice module
  has no Dropwizard resource-test harness — its resource tests are plain unit tests — so the
  coverage went to `ManagedServiceTest` instead, which had none at all.

### Bugs fixed on the way

1. `JobExecutor` started itself from its constructor, so the globally scoped `cancelStale()`
   (`UPDATE job SET status='CANCELED' WHERE status IN ('WAITING','BLOCKED','RUNNING')`, no server
   predicate) fired during `WsServer.run()` of the app coming up — cancelling the live job rows of
   the old app still running in a blue-green deploy.
2. `WsROServer` never registered the executor with the Dropwizard lifecycle at all. It worked only
   because of that constructor start, and its pools were never shut down.
3. Stopping used the orderly `ExecutorService.shutdown()`, which keeps draining its work queue — so
   quiescing an app *launched* up to `MILLIS_TO_DIE` of newly dequeued jobs per lane, then waited
   that long again for a running job rather than interrupting it.
4. `stop()` left `futures` and the serial gate populated, so after a restart `getQueue()`/`exists()`
   reported jobs that no longer existed while the duplicate check rejected their legitimate
   resubmits.
5. `releaseSerialAndPromote` called `execute()` directly, bypassing every gate the executor has.
   All dispatch now goes through one `dispatch()` method.
6. Both polling schedulers ended their own thread on any `Exception`. Their inner handlers caught
   only `IllegalArgumentException`, so the two failures they actually meet — an
   `UnavailableException` from a stopped executor and a `TooManyRequestsException` from a full queue
   — killed scheduling until an operator restarted the component.
7. `SectorRunnable` created its `sector_import` attempt in the constructor but closed it only in the
   `finally` of `execute()`, so a sync cancelled while queued left the row running forever.
8. A sync that could not be queued because a component was offline burned one of the sector's
   numbered attempts and left a FAILED entry in its history that said nothing about the sector.
9. `UsageMatcherFactory.cleanupTempDirs()` deleted **any** `.building`/`.old` dir, ignoring the pid
   that `MatchingConfig.buildDir` embeds precisely so two processes cannot collide — so starting the
   component in the new JVM deleted the in-progress matcher build of the app still serving, every
   blue-green deploy.
10. `UsageMatcherFactory.close()` closed matcher stores directly, bypassing the reference counting
    added so a shared mmap is only unmapped once its last consumer released it.

## Outstanding

Backend changes ship first, with the deprecated aliases, so every existing script keeps working.
Then, in the deploy repo:

1. `nidx-swap.sh` and `nidx-clear.sh`: pause the job executor with `await`, stop `NamesIndex`, run
   the DDL, start `NamesIndex`, resume. **This is where the swap race is actually closed** — today
   neither script stops the executor at all.
2. `start-dev-components.sh`: drop the removed names, add `SyncScheduler`, and move its deliberate
   subset into the dev `config.yml`.
3. Document that `redeploy.sh nocomponents` now leaves the job executor stopped, so submissions
   answer 503 rather than being accepted. Intended — that is what `nocomponents` should have meant.
4. Grep the `checklistbank` UI for hardcoded component names — `/admin/component` is `@PermitAll`.
5. A later backend release deletes the deprecated aliases.

## Known limits

- Pausing does not interrupt running jobs. An XRelease already running keeps hammering the names
  index via its embedded syncs, hence the wait-for-quiesced contract and the `await` parameter.
- A task a worker has already taken between setting `paused` and reaching the gate escapes the
  pause. Inherent — there is no pause API on `ThreadPoolExecutor` — and benign under wait-for-quiesced.
- Pausing stops *all* jobs, exports included, for the window. That is the accepted trade for not
  building the dependency graph.
- `stopAll()` reverses the enum, so `NamesIndex` stops last, after the executor. Correct, but it
  means a stop-all while a job runs still races the index for as long as the executor takes to
  interrupt it. Pause first.
