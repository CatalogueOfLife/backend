# Name usage archive: newest version per id, and a migration that keeps every id

Date: 2026-09-15
Status: implemented on branch `chore/col-stable-id-improvement`, not merged or deployed yet. It replaces the archive
migration of [2026-09-15-stable-id-evidence-model.md](2026-09-15-stable-id-evidence-model.md).

## Why

The stable id rework needs `name_usage_archive` to hold every id as it looked in the latest release that carried it,
not in the first. Its migration deletes a project's archive and replays the releases that still exist. That loses
every id only deleted releases carried - COL deletes monthly releases after a year - so those ids can never be
resurrected. Worse, if one of them was the highest id ever issued, the id sequence starts below it and hands already
published ids to different names.

A review of the branch found more ways the archive goes wrong:

- the publish-time refresh only rewrites a row when one of six identity columns changed, so a changed classification
  or accepted name stays stale, and both feed the id comparison;
- any published release overwrites the archived version, including an extended release published after the next base
  release;
- `release_keys` gains a duplicate whenever a release is archived twice, which every blue-green deploy does: the event
  broker delivers the publish event to both apps;
- matches are copied through the archived name id, which sector syncs re-mint, so they silently miss;
- a publish-time archive that fails is only logged, and the next release maps ids without that release in the archive.

## Decisions

Taken with the maintainer:

- The archive keeps the version of the newest base release generation, see below.
- An extended release's base release comes from the job that built it, never from the dataset notes, which other
  projects may write differently.
- The migration is an admin job per project, not a CLI command: it holds the project's dataset lock and uses the names
  index.
- It refreshes rows in place rather than building a shadow table.
- Archiving stays on publish. Doing it at the start of the next release would add to a COL extended release that
  already takes more than 32 hours and has to stay below 48, and a publish does not realistically race a release.
- A release job refuses to start while a public release of its project is missing from the archive.

## Which version an archived id holds

Every release of the project gets a rank. An extended release belongs to the generation of the base release it was
built on. Generations are ordered by their base release's attempt; within one generation the base release ranks above
its extended releases, and a newer extended release above an older one.

A release **supplies** versions if it is public (not private), not deleted, and listed as ignored in neither the
release nor the extended release config. Every archived id holds the version of the highest ranked supplying release
among its `release_keys`. An id no supplying release carries keeps the version it has.

A version is the whole row: the name columns and `n_id`, status, parent, accepted name, classification, according to,
basionym and published in.

### The base release of an extended release

The extended release's dataset attempt addresses the project's `dataset_import` row, and that row's job recorded
`params.baseReleaseKey` when it was submitted (`AbstractProjectCopy.CopyParams`). Every extended release built since
the job table exists has it. For older ones the job consolidation migration filled it in once, from the notes.

Where it is missing, the base release is the newest base release of the project that is not private, not a temporary
dataset (key below 100000000), was not deleted before the extended release was created, and was created at least one
full day before it.

## The per release step

One step archives one public, not deleted release R. Publishing, the migration job and building an empty archive all
use it:

1. insert rows for the ids R carries that the archive lacks, with an empty `release_keys`;
2. if R supplies versions, rewrite the rows of ids R carries that no higher ranked supplying release carries, wherever
   any archived column differs from R;
3. write matches for the rows inserted or rewritten, from R's own name matches joined through R's usages rather than
   the archived name id;
4. if R is the project's highest ranked public release, apply the superseded pairs staged for R and clear
   `superseded_by` on the ids R carries again. Any other release discards its staged pairs: the ids it dropped are
   already decided by a newer release, whose redirects it must neither set nor clear;
5. add R's key to every row of an id R carries whose `release_keys` lacks it, keeping the array sorted - last, and in
   one statement, so a release whose key appears in the archive has been archived completely.

Every statement only writes what is missing or different, so running the step twice, even at the same time, writes
nothing the second time. A release that does not supply versions, e.g. an ignored one, still gets its missing rows and
its key, but never rewrites a version.

A row inserted in step 1 is ignored by the id provider until step 5 gives it a key, and a release cannot start in that
window: its key is missing, so the start check below fails.

## The migration job

`ArchiveRefreshJob`, submitted with `POST /admin/archive/refresh?projectKey=<key>`, optionally `dryRun=true`. It is a
dataset blocking job on the project key, the lock base and extended release jobs take, so a release of that project
submitted meanwhile waits. It cannot be queued while the names index is offline, and is a duplicate of another for the
same project.

1. Rank the project's releases and log the ranking, marking every extended release whose base came from the fallback.
2. Run the per release step for every public, not deleted release, highest rank first, without its match statement.
   Highest first means a row is rewritten at most once, by its best release.
3. Sort and de-duplicate `release_keys` on the project's remaining rows.
4. Rematch the project's archived names through the names index. Only changed matches are written, and a name that no
   longer matches loses its match row.
5. Report counts per release and in total, in the job's final step and its log.

A dry run ranks and counts without writing, including how many rows would change their scientific name, which is the
rematch load. The job never deletes a row, and a rerun changes nothing that is already right, so a failed or cancelled
run is simply started again.

## Publishing

