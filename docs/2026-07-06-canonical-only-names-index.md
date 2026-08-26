# Canonical-only names index

**Date:** 2026-07-06
**Component:** `dao/src/main/java/life/catalogue/matching/nidx/`, `core/src/main/java/life/catalogue/matching/UsageMatcher.java`
**Status:** shipped in v1.4.0 (prod 2026-08-20)

> Distilled from the implementation plan; this item never had a separate design doc.

## Problem

The names index was two-tiered: a canonical entry (rank `UNRANKED`, no authorship) plus
rank/author-specific child entries beneath it. Every consumer therefore had to "resolve to
canonical" before it could group or compare, and the specific tier doubled the store size while
being used almost entirely as a shortcut for "same authorship + rank".

## Goal

Collapse the two tiers into a single canonical layer, so every name matches directly to one
canonical index entry and no code has to resolve to canonical first.

## Design

The index keeps only canonical entries. Every `name_match.index_id` points straight at a canonical
entry, so `IndexName.canonicalId == key` for all rows. Usage matching and merge-sync keep working
because `UsageMatcher` already retrieves candidates by canonical id and re-derives authorship and
rank from the live usages; the four places that used the specific-layer id as a proxy for "same
authorship + rank" are rewritten to compare live authorship directly. `IdProvider` drops its
now-inert specific-nidx scoring term.

### Behaviour decisions

These are settled design choices that the whole change assumes.

1. **One entry per canonical name.** `NameIndexImpl.add()` inserts exactly one row, always canonical
   (`rank=UNRANKED`, authorship cleared, `canonical_id` self-referencing its own id). No child /
   specific rows are ever created.
2. **`match()` ignores authorship and rank.** A parsed name matches on its normalized canonical
   string only. `MatchType` is `EXACT` when the query's normalized canonical equals the index
   entry's, `VARIANT` when they differ only by unicode/punctuation normalization, `NONE` otherwise.
   `MatchType.CANONICAL` is no longer produced by the index.
3. **`canonicalId == key` invariant.** In phase 1 the `names_index.canonical_id` column is retained
   but every row self-references, which keeps all existing `getCanonicalId()`-based grouping
   (UsageMatcher store, IdProvider, ReleasedIds) working unchanged. Phase 2 drops the column.
4. **Homonym separation moves fully to live authorship.** `UsageMatcher` no longer uses
   `namesIndexId` equality as a proxy for "same authorship + rank". It compares authorship via the
   existing `AuthorComparator` and rank via the live usage ranks (which `filterCandidates` already
   does through `ranksDiffer`).
5. **Retire, don't preserve, the authored-name registry surface.** `NamesIndexResource.match()`,
   `NamesIndexResource.export()`, `NidxExportJob` and `byCanonical` group browsing are deleted in
   phase 2. Checklistbank's `NameIndex` UI that consumes them is out of scope for this repo.

### Phasing

- **Phase 1** — single-tier index + matcher / id-provider rewire. A behaviour change, fully
  testable, and it delivers the store shrink on its own.
- **Phase 2** — mechanical cleanup: remove the redundant SQL joins, drop the `canonical_id` column,
  delete the retired nidx browse/export endpoints.

Phase 1 is independently shippable before phase 2, so the risky semantic change can be validated in
isolation.

## Constraints

- The change rode the `feature/name-parser-v4` branch, which already mandated a full names-index
  rebuild + rematch, so no separate migration release was needed.
- The public `NameMatch` / `IndexName` JSON field names that remain (`id`, `canonicalId`) must not
  change — external clients (the Checklistbank Name page) still read `namesIndexId` / `canonicalId`
  off name usages. `canonicalId` simply always equals `id` now.
- `NameIndexFactory.passThru()`, `.fixed()` and `.build()` signatures must remain — GBIF `taxon-ws`
  depends on `passThru()`.

## Outcome

- Checklistbank UI rework and the GBIF `taxon-ws` behaviour heads-up were out of this repo's scope
  and tracked as separate tickets.
- Open item confirmed during the column drop: the exact auto-generated index names on `names_index`
  had to be read off `\d names_index` before writing the Liquibase drop.
