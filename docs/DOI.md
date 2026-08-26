# DOI support in ChecklistBank

## Overview of DataCite DOIs in ChecklistBank
Every dataset in ChecklistBank has a unique DOI assigned to it which remains the same across its lifetime.
We refer to this as the "concept DOI" as it represents the concept of the dataset, not a specific version.

External datasets additionally have a **version DOI** assigned to every successful import attempt; the concept DOI
always resolves to the latest version while each version DOI points to its specific snapshot.

Projects and releases do not have version DOIs:
- Projects change continuously — there is no meaningful "version".
- Releases are immutable snapshots themselves — the release's concept DOI is already version-specific, so no
  separate version DOI is minted.

Only published datasets (including public releases) have a public, findable DOI. Private datasets still have
a DOI, but it is registered as a draft at DataCite and cannot be resolved publicly.

## DOI identifier scheme
DOIs are minted with a configured DataCite prefix (see `doi.prefix` in `config.yaml`). The suffix is derived
from the dataset key:
- Concept DOI: `{prefix}/ds{key}` (see `DoiConfig.datasetDOI`).
- Version DOI: `{prefix}/ds{key}.v{attempt}` (see `DoiConfig.datasetVersionDOI`).

## Dataset type matrix

| Origin   | Concept DOI | Version DOI | Published when                                                |
|----------|-------------|-------------|---------------------------------------------------------------|
| EXTERNAL | yes         | yes, per import attempt | Concept and each version DOI auto-publish on CREATE if the dataset is public |
| PROJECT  | yes         | no          | Projects are typically private; their concept DOI stays DRAFT |
| RELEASE  | yes         | no          | Concept DOI is explicitly published when the release is made public (private → public transition) |

## DataCite state machine
DataCite knows three DOI states: `DRAFT` → `REGISTERED` → `FINDABLE`.

| Application operation | DataCite effect |
|-----------------------|-----------------|
| create (private)      | Creates a DRAFT DOI with metadata. |
| create (public)       | Creates a DRAFT, then immediately PUBLISHes it to FINDABLE. |
| publish               | PUBLISH event: DRAFT/REGISTERED → FINDABLE. |
| update                | Updates metadata and/or target URL; does not change state. |
| delete on a DRAFT     | Hard-deletes the DOI. |
| delete on a FINDABLE  | Sends a HIDE event — the DOI is moved to REGISTERED (no longer resolvable publicly); the target URL is updated so it still points to the correct resource if ever un-hidden. |

Note: making a dataset private again via `DatasetDao.unpublish()` does **not** revert its DOI state. Once a DOI
is FINDABLE it stays FINDABLE; this is deliberate. Use an explicit delete if you truly need to hide it.

## Event flow and technical implementation
DOIs are managed by a `DoiService` — in production this is implemented by `DataCiteService`, which talks to
the [DataCite REST API](https://api.datacite.org).

All DOI mutations are decoupled via `DoiChange` events on the central `EventBroker`:

**Producers**:
- `DatasetDao.createAfter` — emits `DoiChange.create(conceptDoi)` for every new dataset.
- `DatasetDao.updateAfter` — emits `DoiChange.update(conceptDoi)` on any dataset update (to keep DataCite metadata in sync).
- `DatasetDao.publish` — emits `DoiChange.publish(conceptDoi)` when a non-release dataset is flipped to public.
- `ImportJob` — after a successful import, emits `DoiChange.create(versionDoi)` for the new version DOI.
- `PublishReleaseListener` — on a release's private→public transition, emits `DoiChange.publish(conceptDoi)`
  and `DoiChange.update(prevReleaseDoi)` (to refresh the target URL of the previously-latest release).
- `DoiUpdateCmd` — bulk operations for manual repair or backfill.

**Consumer**:
`DoiChangeListener` subscribes to the bus and persists events into a Chronicle-backed map so they survive
application restarts. Scheduling rules:
- `CREATE`, `PUBLISH`, `DELETE` events run immediately.
- `UPDATE` events are **debounced** by `cfg.waitPeriod` seconds — bursts of dataset edits collapse into a
  single DataCite update call.
- `CREATE` on a public dataset auto-publishes after the draft is created (this is how public external-dataset
  version DOIs become FINDABLE without the importer issuing an explicit PUBLISH).

**Retry and rate limiting**:
- Failed events are re-queued with an exponential back-off of `fails²` hours.
- DataCite HTTP 429 (rate limited) pauses the queue for 6 minutes before retrying.
- Events that can't be matched to a dataset (e.g. prefix mismatch, deleted dataset) are dropped with a warning.

## DOI metadata and chaining
`DatasetConverter` (in the `doi` module) builds the DataCite `DoiAttributes` from the live dataset (for the
latest attempt) or from `DatasetArchive` (for older attempts). It also populates related-identifier links:
- Version DOIs are chained to their previous and next import attempts (via `DatasetImportMapper.getLast/getNext`).
- Release concept DOIs are chained to the previous and next release of the same project (via
  `DatasetMapper.previousRelease/nextRelease`), and the DOI target URL on the latest release differs from
  non-latest ones so that the canonical "latest" URL always resolves to the current public release.

## DoiUpdateCmd
`DoiUpdateCmd` (in the `webservice` module) can be used to manage DOIs in bulk, typically to repair drift
between ChecklistBank and DataCite. Key flags:

| Flag | Effect |
|------|--------|
| `--key K` | Update dataset `K`; if it is a project, also walk its releases. |
| `--doi DOI` | Update a specific DOI. |
| `--all` | Update all projects and all external datasets. |
| `--versions N` | Also update the last `N` import-attempt version DOIs, chaining prev/next links. |
| `--noUpdate` | Only create/publish missing DOIs; skip metadata updates. |
| `--publishOnly` | Only publish existing DOIs that should be FINDABLE but are still DRAFT — the recovery path after the release-publication regression fixed in `081d92ddf`. |
| `--threads T` | Parallel DataCite requests (watch for HTTP 429). |
