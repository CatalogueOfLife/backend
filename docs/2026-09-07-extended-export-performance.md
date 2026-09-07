# Extended COLDP and DwC-A export performance

Date: 2026-09-07
Status: shipped, except the parallel entity passes (see Not done)

## Problem

The extended COLDP and DwC-A downloads of large releases took hours. They are both what
`ColReleaseExportJob` regenerates after every COL release and what a user gets from the download
button in ChecklistBank.

Nothing had ever measured where the time went, so the first change was instrumentation: a duration
and record rate per entity pass, plus the export, metadata, bundling and MD5 phases.

## What the first measurement said

A partial (subtree) XRelease export, dataset 316165, 608k usages, 2026-09-07:

| phase | time | share |
|---|---|---|
| data | 25:52 | 99.4% |
| metadata | 0:04 | 0.3% |
| bundling (59 MB archive) | 0:06 | 0.4% |
| size + MD5 | 0:00.3 | 0.02% |

and inside the data phase:

| entity | records | time |
|---|---|---|
| NameUsage | 608,276 | 5:24 |
| NameRelation | 158,793 | 2:47 |
| TypeMaterial | 107,847 | 3:07 |
| VernacularName | 87,630 | 2:17 |
| Distribution | 30,126 | 2:07 |
| TaxonProperty | **0** | 3:14 |
| SpeciesEstimate | 4 | 1:56 |
| SpeciesInteraction | **0** | 1:50 |
| Media | **0** | 1:48 |
| Treatment | **0** | 1:22 |
| TaxonConceptRelation | **0** | 0:55 |
| Reference | 53,356 | 0:25 |

Five passes exported nothing at all and cost eleven minutes between them. That is the shape of a
per-id fetch loop, not of a scan, and it pointed straight at the filtered export path.

## What was done

**Basionyms resolved in the export query.** Both exporters ran
`nameRelMapper.listByType(name, BASIONYM)` inside the loop over usages — one query per exported
usage, for data the NameRelation pass streams wholesale anyway. The three archive export queries
denormalise it now; the subquery reads only the dataset's BASIONYM rows, so it is built once and
hash joined. `DISTINCT ON` keeps a name with two basionym relations from multiplying the usage.
It yields both the related name id and a usage id of that name, because ColDP writes `basionymID`
as a usage and DwC-A writes `originalNameUsageID` as a name.

**DwC-A reference citations joined in.** DwC-A writes citations inline where ColDP writes ids, and
looked each up through a 10k entry cache. On a release whose references are mostly cited once that
cache never stood a chance, so it degenerated into two reference lookups per usage. Opt in via
`inclCitations`, since ColDP would only discard those wide columns.

**Filtered exports scan instead of fetching each id.** The dominant cost above. Above
`ArchiveExport.SCAN_THRESHOLD` collected ids, each pass streams the entity once and filters in
Java; below it the per-id fetches are kept, because a small subtree of a huge dataset is still
better off asking for what it needs.

**`NameUsageKeyMap`.** `containsUsageID` was a `containsValue` scan over a map with one entry per
usage, called per bare name — quadratic. It is a real index now, built only when bare names are
actually exported. Misses are remembered, so relations pointing at a bare name no longer re-run the
same fruitless query.

**No long-lived session.** `ArchiveExport` held one open for its whole run to back the citation
cache. With nothing querying per usage it would now sit idle in a transaction across the entire
core pass, where prod's `idle_in_transaction_session_timeout = 15min` would cut it down.

**Parallel bundling.** commons-compress `ParallelScatterZipCreator`, with `job.zipThreads` and
`job.zipLevel` as config. The level default is unchanged, so archives keep their current size.
Entries come out in completion order rather than directory order.

**One release export job.** TEXT_TREE, COLDP and DWCA all block on the release's dataset lock. As
three jobs only one won it; the others were rejected, resubmitted and slept on a worker thread with
an escalating backoff of up to five minutes a try, holding down two of the three prod job threads.
They run in sequence under one lock now, each format judged on its own so one failure does not cost
the release its other downloads.

**Printer streaming.** `AbstractPrinter` opened its session with autocommit on, and the postgres
driver ignores `setFetchSize` in that mode — so TextTree, the simple exports and the tree exports
all buffered their whole result set into heap despite the mappers' `fetchSize`.

## Not done

**The entity passes still run one after another.** The plan had them running concurrently on their
own connections, which needs the `writer` field turned into a per-pass local threaded through every
`write` method in both exporters, a thread safe `SectorInfoCache`, and cancellation across the
passes. It was left out deliberately: the measurement above says the core NameUsage pass dominates
what remains once the per-id fetches are gone, so Amdahl caps what parallelising the rest can buy,
and it is the riskiest change in the plan. Worth revisiting against a second measurement.

**`NameUsageKeyMap` still holds one entry per usage** for the whole ColDP job. Translating name id
to usage id in the NameRelation and TypeMaterial queries would remove it, at the cost of transient
fields on two more models.

**MD5 is still a second read of the archive.** Measured at 0.3s for 59 MB; not worth the plumbing.

**`UNION` in the recursive tree CTEs was left alone.** It looks like pointless dedup, but on a tree
with a parent cycle that dedup is what makes the query terminate.

## Outcome

Re-measure a comparable subtree export and a full XRelease export with the same instrumentation and
record the numbers here. Expected from the measurement above: the five empty passes collapse to a
scan each, the NameUsage pass loses its per-usage basionym query, and DwC-A additionally loses two
reference lookups per usage.
