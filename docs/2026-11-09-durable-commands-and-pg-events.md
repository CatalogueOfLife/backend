# Durable outbox commands, then events over Postgres LISTEN/NOTIFY

Date: 2026-11-09
Status: Phase 1 (outbox commands) implemented in PR #1702, phase 2 (LISTEN/NOTIFY) on branch `feat/pg-events`,
stacked on it. Neither merged nor deployed.

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

## Phase 2: LISTEN/NOTIFY replaces Chronicle (implemented)

With commands out of it, the broker only carries cache invalidation.

- `EventBroker` is an interface. `PgEventBroker` publishes with `pg_notify(channel, json)` on a pooled autocommit
  connection after the change committed, as the Chronicle broker did, and listens on one dedicated autocommit
  connection to the primary per app (`PgConfig.connectLongLived`, application name `<applicationName>-events`, TCP
  keepalive). `EventBrokers.build` picks it: a server whose main db `pg_is_in_recovery()` needs `broker.db` with the
  primary or refuses to start; it then publishes on short lived connections to that primary. `LocalEventBroker`
  dispatches synchronously within the JVM for the CLI commands, the release bundle (`WsROServer.buildBroker` hook) and
  tests; `broker.mode: LOCAL` selects it by config.
- The connection LISTENs when the broker is built, before the caches warm up, and one thread reads it once the app
  starts, after every listener is registered (`CopyOnWriteArrayList`). Notifications wait on the connection meanwhile.
- Events are thin JSON (`EventCodec`, type in `"t"`, unknown properties and types ignored, an event of 7900 bytes
  or more becomes `FlushCaches`). `DatasetChanged` carries type, key, user, origin, sourceKey, publisherKey, createdBy,
  deleted, privat, privatChanged and the usernames to invalidate; `UserChanged` type, key and username.
  `EntityChanged`, `DoiChange`, `DeleteSector`, their listeners, `LegacyEventBridge`, the Kryo event pool and the
  `chronicle-queue` dependency are gone. Chronicle Map stays for the names index.
- Listeners: `DatasetInfoCache` builds its info from the event facts instead of reloading, and now also drops the
  label; `ColJerseyBundle` refreshes the latest release on deletions too; `UsageMatcherFactory` schedules a refresh
  job for a dataset made public instead of counting its usages on the event thread; every cache implements
  `Listener.flushCaches()`.
- On a server reading from a standby, a batch of notifications is dispatched once the standby replayed up to the
  primary's `pg_current_wal_insert_lsn()`, read on the listen connection right after the batch arrived (insert, not
  write position, as pg1 runs `synchronous_commit = off`), or after `broker.standbyWaitSeconds`.
- Every `broker.checkSeconds` (30) `SELECT pg_notification_queue_usage()` probes the connection; the health check is
  red when it is down, unchecked for three periods or the queue is half full. Any error closes the connection and
  reconnects with a backoff up to `broker.maxReconnectSeconds`, then every listener flushes its caches.
- `POST /tasks/events` on the admin port shows the status and the last 100 events, replacing the queue dump.
  `POST /tasks/flush-caches?broadcast=true` makes every app flush its caches.
- `BrokerConfig` ignores the Chronicle settings (`queueDir`, `pollingLatency`, `poolSize`, `bufferSize`), so the
  current deploy configs still parse.

Deviations from the plan: no `publish(SqlSession, Event)` variant, every producer already publishes after its commit;
one thread both reads and dispatches, as notifications queue up in Postgres while it waits for the standby; liveness
is the queue usage probe rather than a self-addressed ping.

### Deploying phase 2

0. Deploy phase 1 on its own first, per environment: phase 2 removes the `LegacyEventBridge`, so an app of a
   version before phase 1 must not be running next to it.
1. `config-ro.yml` is an overlay that `deploy.sh` merges onto `config.yml`, so the ro server inherits the `broker`
   block. The overlays of prod and test point `db` at the pg2 standby and therefore need `broker.db` pointing at pg1,
   the same shape as their `pgLog.db` (`applicationName: checklistbank-ro`, the connection appends `-events`). Add it together with this
   version: an older jar rejects the unknown property. Dev's ro server inherits the primary and needs nothing.
2. During the deploy the old apps (Chronicle) and the new ones (NOTIFY) cannot hear each other. Avoid privacy flips
   and permission changes until the old apps are gone, then run
   `curl -X POST http://localhost:<admin port>/tasks/flush-caches?broadcast=true` on the new rw app once. This is
   mandatory: the private filter and the user cache have no expiry.
3. Afterwards remove `broker.queueDir` from the deploy configs and delete `/srv/events`.

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
