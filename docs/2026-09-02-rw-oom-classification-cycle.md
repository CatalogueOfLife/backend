# rw server OOM: a parent cycle walked forever while building classifications

Date: 2026-09-02
Status: shipped — cycle guard in `NameUsageProcessor`, `wouldCreateCycle` added to
`HierarchySync.rewireProjectParents`.

## What happened

The production rw server (`-Xmx31g -XX:+ExitOnOutOfMemoryError`) died three times in as many days.
The third crash left a 45 GB heap dump. There is no record of the dump beyond this document — it was
analysed with a throwaway streaming HPROF parser, not kept.

## What the dump said

**99.5 % of the heap was one `java.util.ArrayList`.** Its `elementData` was an 841 MB `Object[]` with
capacity 105,136,605 and ~95,090,000 entries used. Nothing in the object graph referenced that list:
it was a live local variable on a job thread's stack.

| class | shallow bytes (HPROF space) | % | instances |
|---|---:|---:|---:|
| `byte[]` (String values) | 14.50 GB | 37.2 % | 384,334,227 |
| `java.lang.String` | 11.50 GB | 29.5 % | 383,326,214 |
| `SimpleNameCached` | 5.80 GB | 14.9 % | 47,545,212 |
| `SimpleName` | 4.23 GB | 10.8 % | 47,548,369 |
| `java.lang.Integer` | 1.91 GB | 4.9 % | 95,297,047 |
| everything else | 0.06 GB | 0.2 % | — |

47,545,212 + 47,548,369 = 95,093,581 — the list, exactly. A contiguous window of it read
`SimpleNameCached, SimpleName, SimpleNameCached, SimpleName, …` with no exception.

The thread stack in the dump:

```
HierarchySync.execute → SectorRunnable.execute → updateSearchIndex
  → NameUsageIndexServiceEs.indexSector → NameUsageProcessor.processSector → processTree
  → PgUtils.consume → NameUsageProcessor.addClassification
  → UsageCache.getOrLoad → UsageCacheMapDB.get → MapDbObjectSerializer.deserialize → Kryo
```

Sampling the most repeated String *contents* in the heap named the data:

| string | occurrences | role |
|---|---:|---|
| `f-S5S5JzNktJ14g8rcP210` | ~95.1 M | usage id |
| `wi1z1dFnRlPdB_XtmJzXc` | ~95.1 M | usage id |
| `Prunus domestica insititia` | ~47.6 M | name |
| `Prunus domestica subsp. insititia` | ~47.5 M | name |
| `(L.) Bonnier & Layens` | ~47.5 M | authorship |
| `(Gro-6)` | ~47.5 M | authorship |

Each id occurs twice per turn of the loop — once as the entry's own `id`, once as the other entry's
`parent`. Two usages, each the other's parent. Decoding two adjacent list entries gives the pair
exactly:

| object | id | name / authorship | parent | resolved from |
|---|---|---|---|---|
| `SimpleName` | `wi1z1dFnRlPdB_XtmJzXc` | *Prunus domestica* subsp. *insititia* (L.) Bonnier & Layens | `f-S5S5JzNktJ14g8rcP210` | the `taxa` object cache |
| `SimpleNameCached` | `f-S5S5JzNktJ14g8rcP210` | *Prunus domestica insititia* (Gro-6), sectorKey 6 | `wi1z1dFnRlPdB_XtmJzXc` | Postgres, via `usageCache` |

## The job, and when each half of the cycle was written

The live `HierarchySync` is still on the heap and dates itself:

- sector **3** of project **315784**; `subjectDatasetKey` 3 with `useXRelease`, so `sourceDatasetKey`
  resolved to XRelease **316165**; user 728; sector `syncAttempt` 83
- `created` = `started` = **2026-09-02 13:11:33**, `step` = `indexing`, with `sourceCache` and
  `sourceLoader` already nulled — `doWork` was over and the reindex was running
- `projectMatches` 3644, `projectStatuses` 3644, `projectParents` 4463, `sourceToProject` 3200,
  `namePlacements` 47
- the dump was written at **13:25:06 UTC**, so the whole job lived 13.5 minutes

`name_usage.modified` on the two rows reads 12:35:15 (`f-S5S5…`) and 13:12:09 (`wi1z1…`), and
`updateParentId` always sets `modified = now()`. The reindex runs after `doWork`, so everything it
read was written at or before 13:12:09 — and it read the cycle. Those two timestamps are therefore
the two halves of the cycle being written, not a repair of it:

| time (UTC) | write | by |
|---|---|---|
| 12:35:15 | `f-S5S5…`.parent := `wi1z1…` | a run ~36 min earlier |
| 13:12:09 | `wi1z1…`.parent := `f-S5S5…` | this job, 36 s in — the loop closes |
| 13:25:06 | — | this job's own reindex dies on it |

So it took two runs: one edge from an earlier sync, the closing edge from the run that then walked
into it.

Both usages carry `sector_key` 6, so a different sector created them. Sector 3's HierarchySync only
rewired them, which is precisely `rewireProjectParents`' remit — it rewires every identifier-matched
project usage whatever sector owns it — and precisely the write site that had no cycle guard.

The rows have since been repaired by hand, both parents repointed to genus *Prunus*, with a raw
`UPDATE` of `parent_id` alone. That is why they still carry the `modified` timestamps above.

