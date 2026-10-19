# GBIF compatible species match

CLB answers GBIF's v2 species match API, the one of
[gbif/matching-ws](https://github.com/gbif/matching-ws) behind `https://api.gbif.org/v2/species/match`,
so a client of that API can be pointed at CLB instead. `GbifSpeciesMatcher` runs it on top of a persistent usage
matcher - the same matcher, interpretation and higher rank fallback as `/match/nameusage` - and only translates
request and response. Three servers offer it:

| Server | Path | Resource |
|---|---|---|
| `WsMatchingServer` | `/match/gbif`, for its single dataset | `GbifSpeciesMatchResource` |
| `WsServer` | `/dataset/{key}/match/gbif` | `DatasetGbifMatchResource` |
| `WsBundleServer` | `/match/gbif`, rewritten to `/dataset/{releaseKey}/match/gbif` | `DatasetGbifMatchResource` |

```
GET /match/gbif?scientificName=Bacillus&kingdom=Bacteria&verbose=true
GET /match/gbif/metadata
GET /dataset/3LXR/match/gbif?scientificName=Bacillus&kingdom=Bacteria
GET /dataset/3LXR/match/gbif/metadata
```

None of them serves GBIF's own `/v2/species/match` path. Should CLB ever replace matching-ws behind the GBIF API,
the proxy in front (varnish) maps that path to `/match/gbif`.

The dataset scoped path only serves a dataset with a persistent matcher store, which every published dataset above
`matching.pgMatcherThreshold` usages has. Other than `/match/nameusage` it neither falls back to matching against
live postgres data nor schedules a store build, as it is meant for occurrence scale traffic: every project, small
datasets and most private datasets answer 404. Names are only looked up in the names index, never added to it.

## Request
| Parameter | |
|---|---|
| `scientificName`, `scientificNameAuthorship`, `taxonRank`, `code` | the name to match, interpreted like `/match/nameusage`. `authorship` and `rank` are accepted as aliases |
| `verbatimTaxonRank` | used as the rank if `taxonRank` is blank |
| `genericName`, `specificEpithet`, `infraspecificEpithet` | joined into the name if `scientificName` is blank. Without `genericName` the `genus` of the classification completes epithets |
| `kingdom` … `species` | the classification, all ranks of `Classification` are bound |
| `usageKey`, `taxonID` | look a usage up by its id instead of matching a name |
| `strict` | the `STRICT` matching mode of `/match/nameusage`: no higher rank fallback and a strict taxonomic group veto, see below. Otherwise `RELAXED` |
| `verbose` | include the alternatives the matcher considered |
| `checklistKey` | matching server only, optional; a key other than the server's configured `gbifChecklistKey` is a 400. The dataset scoped path ignores it, the path names the dataset |

The name is built from its pieces like matching-ws does (`NameAssembler`, a port of its `NameNRank`): a given
`scientificName` is used as is, an abbreviated or missing genus (`P. concolor`, `? concolor`) is expanded with the
`genus` of the classification or `genericName`, and a missing authorship is appended. A `scientificName` that is
only an epithet, or none at all, is assembled from the genus, the classification's `subgenus` and `species`, and the
epithets. Without any of these the lowest taxon of the classification is matched with its rank, as GBIF does; with no
classification either the answer is NONE. Epithets without any genus cannot be matched themselves, so only their
classification is, as a HIGHERRANK match like GBIF's, and not at all with `strict`. `/match/nameusage` and matching
jobs do the same unless the mode is STRICT.

A classification that puts the name into another taxonomic group than a usage (`TaxGroup`) rules the usage out in
STRICT mode, as it does in GBIF's strict mode. RELAXED, the default, only prefers usages of a compatible group, and
like GBIF matches plankton filed under `kingdom=Animalia` or lichens under `Plantae`. That is for a bi- or trinomial
given with nothing more specific than a kingdom, and for any name between the overlapping protists and animals,
protists and fungi, or fungi and plants. A different authorship always rules a usage out, and a more specific group
keeps its veto. Merges never relax groups. See [2026-10-15-group-veto.md](2026-10-15-group-veto.md).

Ranks above the genus match within their major group, Osteichthyes as a class matches the parvphylum. Infraspecific
ranks stay strict, as subspecies, variety and form of one name are often different taxa, unless the name does not
show its rank: a trinomial without a rank marker given with a rank, `Anas platyrhynchos domesticus` with
`taxonRank=variety`, matches a usage of another infraspecific rank, the form, in RELAXED mode. A usage of the given
rank still wins. Merges keep all infraspecific ranks apart. See #1659.

A genus followed by an infrageneric epithet in brackets, `Chaetoceros (Hyalochaete)` or `Chaetoceros (Hyalochaete) spp.`,
is a name below that genus even without a rank marker, so it gets the vague rank `INFRAGENERIC_NAME`. The names index
only knows the epithet, which the name shares with the plant genus *Hyalochaete*. A genus therefore never matches,
only a subgenus, section or other rank below the genus. Failing that, the RELAXED higher rank fallback takes the genus
the name was given in. That includes a subgenus that is a genus elsewhere: `Lasioglossum (Dialictus)` gives
*Lasioglossum*, not the genus *Dialictus*, as it already did with `taxonRank=subgenus`.

An authorship cited with the work the name was published in, `X in Y`, is the name of X and is also cited by Y alone:
*Pipistrellus kuhlii* (Natterer in Kuhl, 1817) matches *Pipistrellus kuhlii* (Kuhl, 1817), in merges and stable ids
too. The years have to agree within one year then, as Y may have named the same epithet himself later on, and two
names published in the same work are told apart by their own authors.
The parser keeps `Y` as `Authorship.inAuthors`. See #1652.

Homonyms are not guessed. What tells candidates apart is evidence only:
- an authorship equal to the query's, with or without a rank;
- the classification;
- the name itself over its misapplications;
- a single accepted name among synonyms, or synonyms that all belong to one accepted name, provisional usages aside;
- the spelling, where a query without a subgenus ignores the candidates' subgenus, so `Aphis nerii` is the accepted
  `Aphis (Aphis) nerii`.

If that leaves several candidates, a usage is only picked if they are duplicates of one name: all have an authorship,
none compare different and none belong to disparate groups. Otherwise the candidates are an unresolved homonym,
answered as NONE with the candidates as alternatives, or in RELAXED mode by the higher rank fallback. A bare
`Navicula`, `Ciliophora` or `Tetilla` therefore matches nothing, and `Urtica dioica dioica` the species. See #1651.

A supplied rank that contradicts the name - a subspecies or a genus sent as `SPECIES`, common in GBIF occurrences -
keeps the matcher from finding it. Provisionally, when the first match is neither EXACT nor VARIANT and the rank
contradicts the name - the parser reads another rank from a binomial or trinomial, or a single word comes with a
species or lower rank - the name is matched again without the supplied rank. The second answer is taken if it
is EXACT or VARIANT, or if the first found nothing, and `diagnostics.note` says `taxonRank <rank> ignored`.
A `strict` match never retries.

`gbifChecklistKey` in the matching server config defaults to the `gbifKey` of the matching dataset. A release
usually has none - the COL checklist key `7ddf754f-d193-4cc9-b351-99906754a03b` belongs to the project - so set it
explicitly. The metadata of the dataset scoped path report the dataset's own `gbifKey`.

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
3. `gbif_impact.py match download.zip --min-occurrences 1000` calls `/match/gbif` for every row and writes
   the stored and the CLB result side by side. It is resumable. The `/metadata` of both services, with the
   ChecklistBank dataset key of the release they index, is kept in `proposed.meta.json`.
4. `gbif_impact.py report proposed.tsv --out report` puts every row into a category - from `DIFF_KINGDOM` down to
   `SAME` - weighted by occurrences, and writes `diff.tsv` with debug links to both services plus `report.html`.
   The report opens with the release both services matched against, and links every COL id to its taxon page.
5. `gbif_impact.py verify report/diff.tsv --top 500` re-asks live api.gbif.org without ids. Passing the result to
   `report --verify` replaces the stored interpretation for those rows, telling stale or id based GBIF matches
   from real name matching differences.
