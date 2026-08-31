# CLB release-in-a-box — local/offline matching + browse bundle

Date: 2026-06-20

> **Status: shipped.** Implemented on `feat/release-bundle`. See [`BUNDLE.md`](BUNDLE.md) for what
> the code does today; this record is the intent as of 2026-06-20 and is not kept up to date.
> The `## Outcome` section at the end lists where the implementation deviates from this plan.

## Goal

Ship a self-contained Docker bundle for **one COL release** that exposes name matching +
OpenRefine reconciliation **and** most of the read ChecklistBank API (taxon, tree, parser,
metrics), backed by a small bundled Postgres preloaded with just that release. Built monthly, and
for the annual versions. It serves offline R-client / OpenRefine users and can double as a
relocatable matching tier.

## Background

`WsMatchingServer` already ships matching in Docker today, but it is hardwired to a *single*
dataset, runs **DB-free** from a prebuilt matcher store + names index (built by `MatchingServerBuildCmd`), and
exposes only matching. This design generalises that idea into a full read bundle.

### Why bundle Postgres (vs. store-only)

Once warm matchers exist, matching barely touches Postgres, so offloading PG *load* off the
read-write server is a weak motivation. The real value is **local self-containment**: with a
one-release Postgres in the bundle we reuse *every* existing read resource unchanged
(taxon / tree / parser / match / reconcile / metrics) with **no store-only forks**, and async jobs
can persist to the bundle's own DB.

### Job persistence (dependency on the unified-jobs work, now on master)

That work makes `JobExecutor` take an **optional `JobDao`**: with one, jobs persist to a DB
`job` table; the in-memory lane queues are unchanged.

Key constraint learned from it: `JobMapper.cancelStale()` is **global**
(`UPDATE job SET status='CANCELED' WHERE status IN ('WAITING','BLOCKED','RUNNING')`) with no
per-app column on `JobInfo`. So **two persisting executors must never share one `job` table** —
they would cancel each other's in-flight jobs on startup. The bundle sidesteps this entirely by
using its **own** Postgres.

## Decisions

- **Bundle Postgres** with a single release; reuse existing resources, no store-only variants.
- **Own local job store**: the bundle's `JobExecutor` runs **with** a `JobDao` against the bundle's
  Postgres → full job persistence, no shared-`cancelStale` conflict. Bulk matching can also stay
  **streaming** (no executor) where preferred.
- **Include taxon + dataset metrics** for the shipped release (see *Metrics*).
- **Leave read-write matching as-is** for now; revisit server-side offload only if RW RAM/CPU hurts.

### Recommended defaults (open knobs)

- **docker-compose** (separate `postgres` + `app` containers, optional `elastic`) with the release
  **data as a swappable volume/dump**, rather than baking a multi-GB DB into the app image. Keeps
  the app image thin and lets one image serve any release by pointing at a different data volume.
- **Two flavors**: `core` (PG only — match, reconcile, taxon/tree/parser, metrics) as the lean
  default; `full` (+ Elasticsearch) opt-in for search/suggest. Reconcile `suggest` degrades to
  pass-through without ES; match/extend/browse are unaffected.
- **Scope** = the read API subset (`WsROServer.registerReadOnlyResources`) **plus** matching +
  reconciliation + metrics. No import/sync/release/admin-write endpoints.

## Implementation

### 1. New bundle application — `WsBundleServer`

A new Dropwizard `Application` (sibling of `WsMatchingServer`, superseding its single-dataset role)
that wires, against the **local** Postgres:

- `NameIndex` via `NameIndexFactory.build(cfg.namesIndex, sqlFactory, …)` (read-only use).
- `UsageMatcherFactory` (its `loadFromFS()` opens the prebuilt warm store(s) in `storageDir`).
- `JobExecutor` **with** a `JobDao` bound to the bundle DB (post unified-jobs).
- Resources: call the existing `WsROServer.registerReadOnlyResources(...)` to mount the read API,
  then additionally register the matching + reconciliation resources: `NameUsageMatchingResource`,
  `ReconciliationResource` (already in `webservice/.../resources/matching/openrefine/`).

Reuse, don't fork: `registerReadOnlyResources` already constructs the DAOs and mounts
`TaxonResource`, tree, `NameParserResource`, search/suggest, etc. `WsBundleServer` = that helper +
NameIndex + matcherFactory + executor. Model the bootstrap on `WsMatchingServer.run()` and
`WsROServer.run()`.

