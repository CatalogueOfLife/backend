# Self-maintaining UsageMatcherFactory

**Date:** 2026-06-29
**Status:** Approved (design)
**Component:** `core/src/main/java/life/catalogue/matching/UsageMatcherFactory.java`

## Goal

Simplify `UsageMatcherFactory` so that the set of persistent usage matchers maintains
itself against a clear invariant, instead of being managed through a large surface of
imperative build/prepare/rebuild/cleanup/reload methods.

> For every published, non-deleted dataset there is an existing, up-to-date matcher.
> It is created on publishing and kept up to date after each successful import.
> Most management methods go away; a simple "rebuild" lever (all matchers, or one) stays
> for use when something goes wrong.

## Invariant

A **persistent chronicle matcher file** exists on disk and is in sync with the dataset's
current import attempt **iff** the dataset is:

- origin `EXTERNAL`, `RELEASE`, or `XRELEASE`, **and**
- published (`privat == false`), **and**
- not deleted, **and**
- of usage count `>= cfg.pgMatcherThreshold`.

Datasets that are published but **below** the threshold, and **`PROJECT`** datasets, get
no persistent file — they are served on demand by a Postgres-backed matcher reading live
data, which is therefore always current. This preserves today's `pgMatcherThreshold`
optimization.

The in-memory `matchers` map is a **lazily-populated cache** of open matcher instances,
**not** an eager registry of every published dataset. An entry is created the first time a
matcher is actually used, and reused (shared) by concurrent callers thereafter.

## Behavior

### Event-driven maintenance (existing `DatasetListener`)

The factory already subscribes to the `EventBroker` (`WsServer:520`). The listener
callbacks become the single place that maintains the invariant:

| Event | Condition | Action |
|---|---|---|
| `datasetChanged` UPDATE | `old.privat && !new.privat` (publish) | async (re)build persistent matcher, if external/release & `>= threshold` |
| `datasetChanged` UPDATE | `!old.privat && new.privat` (unpublish) | remove matcher file + sidecar |
| `datasetChanged` DELETE | — | remove matcher (unchanged) |
| `datasetDataChanged` | published external/release & `>= threshold` | async rebuild to pick up the new import attempt; **creates if missing** |

`datasetDataChanged` is fired on successful import at `ImportJob:440`. Today the handler
returns early when no matcher exists; under the new model it builds one if the dataset is
published and above threshold.

Release matchers are created by the same publish path, so the `prepare()` call is removed
from `PublishReleaseListener`.

### Startup reconcile

A lifecycle **start** hook (not the constructor — the DB and `JobExecutor` must be ready)
runs an async reconcile:

1. List published, non-deleted `EXTERNAL`/`RELEASE`/`XRELEASE` datasets.
2. For each `>= threshold`, compare the **sidecar attempt** (`cfg.datasetJson`) against the
   current DB attempt.
3. If the file is missing or the attempt is stale, async-build the matcher.

Reconcile never opens stores; it only reads sidecar JSON and queries the DB. This single
routine replaces today's eager `loadFromFS` open-all, `build(DatasetSearchRequest)`,
`cleanup()`, and `reload()`.

### Lazy, shared cache

`get`, `existingOrPostgres`, and `persistent` open the chronicle store from disk on first
access (under the existing `buildLocks`), cache the instance in `matchers`, and return the
**same shared instance** to concurrent callers. Chronicle map supports concurrent reads, so
no per-request reopen and no file-lock dance are needed (the mapdb exclusive-lock constraint
is gone — see below).

`get(int)` changes from "return cached or null" to "open on demand and cache". Callers in
`ImportJob:185` and `PgImport:412` (cross-dataset identifier matching) thus get a working
matcher even when one was not preloaded — strictly an improvement.

### Drop MapDB

Chronicle is the only supported store. Remove `UsageMatcherMapDBStore`, the `DBMaker`
machinery, all `cfg.chronicle` conditionals, and the `cfg.chronicle` config flag if it is
no longer referenced. `reopenStore`, `buildPersistentMatcher`, and `listFS` collapse to
their chronicle-only forms.

## Public API changes

**Removed:** `build(int)`, `build(DatasetSearchRequest)`, `prepare(int,int)`,
`rebuild(DatasetSearchRequest,int)`, `rebuildExisting(int)`, `cleanup()`, `reload()`,
`removeAll()`, `remove(DatasetSearchRequest)`, `exists(int)`, `isSmallDataset(int)`
(folded into the threshold check), and — if no longer used by the CLI —
`metadata(boolean)` / `FactoryMetadata`.

**Kept:** `get(int)`, `existingOrPostgres(int)`, `postgres(int)`, `postgres(int,SqlSession)`,
`persistent(int)`, `memory(int)`, static `buildPersistentMatcher(...)`, `getNameIndex()`,
`remove(int)`, `metadata(int)`.

**Added:**
- `reconcile(boolean force, int userKey)` — startup (force=false: only missing/stale) and
  rebuild-all (force=true: remove + rebuild every in-scope matcher).
- `rebuild(int datasetKey, int userKey)` — rebuild a single matcher (remove file, async build).

## REST surface (`MatcherManagementResource`)

Shrinks to three endpoints:

| Method | Endpoint | Factory call |
|---|---|---|
| `GET` | `/matcher/{key}` | `metadata(int)` — inspect one |
| `PUT` | `/matcher/rebuild` | `reconcile(force=true, user)` — rebuild all |
| `POST` | `/matcher/{key}` | `rebuild(key, user)` — rebuild one |

Removed: list-all `GET /matcher`, `POST /matcher` (build req), both `DELETE`s,
`POST /matcher/reload`, and the `PUT /matcher/rebuild` request-body variant.

## Other call sites to update

- **`PublishReleaseListener`** (`core/release/`): remove the `prepare()` call; release
  matchers are created by the publish event path.
- **`ImportJob:185` / `PgImport:412`**: `get(int)` now opens on demand — confirm both
  tolerate a freshly opened matcher (expected: yes).
- **CLI `MatcherCmd` / `MatchingServerBuildCmd` / `WsMatchingServer`**: repoint to the
  surviving API. The static `buildPersistentMatcher(...)` stays for the matching-server build.

## Verification items (resolve during implementation)

1. Publishing a **release** actually fires the `privat` `true→false` `DatasetChanged` event.
   If not, keep `PublishReleaseListener` calling the new `rebuild(key, user)` instead.
2. The chronicle store tolerates concurrent reads on a single shared instance.
3. What `MatcherCmd` requires, so the CLI is not stranded by dropping `metadata(boolean)`.

## Testing

- **Publish** event creates a matcher for an in-scope dataset; below-threshold and `PROJECT`
  datasets do not get a persistent file.
- **Import** (`datasetDataChanged`) refreshes an existing matcher and creates a missing one.
- **Unpublish** and **delete** remove the matcher + sidecar.
- **Reconcile** builds only missing/stale matchers (force=false) and all (force=true).
- **Lazy `get`** opens on demand; concurrent `get` returns the same cached instance.
- Existing matching/reconciliation/sync paths still resolve a matcher.

## Non-goals

- Idle eviction / LRU of open matchers (YAGNI for now — chronicle mmap cost is modest).
- Persistent matchers for `PROJECT` datasets (they stay Postgres-live).
- Continued MapDB support.
