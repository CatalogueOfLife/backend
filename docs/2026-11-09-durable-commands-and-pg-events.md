# Durable outbox commands, then events over Postgres LISTEN/NOTIFY

Date: 2026-11-09
Status: Phase 1 (outbox commands) implemented on branch `feat/durable-commands`, not merged or deployed.
Phase 2 (LISTEN/NOTIFY) not yet implemented.

## Why

A review of the `EventBroker`, a Chronicle Queue in `/srv/events` shared by every JVM on the app host (rw and ro
server, both apps of a blue-green deploy), found the Chronicle mechanics sound for same-host fan-out. The
problems were elsewhere:

1. **Commands rode a lossy broadcast.** Every JVM reads with its own tailer wound to the end at start, so delivery
   is at most once and from now on, and every live JVM acts on every event. That is right for cache invalidation
   and wrong for anything that has to happen exactly once:
   - `DoiChangeListener` ignored events while stopped (the gap between `stop-all` on the old app and `start-all`
     on the new one) and discarded its pooled DOI updates, kept in a MapDB file per start, on every deploy.
   - `DeleteSector` was lost whenever no executor or names index ran, i.e. in that gap and during every names
     index swap.
   - The COL exports of a release published in that gap were lost.
   - `PublishReleaseListener` (external publish actions, DOI re-publication) and `PublisherChangeListener` ran on
     both apps during a deploy.
2. **Kryo `FieldSerializer` of whole `Dataset`/`User` objects** travels between two code versions during a
   deploy; any field change breaks decoding (`Dataset.lastImportState` already changed type).
3. **Heavy work on the single dispatch thread**: `archiveRelease`, a usage count for matcher refreshes, a scan of
   all projects for publisher changes.
4. **The ro server reloads from the pg2 standby** within milliseconds of a pg1 commit, so a refresh can re-cache
   stale data (`LatestDatasetKeyCacheImpl` for an hour). Plausible, not confirmed on prod.
5. Smaller broker bugs: listeners added to plain `ArrayList`s after polling started, the thread-local
   `acquireAppender()` (removed in Chronicle 5.27) shared across threads, a Linux-only file cleanup that spins
   with ERROR logs elsewhere after 24h, `publish()` swallowing errors.

Also found: `PublisherChangeListener` could never insert (no `created_by`, a NOT NULL column), a release DOI was
published twice, a dataset made public through a plain PUT never got its DOI published, and
`DatasetDao.updateAfter` tested `obj.getDoi()` but sent `old.getDoi()`.

## Phase 1: commands become outbox rows (implemented)

- Table `outbox (id, type OUTBOXTYPE, key, params jsonb, status OUTBOXSTATUS, due, version, attempts, last_error,
  claimed_by, created_by, created, modified, UNIQUE (type, key))`, DDL in `dbschema.md`.
- Producers call `Outbox.*` with the session of the change wherever there is one (`DatasetDao.updateBefore` shares
  the transaction of `mapper.update`; `createAfter`/`deleteAfter` commit right after the row change), an autocommit
  session otherwise (`ImportJob`, admin endpoints, `LegacyEventBridge`).
- A request for a waiting `(type, key)` is merged: params `||`, `LEAST(due)`, `version + 1`, a failed command is
  revived with its attempts reset, a running one stays running and is requeued instead of completed.
- `OutboxRunner` (component `Outbox`, after `JobExecutor`) polls every 2s, claims due rows per type with
  `FOR UPDATE SKIP LOCKED` in autocommit statements and runs each on a virtual thread, bounded per type by
  `OutboxHandler.concurrency()`. Outcomes: success deletes the row (if the version is unchanged); `RetryLater`,
  `Unavailable` or `TooManyRequests` retry after `retryLaterSeconds` without counting; anything else retries after
  `retryDelay(attempts)` until `maxAttempts` or a permanent error marks it FAILED and calls `onFailed`.
  `start()` resets rows left RUNNING, `stop()` hands back its own.
- Types:
  - `DOI` - one row per DOI, actions as flags run in the order DELETE (alone), CREATE, UPDATE, PUBLISH by
    `DoiHandler`, which replaces `DoiChangeListener`. UPDATE is delayed by `doi.waitPeriod`. `DoiUpdater` now only
    toggles whether DOI rows are claimed.
  - `SECTOR_DELETE` - `SectorDeleteHandler`, retries while the sector is busy instead of recording rejected sync
    attempts.
  - `RELEASE_PUBLISHED` - `ReleasePublishedHandler` submits one `PublishReleaseJob`, which does what
    `PublishReleaseListener` did minus the DOI publish (the update enqueues that).
