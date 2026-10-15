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
| `scientificName`, `scientificNameAuthorship`, `taxonRank`, `code` | the name to match, interpreted like `/match/nameusage`. `authorship` and `rank` are accepted as aliases |
| `verbatimTaxonRank` | used as the rank if `taxonRank` is blank |
| `genericName`, `specificEpithet`, `infraspecificEpithet` | joined into the name if `scientificName` is blank. Without `genericName` the `genus` of the classification completes epithets |
| `kingdom` … `species` | the classification, all ranks of `Classification` are bound |
| `usageKey`, `taxonID` | look a usage up by its id instead of matching a name |
| `strict` | no higher rank fallback when the name itself does not match |
| `verbose` | include the alternatives the matcher considered |
| `checklistKey` | optional; a key other than the server's configured `gbifChecklistKey` is a 400 |

The name is built from its pieces like matching-ws does (`NameAssembler`, a port of its `NameNRank`): a given
`scientificName` is used as is, an abbreviated or missing genus (`P. concolor`, `? concolor`) is expanded with the
`genus` of the classification or `genericName`, and a missing authorship is appended. A `scientificName` that is
only an epithet, or none at all, is assembled from the genus, the classification's `subgenus` and `species`, and the
epithets. Without any of these the lowest taxon of the classification is matched with its rank, as GBIF does; with no
classification either the answer is NONE. Epithets without any genus give NONE.

A supplied rank that contradicts the name - a subspecies or a genus sent as `SPECIES`, common in GBIF occurrences -
keeps the matcher from finding it. Provisionally, when the first match is neither EXACT nor VARIANT and the rank
contradicts the name - the parser reads another rank from a binomial or trinomial, or a single word comes with a
species or lower rank - the name is matched again without the supplied rank. The second answer is taken if it
is EXACT or VARIANT, or if the first found nothing, and `diagnostics.note` says `taxonRank <rank> ignored`.
A `strict` match never retries.

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
| AMBIGUOUS, unresolved homonym | NONE | none |
| HIGHERRANK | HIGHERRANK | 50 |
| NONE, UNSUPPORTED | NONE | none |

Confidences follow the match type, the scores of the OpenRefine reconciliation. Above genus the matcher accepts a
usage of another rank in the same major group (Osteichthyes asked for as a class, held as a parvphylum); such a
match loses 2 points if both ranks share their major rank (subclass and class) and 5 otherwise. Whenever the GBIF type differs
from the CLB one, `diagnostics.note` says `clb matchType=<type>`. Alternatives carry a match type but no confidence. A query with exactly the usage's name is
EXACT, as for GBIF, whatever the authorship says and with or without a subgenus, although the CLB matcher calls
it a VARIANT of the full label. GBIF reads a VARIANT as a fuzzy match of a differently spelled name, so only
spelling variants that share a stemmed names index entry (Euphorbia peplis, peplus) stay VARIANT.
CLB statuses map to GBIF ones: `PROVISIONALLY_ACCEPTED` and `BARE_NAME` are `DOUBTFUL`, `AMBIGUOUS_SYNONYM` is
`PROPARTE_SYNONYM`.

## Not supported
- Fuzzy matching. The names index is stemmed, so `Abies albus` or `Quercus robor` match, but a typo such as
  `Puma concolar` falls back to the genus where GBIF matches `Puma concolor`.
- The `exclude`, `scientificNameID` and `taxonConceptID` parameters.
- IUCN `additionalStatus`, the nested set `left`/`right` values and the parsed name parts of a usage.

## Impact comparison with GBIF
`webservice/py/gbif_impact.py` compares this service with GBIF's matching-ws on real GBIF occurrence names, in the
spirit of GBIF's pre-backbone-release impact reports. It compares name matching only: no `taxonID`,
`scientificNameID` or `taxonConceptID` is ever sent.

1. A GBIF SQL download (`SQL_TSV_ZIP`) of every distinct verbatim name and classification, with the stored
   interpretation and an occurrence count:
   ```sql
   SELECT v_kingdom, v_phylum, v_class, v_order, v_superfamily, v_family, v_subfamily, v_tribe, v_subtribe,
     v_genus, v_subgenus, v_scientificname, v_scientificnameauthorship, v_taxonrank, v_verbatimtaxonrank,
     v_genericname, v_specificepithet, v_infraspecificepithet, v_nomenclaturalcode,
     v_taxonid, v_scientificnameid, v_taxonconceptid,
     taxonkey, acceptedtaxonkey, kingdomkey, phylumkey, classkey, orderkey, familykey, genuskey, specieskey,
     scientificname, acceptedscientificname, taxonrank, taxonomicstatus,
     ARRAY_JOIN(taxonomicissue, ';') AS issues, COUNT(*) AS occurrences
   FROM occurrence
   GROUP BY <every column above but the count>
   ```
   SQL downloads support no `HAVING`, so occurrence thresholds are applied locally. The id columns only flag rows
   GBIF may have matched by id.
2. A `WsMatchingServer` for the release GBIF indexed (`matchingServerBuild --key <release>`, then `server`).
3. `gbif_impact.py match download.zip --min-occurrences 1000` calls `/v2/species/match` for every row and writes
   the stored and the CLB result side by side. It is resumable.
4. `gbif_impact.py report proposed.tsv --out report` puts every row into a category - from `DIFF_KINGDOM` down to
   `SAME` - weighted by occurrences, and writes `diff.tsv` with debug links to both services plus `report.html`.
5. `gbif_impact.py verify report/diff.tsv --top 500` re-asks live api.gbif.org without ids. Passing the result to
   `report --verify` replaces the stored interpretation for those rows, telling stale or id based GBIF matches
   from real name matching differences.