## Upstream trigger: 15 usages that all parse to `Prunus domestica`

Found by the data team, see checklistbank#1725. ArchisBotany
(`data-archis-botany/taxonomy.txtree`, source dataset 53677, merged into the XRelease that is this
sector's source) records informal accession groupings as synonyms of the form `Prunus domestica <n>`:

```
Prunus domestica subsp. insititia [subspecies]
  =Prunus domestica 1 [infraspecific_name]
  Prunus domestica insititia (Gro-5b) [infraspecific_name]
    =Prunus domestica 6 [species]
  Prunus domestica insititia (Gro-6) [infraspecific_name]
    =Prunus domestica 6/12 [species]
```

The name parser discards the trailing token, so dataset 53677 ends up with **15 usages whose
`scientificName` is exactly `Prunus domestica`**, every one of them `type=scientific`: the real
accepted species plus 14 synonyms sitting under its own descendants. (`5a` and `5b` fare worse - they
come through as `Prunus domestica` with authorship `a` and `b`.) Nothing flags the dropped token; the
only issues raised are `duplicate name`, `synonym rank differs` and `missing authorship`.

That is what misdirects the rewire. Resolving the source ancestor `Prunus domestica` for
`Prunus domestica subsp. insititia` can land on one of those synonyms, and `resolveToAccepted` then
maps it to its accepted parent - `Prunus domestica insititia (Gro-6)`, a *descendant* of the real
species. So the 13:12:09 write pointed a parent at its own grandchild. The 12:35:15 write, `(Gro-6)`
under `subsp. insititia`, was correct all along.

The guards below stop this from being fatal, not from being wrong: with them the second rewire is
refused and logged, and the usage keeps its existing parent. Getting the placement *right* needs the
names to stay distinct - either the source drops these synonyms or expresses them as cultivars (the
same file already uses `Prunus domestica cv. pershore`), or CLB stops silently collapsing them onto a
real taxon's name.

## Root cause

`NameUsageProcessor.addClassification()` walked `parent` links upward with no cycle guard and no depth
limit. On a cycle A ↔ B where A is served from the `taxa` object cache (`new SimpleName(...)`) and B
from the usage cache (`SimpleNameCached`), every turn appended one of each. Both caches are MapDB +
Kryo, so each `get` deserialises a *fresh* object graph with fresh Strings — which is why 383 M Strings
were retained while `NameUsageWrapper` does not appear in the histogram at all. It ran ~47.5 million
turns before the heap was gone.

`UsageCache.addParents()`, the sibling walk, already had exactly this guard.

## Where the cycle came from

`HierarchySync` writes `parent_id` in five places. Four called `wouldCreateCycle(...)`.
`rewireProjectParents()` did not — it only rejected a direct self-loop. That pass rewires accepted
project usages under *other matched accepted* project usages, so rewiring X→Y and later Y→X each pass
the self-loop check individually and together close a 2-cycle. `SectorRunnable.execute()` then runs
`updateSearchIndex()` in the same job, walking straight into it.

The `(dataset_key, parent_id)` foreign key means a cycle can never be inserted, only closed by an
`UPDATE` — which is what that pass does.

Repetition was self-sustaining: `ExitOnOutOfMemoryError` killed the JVM, `SyncManager` cancelled the
stale job row on restart, and `SyncSchedulerJob` then rescheduled the still-outdated sector against
unchanged data.

## What changed

1. `NameUsageProcessor.addClassification()` keeps a visited-id set and stops at
   `MAX_CLASSIFICATION_DEPTH` (100), logging both cases and keeping the partial classification rather
   than throwing — one bad subtree must not fail a whole reindex. This protects every caller of the
   processor, so `indexDataset` and `index --all` are covered too, not just `indexSector`.
2. `HierarchySync.rewireProjectParents()` calls the existing `wouldCreateCycle(...)` before each
   `updateParentId`, and counts blocked moves as `cycle-blocked` in its summary log.
3. `NameUsageMapper.getClassification` and `getClassificationSN` had the identical unguarded walk,
   one DB round trip per hop. A cycle would have hung whichever request thread hit them, so they got
   the same visited-set guard and a warning.

`UsageMatcherStore.addParents` and `UsageCache.addParents` were already guarded and were left alone.
The SQL classification walks (`TaxonMapper.classification*`) need nothing: they are recursive CTEs
built on `UNION`, not `UNION ALL`, so the working table empties once rows repeat and Postgres ends
the recursion on its own.

## Deliberately not done

- **No fail-fast on cycles.** Truncating and warning keeps a whole release indexable when one subtree
  is broken. A cycle is a data bug to be fixed in the data; the indexer's job is to survive it.
- **No database-level cycle constraint.** Postgres cannot express it cheaply, and the FK already
  guarantees referential integrity — cycles are a semantic problem, not a referential one.
- **No change to the sync scheduler.** The crash loop was a symptom; with the walk bounded, a
  rescheduled sync is no longer fatal.
- **The two production usages were not repaired as part of this change** — that data fix was applied
  by hand on 2026-09-02 (both parents repointed to genus *Prunus*).
- **No sweep for other parent cycles in existing projects.** With the walks bounded, a cycle no longer
  kills anything; it still yields a truncated classification, so one is worth running, but it is a data
  job rather than part of this fix.