Config: new `WsBundleServerConfig` (PG `db`, `namesIndex`, `matching`, `job`, optional `es`,
`releaseKey`). The single release key identifies the dataset the bundle serves.

### 2. Transparent single-dataset routing (keyless paths)

Because the bundle serves exactly one dataset, users should not need to know its key:
`/taxon/{id}`, `/nameusage/...`, `/tree`, `/reconcile`, `/dataset` (the single dataset's metadata),
etc. — instead of `/dataset/{key}/...`.

Do this **without forking resources** via a new `@PreMatching` `ContainerRequestFilter`
(`SingleDatasetRewriteFilter`), mirroring the existing `DatasetKeyRewriteFilter` (which already
rewrites the URI with `req.setRequestUri(...)`). Configured with the bundle's fixed `releaseKey`:

- For a request whose **first path segment** is a known dataset-scoped root (allowlist: `taxon`,
  `nameusage`, `name`, `synonym`, `tree`, `reference`, `vernacular`, `metrics`, `match`,
  `reconcile`, `export`, `verbatim`, …) → rewrite to `/dataset/{releaseKey}/<rest>`.
- For bare `^/dataset/?$` → rewrite to `/dataset/{releaseKey}` so it returns the single dataset.
- Leave already-keyed `/dataset/{key}/...` and genuinely global paths (`/parser`, `/vocab`,
  `/version`, openapi, admin) untouched (allowlist + "no rewrite if a numeric key already follows").

All resource classes are reused as-is — they still receive `/dataset/{key}/...`. Register the
**dataset-scoped** `ReconciliationResource` and let the filter supply the key (so
`DefaultReconciliationResource`'s COL-XR cache logic is not needed here). Both filters are
`@PreMatching`; order so the keyless filter runs first (it emits a numeric-key path, after which
the alias filter is a no-op). Derive the allowlist from the actual `@Path("/dataset/{key}/...")`
roots, and audit for resources exposed both globally and dataset-scoped (e.g. `/nameusage`) so the
rewrite targets the intended one.

### 3. Bundle builder — extend `MatchingServerBuildCmd` → `BundleBuildCmd`

A `ConfiguredCommand` that, for a given release key, produces the **data artifact**:

- Trimmed **`pg_dump`** of just that dataset's partitions: name usages, names, references,
  `taxon_metrics`, plus the shared `names_index`/`nidx` tables and supporting lookup tables.
- The prebuilt **memory mapped matcher store** + **Chronicle names-index store** (today's
  `UsageMatcherFactory.buildPersistentMatcher(...)` path, already exercised by
  `MatchingServerBuildCmd`).
- The **metrics** payloads (see below).
- Optional **Elasticsearch snapshot** for the `full` flavor.

Output is assembled into the per-release data volume / tarball consumed by compose.

### 4. Metrics in the bundle

- **Taxon metrics**: include the release dataset's `taxon_metrics` rows in the PG dump (keyed by
  `dataset_key`, already built for releases by `TaxonMetricsCmd`). The existing `TaxonResource`
  metrics endpoint then serves them unchanged — no code change.
- **Dataset metrics**: these are file-based (`FileMetricsDatasetDao` over `metricsRepo`) and
  attributed to the **project's** import attempt, not the release. Capture them at build time as a
  **JSON dump** and drop the files into the bundle's `metricsRepo` under the release key, so the
  existing dataset-metrics/import endpoints serve them without API changes.

## Sequencing

1. ~~**Wait** for the unified-jobs work to merge.~~ Done — it is on master.
2. Rebase on the merged `JobExecutor` / `JobDao` API.
3. Build `WsBundleServer` + `WsBundleServerConfig` + `SingleDatasetRewriteFilter`.
4. Build `BundleBuildCmd` (PG dump + stores + metrics [+ ES snapshot]).
5. Author `docker-compose` (`core` and `full`) + per-release data volume layout.
6. Wire CI to produce a bundle per release, monthly + annual.

## Verification

- **Unit/IT**: `BundleBuildCmd` produces a dump + stores + metrics JSON for a small test release
  (TestContainers Postgres); `WsBundleServer` boots against the restored DB and the NameIndex
  starts.
- **Keyless routing**: `GET /dataset` returns the single shipped dataset; `GET /taxon/{id}`,
  `/tree`, `/reconcile`, `/parser/name?q=...` work without a `/dataset/{key}` prefix, and the keyed
  forms still work too.
- **Local end-to-end** (`docker compose up`, `core` flavor):
  - taxon + tree endpoints return data (PG-backed read API works);
  - taxon metrics endpoint returns metrics; dataset metrics endpoint returns the shipped JSON;
  - `/parser/name?q=...` works;
  - `GET /reconcile` manifest + `POST /reconcile` reconcile a name (matcher warm-loaded from the
    bundled store); `…/reconcile/extend` returns classification columns;
  - add the local `…/reconcile` URL as a standard service in OpenRefine and reconcile a column;
  - a bulk match streams results back with no external services.
- **`full` flavor**: name usage search + suggest return ES-backed results.
- **Offline check**: disconnect networking; all of the above still work against the bundled PG
  (+ ES for `full`).

## Out of scope / deferred

- Server-side relocation of public matching off the RW server (only worthwhile if RW RAM/CPU
  becomes the constraint; a shared-DB matching tier would need streaming-only jobs or per-app
  `cancelStale` scoping — a separate unified-jobs enhancement).
- Write/import/sync/release/admin endpoints in the bundle.


## Outcome

Shipped on `feat/release-bundle`. `docs/BUNDLE.md` is the living reference; what follows is only where
the implementation departed from the plan above and why.

### Elasticsearch is mandatory — the `core`/`full` flavors are gone

The plan offered a lean `core` flavor without Elasticsearch, with reconcile `suggest` degrading to
pass-through. That was dropped: a bundle always ships elastic, `WsBundleServer.esRequired()` returns
true and startup fails without an `es` section. A single flavor removes the "which endpoints work in
this bundle?" question entirely, and search is a large part of what makes a browse bundle useful.

### The ES index is built on first boot, not shipped

The plan listed an "optional Elasticsearch snapshot" in the build artifact. There is no snapshot or
restore support anywhere in the codebase, and adding it would have tied the artifact to an ES major
version. Instead the bundle creates the index if it is missing and, when it holds no documents,
indexes the release out of its own Postgres via the existing `NameUsageIndexService.indexDataset`.
The cost is a slow first start, hidden behind the `bundle-index` health check that
`docker compose up --wait` blocks on.

### `WsROServer` was generified rather than copied

The plan said `WsBundleServer` would be a sibling of `WsMatchingServer` calling
`WsROServer.registerReadOnlyResources`. It turned out `WsROServer` is referenced from only two places,
so it became `WsROServer<C extends WsServerConfig>` with four protected hooks (`esRequired`,
`buildIndexService`, `buildJobExecutor`, `registerAdditional`) and `WsBundleServer extends
WsROServer<WsBundleServerConfig>`. That reuses the http client, the bundles, the DAO wiring and the
health checks too, not just the resource registration, and `WsServer` was left untouched.

### Bulk matching stays streaming, and `/match/nameusage` is not rewritten

The plan had the bundle register the dataset scoped `NameUsageMatchingResource` and persist bulk match
jobs to the bundle's own `job` table. Its job endpoints require `@Auth User` and hand the caller a
`job.downloadURI` pointing at a download server a bundle does not have. The bundle registers the global
`FixedNameUsageMatchingResource` instead — already keyless, no credentials, results streamed straight
back. The plan itself allowed for this ("bulk matching can also stay streaming (no executor) where
preferred"). A `JobExecutor` with a `JobDao` still exists, because `UsageMatcherFactory` needs one and
a matcher rebuild should be visible.

### The names index is built from the bundle database, not DB-free

`MatchingServerBuildCmd` builds its names index with a null `SqlSessionFactory` because the matching
server has no database to disagree with. A bundle ships `names_index` rows, so its store must carry the
same ids: `BundleBuildCmd` builds both stores from the temporary bundle database, after the slice has
been copied into it. The shared part is `MatcherStoreBuilder`, whose one parameter is exactly this
difference.

### The Postgres slice goes through a temporary database

The plan said "trimmed `pg_dump` of just that dataset's partitions". `pg_dump` cannot filter rows and
the partitions are hashed, so there is nothing to trim. `BundleBuildCmd` creates a temporary database,
binary-`COPY`s the release into it, dumps that with `pg_dump -Fc` and drops it. The artifact is then a
plain dump the stock postgres image restores on first boot with none of our code involved.

### Not done

CI: nothing builds a bundle per monthly and annual release yet. That is the remaining piece of
*Sequencing* step 6.
