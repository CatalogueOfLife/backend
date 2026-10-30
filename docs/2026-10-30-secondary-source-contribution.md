# What a source contributed to a release

Date: 2026-10-30
Status: implemented on `feat/source-contribution`, not yet deployed. DDL before the deploy, one backfill after it, see
`dbschema.md`. UI changes in checklistbank and portal-components go with it.

## Problem

Source metrics only count records a sector owns. In COL26.10 XR (316691) BHL showed no names and 1,337 references, yet
324,695 usages credit it, nearly all of them for a page link on the reference of another source. BioNames showed 27,456
references and is credited on 201,310 usages. Neither was listed as a source of the release at all.

37,763 datasets are credited as secondary sources of that release and 27,801 of them had no `dataset_source` row, not even
with `inclPublisherSources=true`: BHL, BioNames, TPL and IPNI Literature, whose merge sectors only sync references, and
about 27.8k articles of sector publishers. 60% of a sample of those articles had also lost their sector.

Three things did not agree with each other:

- The source list asked for an owned `name_usage`, the sector copy (`SectorMapper.hasData`) for any owned record.
- `hasData` ignored `verbatim_source_secondary`, so a sector that only enriched was dropped while its credits stayed.
- `secondarySourceByInfoCount` of a sector or source is what its records *received*. Nothing reported what it *gave*.

## Decision

**A release lists a source if it owns usages, or if it contributed in any other way and is not covered by a sector
publisher.** `listContributingSources` does that and only `ProjectRelease.finalWork` calls it. `hasData` also counts a credit
now, for merge and hierarchy sectors, which are the modes that write them, and also not for articles of sector publishers.

**Contributions are counted once when a release finishes** and kept in `dataset_source_contribution`, one row per source
with `enriched` (distinct records) and `by_info` (credits per info group). Source metrics then report them as `enrichedCount`
and `enrichedByInfoCount`. Summed over all sources, `by_info` equals the `secondarySourceByInfoCount` of the release.

**`PUBLISHED_IN` is split into three info groups**: `PUBLISHED_IN` for the reference including a DOI or URL added to it,
`PUBLISHED_IN_PAGE` and `PUBLISHED_IN_PAGE_LINK`. A source is credited only for what it changed. Each group still has a
single source, and a later source only takes over the groups it changed.

## Why not

- **Counting live when metrics are read.** The source list is already slow, and `/source?inclPublisherSources=true` was
  truncated in prod before (#1675). The metrics page asks for every source separately, and `type` is not in the index, so
  each read of BHL would need about 325k heap fetches. Nothing that serves a request reads the secondary table: the project
  source list and `/source/count` run the same SQL as before, and a release's list reads `dataset_source`.
- **Counting at sync time in `sector_import`.** One source per group means a later source can replace a credit, so the
  numbers go stale. It would also need a `sector_key` on `verbatim_source_secondary`, which only knows the source dataset.
- **Listing every article of a sector publisher.** It is the cleaner rule, but `dataset_source` would grow from 19,434 to
  about 47k rows and the already 48 MB `inclPublisherSources` response with it, for sources that mostly enrich a few names.
  Their contribution is reported by their publisher.
- **One `PUBLISHED_IN_PAGE` group for page and link.** Pages come from many sources and links from few: of 300 sampled names
  each, all World Plants names have a page and none a link, all BHL names have both. A combined group hides who gave links.

## Consequences

- XR creators grow, because `addSourceAuthors` is on and only `ARTICLE` sources are excluded. BHL adds "BHL Staff and
  Community", TPL and IPNI Literature add theirs if they have any. BioNames has no creators.
- Existing `PUBLISHED_IN` rows cover reference, page and link together and cannot be split. An XR is rebuilt in full, so
  the next one is clean. Project rows change as their sectors sync again.
- Published releases get their contribution from the backfill, but keep their `dataset_source`, so BHL only joins the
  source list with the next XR.
- Projects report no contribution. Reading it would need the very count this avoids, and the sectors in question only
  produce data inside an XR.
- `enrichedCount` of a publisher sums its sources, so a record credited to two of its articles counts twice.

## Left out

- Contribution per sector. The sector sync page stays as it is. A source with both merge and other sectors reports it on
  its merge row only, as nothing tells the sectors apart.
- A source with several merge sectors keeps all of them in a release once it is credited, even one that gave nothing.
- Hierarchy sectors with a project subject store their authorship credit under the release they read, while the source is
  listed under the project, so it does not show up in `enrichedCount`.
