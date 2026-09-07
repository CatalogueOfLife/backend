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
passes. Left out deliberately, and the third measurement below argues it should stay out: once the
extension passes are batched the core tree traversal is ~94% of the data phase, so Amdahl caps what
overlapping the rest can buy, and this is the riskiest change in the plan.

**`NameUsageKeyMap` still holds one entry per usage** for the whole ColDP job. Translating name id
to usage id in the NameRelation and TypeMaterial queries would remove it, at the cost of transient
fields on two more models.

**MD5 is still a second read of the archive.** Measured at 0.3s for 59 MB; not worth the plumbing.

**`UNION` in the recursive tree CTEs was left alone.** It looks like pointless dedup, but on a tree
with a parent cycle that dedup is what makes the query terminate.

## Outcome

### Second measurement, dev, 2026-09-07

A DwC-A extended export of the Lepidoptera subtree of dataset 37384 (iBOL) on dev, with the new
code. It is not a before/after — different dataset, format and environment from the first
measurement — but it is a filtered export (`root` was set), so it does exercise the new
scan-and-filter path.

| pass | records | time | share of data phase |
|---|---|---|---|
| Taxon | 325,378 | 59.1s | 24.4% |
| Multimedia | 1,365,149 | 3:02.8 | 75.5% |
| VernacularName | 0 | 0.064s | 0.0% |
| Distribution | 0 | 0.023s | 0.0% |
| MeasurementOrFact | 0 | 0.016s | 0.0% |
| **data** | | **4:02.0** | |
| metadata | | 0.3s | |
| bundling, 8 files, 4 threads, level -1, 59 MB | | 6.5s | |
| size + MD5 | | 0.5s | |
| **total** | | **4:08.8** | |

The passes account for 242.018s of the 242.040s data phase, so nothing is hiding between them.

**This run exposed a flaw in the scan-and-filter fix.** iBOL has 10,063,402 media rows for 2,032,208
usages, and the Lepidoptera subtree is 325,378 of those usages (16%). The Multimedia pass therefore
scanned all 10.06M rows to keep 1,365,149 — **7.4× more than it needed** — at 55,000 rows/s. At the
~5,600 queries/s the first measurement showed, the per-id loop it replaced would have been about 58s
against 182.8s. For this shape the change is a regression, because `SCAN_THRESHOLD` keys off the
number of ids and never off what fraction of the dataset they are. See the third measurement.

**Bundling is noise** at 6.5s of 4:09, and there is no visible benefit from the parallel writer:
8 entries with one holding most of the bytes means the big entry is deflated by a single thread
anyway, and the scatter store adds a copy. For an archive with one dominant entry the lever that
would work is `job.zipLevel`, not `job.zipThreads`.

**Latent, not yet hit:** `Media` and `Distribution` still resolve `dc:source` through the bounded
citation cache, which is the same pattern that was fixed for usages. This dataset has 1.37M media
rows and did not suffer, so those rows evidently carry no reference id — but a media or distribution
heavy dataset that does cite references would hit exactly the old behaviour. The fix is the same
join, in the media and distribution export queries.

### Third measurement, dev, 2026-09-07 — the headline

A ColDP extended export of the Lepidoptera subtree of the COL XRelease 310362 on dev. This one is a
near like-for-like with the first measurement: both exported **exactly 608,276 usages**.

| | prod 316165, before | dev 310362, after |
|---|---|---|
| **total** | **26:02.0** | **3:41.0** |
| data phase | 25:51.8 | 3:17.2 |
| NameUsage | 608,276 in 5:24 | 608,276 in 1:57 (3×) |
| VernacularName | 87,630 in 2:17 | 93,398 in 27.9s (5×, on 7% more rows) |
| Distribution | 30,126 in 2:08 | 34,901 in 38.6s (3×, on 16% more rows) |
| Reference | 53,356 in 24.8s | 50,674 in 13.3s (2×) |
| the six empty passes | 55s – 3:14 each | 6 – 25 ms each |
| metadata | 4.3s | 18.9s |
| bundling | 5.9s / 59 MB | 4.9s / 40 MB |

**7.1× overall.** Caveats: different dataset and environment, and the dev XRelease happens to hold
no name relations and no type material where prod had 158,793 and 107,847 — excluding those two
passes it is 6.1×.

The metadata phase is now larger than bundling. It writes one YAML per source dataset of the
XRelease, so it is dataset-dependent rather than a regression, but it is the next thing that will
surface.

### What is still on the table

The scan reads far more than it keeps, and the cost does not shrink with the size of the download —
someone exporting a single genus of the COL XRelease pays the same:

| run | pass | kept / scanned | cost |
|---|---|---|---|
| 310362 | Distribution | 34,901 / 2,327,051 = 1.5% | 38.6s |
| 310362 | VernacularName | 93,398 / 1,990,292 = 4.7% | 27.9s |
| 310362 | Reference | 50,674 / 1,734,879 = 2.9% | 13.3s |
| 37384 | Multimedia | 1,365,149 / 10,063,402 = 13.6% | 182.8s |

That is 80s of the 197s data phase spent on rows that are discarded. Batched id fetches read only
what is wanted, at roughly 120 queries per entity rather than 393,007, and would retire
`SCAN_THRESHOLD` altogether.

That also settles the parallel-passes question the other way. Multimedia looked like 75% of the
iBOL data phase only because it was scanning 10M rows to keep 1.4M; once the extension passes are
batched the core tree traversal is ~94% of what is left, and there is little to overlap it with.
