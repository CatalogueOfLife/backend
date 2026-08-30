# Where the Elasticsearch index disk actually goes

Date: 2026-08-30
Status: shipped

## Problem

`438cc268a` set `index.codec: best_compression` on the name usage index. Reindexing dev gave:

```
clbdev-2026-07-21   159,997,976 docs (+8,922,787 deleted)   180.5gb
clbdev-best         159,328,532 docs (0 deleted)            116.1gb
```

**35.7% smaller**, of which ~5.3 points is purging the deleted docs the old index carried, so the codec
itself is worth ~32%. Occurrences at GBIF saw ~70% from the same change, so the obvious next question was
where the rest of our disk sits and whether more of it can go.

`_disk_usage` on both indices, with the non-stored buckets scaled for the deleted-doc difference:

| bucket | before | | after |
|---|---:|---|---:|
| stored fields (`_source`) | 109.1 GB — **60%** | → | ~48.7 GB — **42%** |
| inverted index | 49.0 GB — 27% | → | ~46.4 GB — **40%** |
| doc_values | 20.9 GB — 12% | → | ~19.8 GB — 17% |
| points + norms | 1.3 GB | → | 1.2 GB |

`index.codec` only compresses stored fields; it does nothing for doc_values or the inverted index. Stored
fields duly fell 53%, in line with occurrences. The difference is the mix: this index is unusually
index-heavy, so the bucket the codec can reach was only 60% of it to begin with. That is the whole
explanation for 36% vs 70%, and it means the remaining savings live in the mapping, not in `_source`.

The schema has **no** `"store": true` fields, so "stored fields" here is `_source` and nothing else.

## Goals