`PublishReleaseListener` runs the per release step for a release that goes public, where it archives today. Because the
step is safe to run twice, both apps of a deploy handling the event do no harm. A late extended release of an older
generation never overwrites a newer version: it inserts the ids only it carries, adds its key, and leaves superseded
redirects alone.

## The release start check

Before a base or extended release job creates its dataset, it checks that every public, not deleted release of the
project that has any usage carries its key somewhere in the archive - one lookup per release on the existing GIN index
of `(dataset_key, release_keys)`. If one does not, the job fails and names it; the migration job archives it. The check
takes milliseconds and adds nothing measurable to a release.

## Runbook

After the deploy, for each project before its first release, COL first:

1. back up the project's archive and match rows into tables of their own;
2. dry run the job and check the ranking and the counts;
3. run it;
4. `VACUUM (ANALYZE) name_usage_archive`, since the first run rewrites most rows;
5. before publishing the first release afterwards, diff its created, deleted and resurrected reports against the
   previous attempt.

The start check blocks the releases of a project whose archive lacks one of its public releases until the job has run
for it.

Do not use the deploy repository's `archive.sh` for this: the `archive` command refuses a project whose archive is not
empty, and emptying it first is exactly what loses the ids of deleted releases. That command, `rebuildProject` and
`rebuildAll` remain for building an empty archive from scratch, through the per release step in rank order.

## Tests

- Ranking, as a unit test: the generation from the job params, the one day fallback including a base release deleted
  before the extended release, ignored and deleted releases not supplying, and two extended releases of one base.
- Every new statement runs against the schema, and running it a second time writes nothing.
- An integration test on a small project with two base releases and an extended release: every row holds its newest
  generation's version, an id only a deleted release carried keeps its row and keys, a rerun writes nothing, a renamed
  name's match follows it, a late extended release of an older generation only adds its key and leaves redirects
  alone, and a release job refuses to start while a public release lacks its key.
- The release integration tests keep passing with `ArchivingRule` on the new step; `ExportManagerIT`,
  `IdProviderReleaseIT` and `XReleaseIT` also touch the archiver or the publish listener.

## Not in this change

From the same review, deliberately left for their own changes:

- release counts, the main seniority key, count extended release keys, so ids from after extended releases began
  outrank older ones;
- the candidate ordering has no usage id as its last key, so identical duplicates still follow the store's scan order;
- an unparsable authorship never counts as a contradiction;
- superseded pairs staged for a release that is never published, or deleted, are never removed;
- extended releases pick name ids again for names their base release already mapped;
- resolving a superseded id over HTTP.

## Rejected alternatives

- **Delete and rebuild**, the branch's migration: it can only replay releases that still exist, see Why.
- **Build a shadow table and swap it in**: no bloat and a rename as rollback, but it needs double the disk, table DDL
  from inside a job, migrates every project at once because one table holds them all, and would be a second code path
  next to publishing.
- **A CLI command**: `DatasetLock` lives in each JVM, so a command cannot hold back the server's releases, and the
  server owns the names index.
- **Archiving at the start of the next release**: it removes the publish race and the double run, but adds its work to
  the release window. Only the check survives.
- **The base release from the dataset notes**: free text, which other projects may write differently and editors can
  change.
- **The newest release by key**: extended release edits of base usages would become the evidence the next base release
  is scored against, rows would flip twice a month, and a late extended release would roll data back.
- **Base releases only**: an id kept alive only by extended releases would stay frozen at its last base version.

## Outcome

- The release start check runs right after the release job created its dataset and import metrics
  (`ProjectRelease.initJob`, which `XRelease.initJob` passes through), not before. A job failing earlier breaks
  `onError` and `onFinishLocked`, which expect both. It still runs before any id work.
- The version comparison picks a name's basionym with an ordered `LIMIT 1` lateral join. The plain join the branch used
  yields one row per basionym relation and an UPDATE applies an arbitrary one, so the "only rewrite what differs"
  statement would have written on every run.
- The basionym lookup in the version statements filters `name_rel` and `name` on the literal release key instead of
  correlating on the usage's dataset key. A correlated `LIMIT 1` lateral subquery is not flattened, so Postgres planned
  every `name_rel` and `name` partition; EXPLAIN confirmed a single partition each with the literal key.
- A dry run counts each release against the archive as it is before the run, so the counts of lower ranked releases
  include records a higher ranked one would already have written.
- `createMissingMatches`, `refreshMatches` and `createAllMatches` are gone: matches follow the release usage's own
  `name_id` on publish, and the names index in the refresh job.
- A release that does not supply versions only writes and removes archive matches for the rows it inserted in the same
  run (`cardinality(release_keys) = 0`, via an `insertedOnly` flag on `copyReleaseMatches` and
  `deleteUnmatchedReleaseMatches`). The plan's first version also re-pointed the matches of ids only deleted or
  ignored releases carried.
- Tests: `ReleaseRankingTest`, `NameUsageArchiverIT` on the `archive` fixture, including
  `nonSupplyingReleaseOnlyTouchesMatchesItInserted`, `ArchiveRefreshJobIT` and
  `ProjectReleaseIT.releaseRefusesUnarchivedPublicRelease`.
