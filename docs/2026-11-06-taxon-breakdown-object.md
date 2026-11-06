# The taxon breakdown decides its own chart

Date: 2026-11-06
Status: implemented on `feat/breakdown-tree`, not yet deployed. The clients (portal-components, checklistbank) still
call the legacy array and move to the object afterwards.

## Problem

`GET /dataset/{key}/taxon/{id}/breakdown` returns a nested array of the taxon's descendants at the next one or two
Linnean ranks, each counted at a rank. It already counts through the taxon metrics for every dataset that has them and
through elastic only for projects. But the chart clients, `TaxonBreakdown` in portal-components and in checklistbank
and the Taxon component's includes table, fire a `nameusage/search?TAXON_ID=…&facet=rank` before every call, only to
decide what to ask for: whether to count species or genera (genus when genera outnumber species twofold, #1584), which
rank below the taxon has any members and so forms the first ring, whether a second ring is worth drawing (not when
its rank is at or below the count rank, nor above a thousand members), and the subtree total that sizes the pie and
the "Other / Unknown" slice. Every taxon page view therefore cost one or two faceted searches, which is what let a
page crawler saturate elastic on 2026-11-06 (see `2026-11-06-facets-in-query-context.md`).

The `api_logs` of 30 days showed who calls the endpoint: 331k calls from a headless browser rendering ChecklistBank
taxon pages, about 30k from people and crawlers rendering ours or embedded portal-components, and no script at all.
All but 26 of them pass `level`; the ChecklistBank UI also passes `countRank`.

## Decision

- Called without any parameter the endpoint returns a `TaxonBreakdown` object: the taxon as a `SimpleName`, its
  accepted descendants by rank (`counts`, from `TaxonMetrics.taxaByRankCount` or one rank facet search for projects),
  the `countRank` and `ranks` decided by `BreakdownRules` with the rules above, the `total` at the count rank and the
  `children` tree. Every node is a `SimpleName` unwrapped with a `count` and its `children`; a node without an id is
  the remainder at its rank, the parent's counted descendants placed in no node of that ring. A node of the count rank
  counts itself: the metrics only count descendants, so an order holding nothing but species, charted as its species,
  would otherwise be all zeros and one big remainder. A taxon with nothing to count comes back with a null
  `countRank`, no ranks and no children.
- Called with `level` or `countRank` the endpoint returns the array it always did, so every existing client and every
  pinned portal-components version keeps working. The array goes once the clients have moved.
- The rules are pure functions in `BreakdownRules`, unit tested; `TaxonDaoIT.taxonBreakdown` covers the object on the
  species test tree including both remainders, the species-only ring and the project case.

## Non goals

- Changing the clients. portal-components and checklistbank move to the bare call in their own repos; the includes
  table reads `counts` from the same response and drops its facet search too.
- The dataset level `/dataset/{key}/breakdown` by taxonomic group, which is a different thing and untouched.

## Rejected

- A new path or a `shape` parameter for the object. The parameter rule needs no client to learn a new URL, and the
  logs showed no bare caller to break.
- Keeping the per rank count property name (`"species": 41`) in the object. `countRank` says it once; a fixed `count`
  lets a client stay rank agnostic.