- `PublisherChangeListener` became `DatasetDao.addChangedPublisherToProjects`, inline in the update transaction.
- `UsageMatcherFactory.datasetDataChanged` stays a broadcast reaction (it only acts where an executor runs and
  `MatcherReconcile` is its safety net); the dataset size is now counted in the `MatcherBuildJob`.
- Admin: `GET /admin/outbox?type=&status=`, `POST /admin/outbox/{id}/retry`, `DELETE /admin/outbox/{id}`;
  `GET /admin/doi` lists the DOI rows. Health check `outbox` reports pending rows overdue by 15 minutes.
- `doi.store`, the directory of the MapDB event files, is gone. No deploy config set it; the old files under
  `/tmp/clb/datacite` can be deleted after the deploy.

### Deliberate behaviour changes

- A dataset made public through any update, not only `/publish`, gets its DOI published.
- The DOI debounce is a fixed window from the first request instead of restarting with every change, so constant
  edits can no longer postpone an update forever.
- `PublishReleaseJob` reports failed steps by failing, with a job log, and is never retried by itself, so publish
  actions do not fire twice.

### Deploying phase 1

1. Run the DDL before the deploy.
2. The old app discards its pooled DOI updates one last time at `stop-all`, logged as "Discard queued DOI";
   re-enqueue them with `PUT /admin/doi/{suffix}`.
3. `LegacyEventBridge` on the new app turns `DoiChange`/`DeleteSector` events of the old app into outbox rows.
   This only works while the event classes and `EventKryoPool` stay unchanged.
4. Don't publish releases while both versions run: the old app's `PublishReleaseListener` still reacts.

### Left as is

- A job a handler submitted is subject to the executor: discarded by `stop()` while queued it is CANCELED, as
  before. Making `PublishReleaseJob` or sector deletions `resubmitAfterStop` is a possible follow-up.
- `resetStale` on start assumes one active runner, the same assumption `JobExecutor.cancelStale` makes.

## Phase 2: LISTEN/NOTIFY replaces Chronicle (not yet implemented)

With commands out of it, the broker only carries cache invalidation. Plan:

- `EventBroker` becomes an interface, `PgEventBroker` sends `pg_notify('clb_events', json)` (inside the caller's
  transaction where there is one: delivered on commit, dropped on rollback) and listens on one dedicated
  autocommit connection to the primary per JVM. The ro server, whose main db is the pg2 standby where `LISTEN`
  is impossible, gets an explicit `broker.db` pointing at pg1. `LocalEventBroker` serves the CLI, the bundle server
  and tests.
- Thin JSON events (Jackson, unknown properties and types ignored, < 8000 bytes): keys plus the facts listeners
  need (`privat`, `privatChanged`, `origin`, `sourceKey`, `createdBy`, usernames). An oversized event degrades to
  `FlushCaches`. This removes the Kryo version skew.
- On the ro server, before dispatching a batch, read `pg_current_wal_insert_lsn()` on the listen connection (insert
  LSN, as pg1 runs `synchronous_commit = off`) and wait until pg2's `pg_last_wal_replay_lsn()` reaches it, so
  reloads never re-cache stale data.
- Every 30s `SELECT pg_notification_queue_usage()` on the listen connection proves it alive and reports a filling
  queue; any error reconnects with backoff and flushes every cache, as notifications sent meanwhile are gone.
- Listeners registered before dispatch starts, `CopyOnWriteArrayList`.
- Remove `chronicle-queue`, the Kryo event pool, `EventQueueTask` and the legacy event classes. Chronicle Map stays
  for the names index.
- Deploy: the old (Chronicle) and new (NOTIFY) apps cannot hear each other during that one overlap, so a
  `clear-caches?broadcast=true` once the old apps are gone is mandatory.

## Rejected alternatives

- **The `job` table as the outbox.** The executor queue lives in memory and nothing polls the table; `submit()`
  applies request time checks (queue size, duplicates, users) that must not fail a DAO transaction; jobs have no
  merge or debounce and would flood the job history with DataCite calls.
- **A DOI-only table.** Sector deletion and release publishing need the same claim, retry and merge mechanics.
- **Gating side-effect listeners on the active app.** Fixes duplicates, not the loss in the deploy gap.
- **External brokers** (Redis, NATS, RabbitMQ, Kafka) or **JGroups/Hazelcast** - new infrastructure for hundreds of
  events a day, while every JVM already talks to the same Postgres.
- **TTL-only caches** - a dataset made private must stop being served by the ro server at once.
- **Dual publishing during the phase 2 transition** - more code than a one-off cache flush.