1. Find the remaining savings that cost no behaviour change at all.
2. Give a defensible number to size the new CLB test environment against
   ([gbif/infrastructure#57](https://github.com/gbif/infrastructure/issues/57)).
3. Say plainly what is *not* available, so nobody re-derives it later.

## Non-goals

- Any change to search results, facets, or ranking. Latency changes were also treated as out of scope.
- Sharding, routing, or splitting the index across several indices (that is issue #563).

## What shipped

Every mapped field was audited against `FilterTranslator`, `FacetsTranslator`, `SortByTranslator`,
`QTranslator` and `QSuggestTranslater`. The decisive finding is that `FieldLookup` is a single `EnumMap`
consumed by *both* the filter and the facet translator, and lines 57-64 assert every
`NameUsageSearchParameter` is mapped. **Every mapped search field is therefore simultaneously filterable
and facetable**, so essentially all doc_values are load bearing. Only these were not:

| change | fields | saving |
|---|---|---:|
| `index: false, doc_values: false` | `usage.sectorKey`, `usage.name.sectorKey`, `usage.sectorMode`, `usage.name.sectorMode` | 0.39 GB |
| `index: false, doc_values: false` | the four `combinationAuthorship`/`basionymAuthorship` arrays | 1.00 GB |
| `doc_values: false` | `usage.name.authorship` | 0.51 GB |
| `index: false` | `usage.statusOrder` | 0.04 GB |

All eight of the first two rows exist purely to feed `copy_to`. `sectorKey` was being indexed three times
over (top level, `usage.`, `usage.name.`), and the four authorship arrays all copy into
`usage.name.author`, which is the field the `AUTHORSHIP` facet actually uses. `copy_to` still fires when
the source has `index: false` — verified against ES 9.3.1, not assumed.

Total **~1.9 GB, about 1.7%**. Modest, and that is the honest headline: the codec was the win.

## For sizing

`clbprod-2026-08-20` predates the codec commit. Applying the dev ratio:

```
457.8gb primary / 915.9gb with replica   →   ~294gb primary / ~587gb with replica
```

~164 GB primary, ~330 GB total, freed by the next prod reindex. Plan a prod-sized index at **~300 GB
primary**, and do not budget for further schema savings — there are only 3-4% left.

## Rejected: `_source` excludes

Five fields are forced into the document by `EsModule` purely so the mapping can index them —
`usage.label`, `usage.statusOrder`, `usage.nameFields`, `usage.name.alphaIndex`,
`usage.name.scientificNameNormalized`. None has a setter, so nothing reads them back and Jackson discards
them on load, the same reasoning already applied to `SimpleNameMixIn`. Excluding them from `_source` looked
free and was briefly shipped in `5a751d57a`.

It was reverted in `3f1dd7807`. **An update rebuilds a document from its `_source`, so a field the mapping
indexes but `_source` does not carry is silently dropped the moment anything updates that document.**
Measured on 9.3.1 — after a partial update touching only `classification`:

```
usage.label queryable BEFORE = 1
usage.label queryable AFTER  = 0     ← silently destroyed
scientificName (in _source)  = 1     ← survived
```

This applies to `_update`, `_update_by_query` and `_reindex` alike. The excludes are therefore only safe in
an index where no document is ever updated in place. That happened to be true while `ClassificationUpdater`
was a no-op stub, which is exactly why the trap was invisible — and `39fa8deb7` reintroduces in-place
updates deliberately, so the excludes cannot come back.

**This was measured and given up, not overlooked.** Worth ~1-3 GB, and less than the raw byte count
suggests: `usage.label` is `scientificName + " " + authorship` and `scientificNameNormalized`/`alphaIndex`
are near-duplicates of `scientificName`, all present verbatim in the same document, so DEFLATE already
collapses them. Recovering them would mean recomputing the five fields in an ingest pipeline, duplicating
the Java name-formatting logic in painless. Not worth it for 1-3%.

## Rejected: trimming the prefix index

The largest single field on disk is now an auto-generated sub-field:

```
usage.name.scientificName.search._index_prefix    22.4 GB   19.3% of the index
```

The whole `search_as_you_type` family is 26.6 GB, 23% of the index, and being inverted index the codec does
nothing for it. It is not dead weight: profiling the real query shapes shows `match_phrase_prefix`
(`QTranslator:149`, `QSuggestTranslater:12/35`) rewrites straight onto `._index_prefix`.

Two things about how it works that are easy to get wrong:

- The prefixes are built over **2-word shingles and unigrams**, not single terms and not the whole name. A
  query for `Abies a` becomes the single term `_index_prefix:abies a` — a 7-character prefix of the shingle
  `abies alba`, not a 1-character prefix of `alba`.
- The switch onto the prefix field is driven by **token count, not character length**. One-token queries
  (`A`, `Ab`, `Abies`) never touch it and use the root `.search` field.

`search_as_you_type` hardwires `min_chars: 1, max_chars: 20`. Measured on a 200k synthetic binomial corpus,
using an explicit `text` + `index_prefixes` mapping (which cannot even express 20 — ES caps it below):

| | prefix field | vs baseline |
|---|---:|---:|
| `min 1, max 19` (≈ current) | 14.96 MB | — |
| `min 3, max 19` | 14.06 MB | −5% |
| `min 1, max 12` | 7.71 MB | −40% |
| `min 3, max 12` | 6.79 MB | −45% |

`min_chars` is nearly free but buys almost nothing — short prefixes are high-frequency and delta-encode to
near zero. **`max_chars` is the entire lever**, worth ~9 GB, and it is the one that hurts: at 12,
`Abies alba s` is already past the boundary and falls back to a slower path. Results stay identical, but
the regression lands on exactly the queries autocomplete users type. Declined.

`._2gram` (2.5 GB) appears unused — neither profiled query shape touches it. Extracting it means replacing
`search_as_you_type` with a hand-rolled `text` field plus a shingle+edge-ngram analyzer, because
`match_phrase_prefix` only auto-rewrites onto `_index_prefix` for the built-in type. A core-search-path
rewrite for 2.1% was not worth it.

## Rejected: doc_values on high-cardinality id fields

`classification.id` (6.3 GB), `usage.name.id` (2.1 GB), `id` (1.3 GB) and `usage.name.publishedInId`
(1.2 GB) hold ~10.9 GB of doc_values between them, and a terms facet over any of them is meaningless. But
all four are in `FieldLookup`, so the REST API accepts `facet=TAXON_ID` and friends from clients. Dropping
their doc_values is an API change, not a free win, and was out of scope.

## Outcome

- `5a751d57a` mapping trim, `3f1dd7807` the `_source` revert, `39fa8deb7` the `ClassificationUpdater` fix.
- Net ~1.9 GB on dev. The real number is the prod reindex: ~164 GB primary.
- Verified with 31 tests against real ES 9.3.1 (`ClassificationUpdateTest`, `NameUsageIndexServiceEsTest`,
  `NameUsageRoundTripIT`, `NameUsageSearchServiceEsIT`, `NameUsageSuggestionServiceEsIT`).
- Two unrelated observations made during the audit, neither acted on:
  - Search sets no `_source` filter at all (`SearchRequestTranslator:45-64`), so every hit ships its full
    classification, decisions and vernacular names. Only suggest filters (`SuggestRequestTranslator:17-19`),
    and coarsely — `usage.*` pulls far more than `ResponseConverter` consumes.
  - Faceting on `PROJECT_KEY` or `DECISION_MODE` builds a plain `terms` agg over the `nested` `decisions`
    field with no enclosing `nested` agg (`FacetsTranslator:65-74`), so it silently returns no buckets. No
    server-side caller requests those facets, but the REST API accepts them from clients.
