# Record the names-index build time with each matcher, so nidx swaps invalidate stale matcher stores

Date: 2026-07-02

## Problem

Per-dataset usage matcher stores (`UsageMatcherChronicleStore`, on disk under `matching.storageDir`)
hold materialized copies of names-index (nidx) ids: each `SimpleNameCached.namesIndexId` /
`canonicalId`, and the `byCanonNidx` inverted index that all matching keys on. Those ids are only
valid for the names index they were built against.

A full names-index rebuild (`NamesIndexCmd`, the `nidx` command) reassigns every `names_index.id`
from scratch and rewrites `name_match`. Once the new index is promoted live, every matcher store
built against the old ids is stale and will silently return wrong/empty matches.

Today `UsageMatcherFactory.needsRebuild()` only compares the dataset **import `attempt`** (read from
the `<key>.json` sidecar) against the DB. A nidx rebuild does not change any dataset's attempt, so
neither the startup `reconcile(false, …)` nor `--rebuild-stale` detects the swap. The only recovery
is a manual force rebuild of every matcher, and nothing tells the operator that is required.

## Goal

1. Persist, alongside each matcher store, the `created` timestamp of the names index the matcher was
   built against, so we can tell which stores are out of date after a nidx swap.
2. Make `needsRebuild()` treat a matcher whose recorded nidx-created differs from the live
   `NameIndex.created()` as stale, so `reconcile` (startup and forced) rebuilds it automatically.
3. Document in `NamesIndexCmd` that promoting a new index requires the matcher stores to be rebuilt,
   and how that now happens.

## Key facts (verified in code)

- `NameIndex.created()` returns a `LocalDateTime` — "DateTime the store was first created or entirely
  cleared" (`NameIndexStore.created()`). Backed by Chronicle (`NameIndexChronicleStore`) and MapDB
  (`NameIndexMapDBStore`) stores. It changes on every rebuild/reset. This is exactly the version
  marker we need.
- The existing sidecar `MatchingConfig.datasetJson(key)` = `<key>.json` is a **COLDP Dataset JSON**
  (written by `DatasetJsonWriter`, read by `ColdpMetadataParser.readJSON`). It is also consumed by the
  standalone matching server (`WsMatchingServer.readDataset`, `MatchingServerBuildCmd`). It must NOT
  be restructured — the nidx timestamp needs its own home.
- `writeSidecar()` runs inside `swapIn()` after the store files are moved into place; `evictLocked()`
  deletes the store dir and the `<key>.json` sidecar. Both are the natural places to also
  write/delete the new nidx marker.
- `UsageMatcherFactory` already holds the live `NameIndex` (`this.nameIndex`), so `created()` is
  available at build time and in `needsRebuild()`.

## Design

### Storage: extra property in the existing `<key>.json` sidecar

No second file. The names-index `created` timestamp is written into the existing dataset sidecar
`<key>.json` as one extra top-level JSON property `nidxCreated` (ISO-8601 `LocalDateTime` string).
This is safe because both readers of that file — `ColdpMetadataParser.readJSON` and
`WsMatchingServer.readDataset` — deserialize it with `ApiModule.MAPPER`, which has
`FAIL_ON_UNKNOWN_PROPERTIES` disabled, so the extra property is ignored when the file is read back as
a plain `Dataset`.

### Write

In `writeSidecar(datasetKey, store)` (already called from `swapIn` after the swap), serialize the
`Dataset` to an `ObjectNode` via `ApiModule.MAPPER.valueToTree(d)`, add the `nidxCreated` property
from `nameIndex.created()`, and write the node to `datasetJson(datasetKey)` (replacing the plain
`DatasetJsonWriter.write`). Best-effort (log-and-continue) as before; logs the recorded nidx-created
alongside the attempt.

### Read + compare

Add `readStoredNidxCreated(datasetKey)` that reads the `nidxCreated` property from the sidecar JSON
tree and returns the parsed `LocalDateTime`, or `null` if the file/property is absent or unparseable.

Extend `needsRebuild(datasetKey)`:

```
existing behaviour: no store dir OR stored attempt != current attempt  -> rebuild
new:                stored nidxCreated is present AND != nameIndex.created() -> rebuild
```

Legacy handling (safe rollout): a **missing** `nidxCreated` property (matcher built before this
change) does NOT by itself mark the matcher stale — we fall back to the attempt-only check. This
avoids a rebuild storm on first deploy. From the first rebuild onward the marker is written, and
subsequent nidx swaps are detected. (A present-but-different timestamp always marks stale.)

### Cleanup

No new file to clean up — the timestamp lives in the existing `<key>.json`, already deleted by
`evictLocked`.

### Observability

Extend `UsageMatcherFactory.MatcherMetadata` with a `nidxCreated` field (nullable), populated in
`metadata(datasetKey)` from `readStoredNidxCreated`. Surfaced via
`MatcherManagementResource GET /matcher/{key}`. Purely additive; lets an operator see which matchers
are out of date without a rebuild.

### Documentation in NamesIndexCmd

Update the class Javadoc (the numbered rebuild steps) and the final completion log line
(`"Names index rebuild completed. Please put the new index (postgres & file) live manually"`) to
state that after promoting the new index:

- every persistent matcher store is now stale (its nidx ids reference the old index), and
- rebuilding happens either automatically on the next `UsageMatcherFactory.reconcile` (server
  startup after the swap, which now detects the nidx-created change), or immediately without a
  restart via `MatcherCmd --rebuild-all` / `POST /matcher/rebuild?force=true`; and
- the correct order is: promote nidx → refresh `name_match` (`DatasetMatcher`/`RematchJob`) →
  rebuild matcher stores (the store build trusts persisted `name_match`).

## Out of scope

- No change to how the nidx itself is built/promoted.
- No change to `name_match` rematching (`DatasetMatcher`/`RematchJob`).
- Bundle/standalone matching server build flow unchanged (it builds nidx + matcher together, so the
  marker is consistent by construction; writing it there is optional and not required for this goal).

## Testing

- Unit test on `UsageMatcherFactory.needsRebuild()` (or a small extracted helper) covering:
  attempt match + nidx match → not stale; attempt match + nidx differs → stale; missing nidx marker →
  not stale (falls back to attempt).
- `MatchingConfig.nidxJson` path shape.
- Round-trip write/read of the timestamp file.
