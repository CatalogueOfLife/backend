# GBIF compatible species match

The DB-free `WsMatchingServer` also answers GBIF's v2 species match API, the one of
[gbif/matching-ws](https://github.com/gbif/matching-ws) behind `https://api.gbif.org/v2/species/match`,
so a client of that API can be pointed at a CLB matching server instead.
It is served by `GbifSpeciesMatchResource` on top of the server's single persistent usage matcher -
the same matcher, interpretation and higher rank fallback as `/match/nameusage` - and only translates
request and response.

```
GET /v2/species/match?scientificName=Bacillus&kingdom=Bacteria&verbose=true
GET /v2/species/match/metadata
```

## Request
| Parameter | |
|---|---|
| `scientificName`, `authorship`, `rank`, `code` | the name to match, interpreted like `/match/nameusage` |
| `genericName`, `specificEpithet`, `infraspecificEpithet` | joined into the name if `scientificName` is blank |
| `kingdom` … `species` | the classification, all ranks of `Classification` are bound |
| `usageKey`, `taxonID` | look a usage up by its id instead of matching a name |
| `strict` | no higher rank fallback when the name itself does not match |
| `verbose` | include the alternatives the matcher considered |
| `checklistKey` | optional; a key other than the server's configured `gbifChecklistKey` is a 400 |

`gbifChecklistKey` in the server config defaults to the `gbifKey` of the matching dataset. A release usually has
none - the COL checklist key `7ddf754f-d193-4cc9-b351-99906754a03b` belongs to the project - so set it explicitly.

## Response
The matching-ws JSON: `usage`, `acceptedUsage`, `synonym`, `classification` (root first, ending with the usage or,
for a synonym, its accepted name) and `diagnostics` with `matchType`, `confidence`, `note`, `timeTaken` and
`alternatives`. Usage keys are CLB usage ids.

| CLB match type | GBIF `matchType` | `confidence` |
|---|---|---|
| EXACT | EXACT | 100 |
| VARIANT | VARIANT | 98 |
| CANONICAL | EXACT | 90 |
| AMBIGUOUS, a usage was picked | EXACT or VARIANT of the picked usage | 75 |
| AMBIGUOUS, unresolved homonym | NONE | 75 |
| HIGHERRANK | HIGHERRANK | 50 |
| NONE, UNSUPPORTED | NONE | 100 |

Confidences are fixed per match type, the scores of the OpenRefine reconciliation. Whenever the GBIF type differs
from the CLB one, `diagnostics.note` says `clb matchType=<type>`. Alternatives carry a match type but no confidence.
CLB statuses map to GBIF ones: `PROVISIONALLY_ACCEPTED` and `BARE_NAME` are `DOUBTFUL`, `AMBIGUOUS_SYNONYM` is
`PROPARTE_SYNONYM`.

## Not supported
- Fuzzy matching. The names index is stemmed, so `Abies albus` or `Quercus robor` match, but a typo such as
  `Puma concolar` falls back to the genus where GBIF matches `Puma concolor`.
- The `exclude`, `scientificNameID` and `taxonConceptID` parameters.
- IUCN `additionalStatus`, the nested set `left`/`right` values and the parsed name parts of a usage.
