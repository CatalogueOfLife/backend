# The taxon breakdown decides its own chart

Date: 2026-11-06
Status: implemented on `feat/breakdown-tree`, not yet deployed. A breaking API change: the clients (portal-components,
checklistbank) move to the object before the deploy, and the two known external users of the endpoint were told.

## Problem

`GET /dataset/{key}/taxon/{id}/breakdown` returned a nested array of the taxon's descendants at the next one or two
Linnean ranks, each counted at a rank. It already counted through the taxon metrics for every dataset that has them and
through elastic only for projects. But the chart clients, `TaxonBreakdown` in portal-components and in checklistbank
and the Taxon component's includes table, fired a `nameusage/search?TAXON_ID=…&facet=rank` before every call, only to
decide what to ask for: whether to count species or genera (genus when genera outnumber species twofold, #1584), which
rank below the taxon has any members and so forms the first ring, whether a second ring is worth drawing (not when
its rank is at or below the count rank, nor above a thousand members), and the subtree total that sizes the pie and
the "Other / Unknown" slice. Every taxon page view therefore cost one or two faceted searches, which is what let a
page crawler saturate elastic on 2026-11-06 (see `2026-11-06-facets-in-query-context.md`).

The `api_logs` of 30 days showed who calls the endpoint: 331k calls from a headless browser rendering ChecklistBank
taxon pages, about 30k from people and crawlers rendering ours or embedded portal-components, and no script at all.

## Decision

- The endpoint returns a `TaxonBreakdown` object, always: the taxon as a `SimpleName`, its accepted descendants by
  rank (`counts`, from `TaxonMetrics.taxaByRankCount` or one rank facet search for projects), the `countRank` and
  `ranks` decided by `BreakdownRules` with the rules above, the `total` at the count rank and the `children` tree.
  Every node is a `SimpleName` unwrapped with a `count` and its `children`; a node without an id is the remainder at
  its rank, the parent's counted descendants placed in no node of that ring. A node of the count rank counts itself:
  the metrics only count descendants, so an order holding nothing but species, charted as its species, would otherwise
  be all zeros and one big remainder. A taxon with nothing to count comes back with a null `countRank`, no ranks and
  no children. Genera and below answer 404 as before.
- `countRank` stays as an optional override of the rule; `level` is gone, the backend picks the rings.
- The array shape and the `childrenBreakdownPrinter`/`childrenBreakdownCollector` methods behind it are removed.
  `JsonTreePrinter` itself stays, it prints whole datasets.
- The rules are pure functions in `BreakdownRules`, unit tested; `TaxonDaoIT.taxonBreakdown` covers the object on the
  species test tree including both remainders, the species-only ring, the override, the 404 and the project case.

## Non goals

- Changing the clients here. portal-components and checklistbank move to the object in their own repos; the includes
  table reads `counts` from the same response and drops its facet search too.
- The dataset level `/dataset/{key}/breakdown` by taxonomic group, which is a different thing and untouched.

## Rejected

- Keeping the array behind `level` or `countRank` and returning the object only for a bare call. It would have broken
  nobody, but a resource whose shape depends on a query parameter is odd, and the known users can upgrade.
- A new path for the object. Same reason, and nothing to protect.
- Keeping the per rank count property name (`"species": 41`) in the object. `countRank` says it once; a fixed `count`
  lets a client stay rank agnostic.
