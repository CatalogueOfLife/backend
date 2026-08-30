# Splice moved classifications inside Elasticsearch

Date: 2026-08-30
Status: shipped

## Problem

`ClassificationUpdater` lost its body in `bb977f8df`, the ES rewrite that removed `EsNameUsage` and
`NameUsageWrapperConverter` which it depended on. It was left as a 17-line stub with an empty `accept()`
and never reimplemented, but it stayed wired in.

So since that commit, every taxon move ran the full sequence and achieved nothing:

```java
// NameUsageIndexServiceEs.updateClassification, before
PgUtils.consume(() -> mapper.processTree(datasetKey, null, rootTaxonId), batchUpdater);  // full subtree scan
EsUtil.refreshIndex(client, esConfig.index.name);                                        // whole-index refresh
LOG.info("Successfully updated {} name usages", indexer.documentsIndexed());             // always 0
```

`TaxonDao.updatedParentCacheUpdate` fires this whenever a `parentID` changes, so the denormalised
`classification` array of every descendant went stale — that is what the `TAXON_ID` filter and the
classification shown in search results read. The log line said it had succeeded.

Blast radius was bounded: taxa can only be moved in editable **project** datasets, which are few. Releases
and external datasets never move taxa, so the great majority of documents were never at risk, and the
backlog is clearable with `POST /admin/reindex?datasetKey={key}` per project.

## Goals

1. Actually update the classifications.
2. Keep Postgres load minimal. A subtree is typically 1-100k usages but can reach ~1M.

## Non-goals

- Moving the work onto `JobExecutor`. `TaxonDao` still fires it via `CompletableFuture.runAsync`; the heavy
  lifting now happens inside Elasticsearch as its own task, so there is little left on the common pool to
  bound. If that plumbing wants tightening it is a separate change.

## Design

When a taxon moves, each descendant's classification changes only in its *upper* part. The path from the
moved taxon down to the descendant is untouched. Classifications are ordered highest-root-first with the
usage itself last (`array_append` onto the parent's array in `processTree`), so:

```
descendant.classification = [moved taxon's NEW full classification] + [existing entries below it]
```

That gives one small Postgres query for the moved taxon's new chain — `classification_sn(key, id, true)`,
reusing the existing `SimpleNameArrayTypeHandler` — and then a single `_update_by_query` over
`classification.id = rootTaxonId`, the same term filter `deleteSubtree` already uses, with a painless
script that locates the root in each stored classification and replaces the head.

**Zero per-document Postgres reads, at any subtree size.** This is better than the pre-`bb977f8df` code,
which read the whole subtree out of Postgres via `processTree` just to compute classifications it could
have spliced.

Submitted with `wait_for_completion=false` and `slices: auto`, so `updateClassification` returns the ES
task id rather than `void` and a million-document update is traceable instead of invisible.

The new classification is passed through `EsModule.contentMapper()` before becoming a script param, so
spliced entries carry exactly the shape the indexer writes — ranks as ordinals, `label`/`labelHtml` dropped
by `SimpleNameMixIn`. Without that, spliced ancestors would differ from indexed ones in a way nothing would
notice until someone compared two documents.

`ClassificationUpdater` itself is deleted; there is nothing left for it to do.

## Rejected: reindex the subtree from Postgres

`update(datasetKey, usageIds)` already does delete-and-reindex and is covered by ITs, so routing
`processTree` ids through it would have been the smallest, safest change — and it is the only option
compatible with `_source` excludes (see [2026-08-30-es-index-disk-usage.md](2026-08-30-es-index-disk-usage.md)).

Rejected on the Postgres constraint: it re-reads every usage in the subtree as a full `NameUsageWrapper`,
up to ~1M rows, on what is an ordinary editorial action.

Worth knowing when weighing this up: **Elasticsearch has no in-place update.** A partial update deletes and
re-adds the Lucene document anyway. The splice does not save the ES write — it saves the Postgres read and
the re-serialization, which is precisely the cost that mattered here.

## Outcome

- Shipped in `39fa8deb7`. Cost: the `_source` excludes from `5a751d57a` had to be reverted, ~1-3 GB.
- `ClassificationUpdateTest` moves `p2` from `k1` to `k5` in Postgres only, then asserts the moved taxon and
  both children are rewritten while the `p3` branch is not. It was confirmed to fail without the fix
  (`expected:<[k5, p2]> but was:<[k1, p2]>`), not merely to pass with it.
- It also asserts `usage.label` and `alphaIndex` survive the update, which is the standing guard against
  anyone re-adding `_source` excludes.
- `TestDataRule` is a `@ClassRule`, so the Postgres move leaks between test methods; the test resets `p2`
  to `k1` in `@Before` rather than relying on cleanup.
