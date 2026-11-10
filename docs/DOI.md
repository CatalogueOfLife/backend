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

All DOI mutations are durable commands in the `outbox` table, see
[the design record](2026-11-09-durable-commands-and-pg-events.md). A producer writes the command in the
transaction of the change that causes it, so it is never lost and never exists without the change, and the
`OutboxRunner` of the app whose `Outbox` component is started runs it once.

**Producers** (`Outbox.doi(...)`):
- `DatasetDao.createAfter` — `CREATE` of the concept DOI of every new dataset.
- `DatasetDao.updateBefore` — `UPDATE` of the concept DOI on any dataset update, plus `PUBLISH` when the update
  turns a private dataset public. That covers `DatasetDao.publish` as well as a plain PUT.
- `DatasetDao.deleteAfter` / `deleteBefore` — `DELETE` of the concept DOI, `UPDATE` (or `DELETE` for a project) of
  the DOIs of the release sources.
- `ImportJob` — after a successful import, `CREATE` of the new version DOI.
- `PublishReleaseJob` — for COL releases, `UPDATE` of the previous release's DOI so its target URL no longer
  resolves as the latest.
- `POST|PUT|PATCH /admin/doi/{suffix}` — manual create, update and publish.
- `DoiUpdateCmd` — bulk operations for manual repair or backfill; it talks to DataCite directly.

**Consumer**:
`DoiHandler` runs the DOI commands. There is one command per DOI, all requested actions are flags of it and run in
a fixed order: a `DELETE` runs alone; a `CREATE` sends the current metadata and, for a public dataset, publishes the
DOI; an `UPDATE` sends the metadata unless a create just did; a `PUBLISH` publishes unless a create just did.
Metadata is read when the command runs. Every step is safe to repeat: a create of an existing DOI updates it.
- `CREATE`, `PUBLISH` and `DELETE` are due immediately.
- An `UPDATE` is **debounced**: it is due `doi.waitPeriod` seconds after the first request, and later requests
  are merged into it, so a burst of dataset edits ends up in a single DataCite call.
- DOIs with a foreign prefix or of an unknown dataset are dropped with a log line.

`DoiHandler` runs two commands at a time, and a finished command makes room for the next due one at once.
It is the `DoiUpdater` component. While it is stopped, e.g. on the old app of a deploy or during a DataCite outage,
DOI commands wait in the outbox, and the outbox health check does not report them as overdue. Where the component is
`DISABLED` (dev) the handler runs without being started against `DoiService.passThru()`, so the commands drain
without touching DataCite.

**Retry**:
- A failed command is retried after `attempts²` hours, at most `outbox.maxAttempts` times (12 by default).
- Invalid metadata (pre-flight validation, or HTTP 422 from DataCite) fails the command at once and mails the
  error. A later change of the dataset revives a failed command.
- Failed commands are kept for `outbox.failedRetentionDays`, listed by `GET /admin/outbox?type=DOI&status=FAILED`
  and retried with `POST /admin/outbox/{id}/retry`.

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
