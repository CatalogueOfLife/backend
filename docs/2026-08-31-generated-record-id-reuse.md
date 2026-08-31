# Reapplying generated record identifiers across imports

Date: 2026-08-31
Status: shipped

Closes [#1189](https://github.com/CatalogueOfLife/backend/issues/1189).

## Problem

The importer mints identifiers for records a source does not identify itself, and those identifiers were
**positional**, so they changed on every re-import. Two distinct cases:

**Implicit records.** A denormalised classification, or DwC-A rows referencing a parent, accepted or basionym
name that has no record of its own, make the importer materialise the missing taxa. `IdGenerator` picks a
prefix unoccupied by the source's own ids (preferring `x`) and appends an incrementing `LATIN29` counter.
`Normalizer.applyDenormedClassification()` iterates `store.usages().allKeys()`, which is MapDB hash order over
the set of source ids, so changing that set reordered the whole thing and every implicit taxon came out with a
different id.

**TextTree without explicit IDs.** `TxtTreeInterpreter` gives every name `String.valueOf(tn.id)` and every
usage `coalesce(uid, String.valueOf(tn.id))`, where `tn.id` is the **line number**. Inserting a line near the
top of the file renumbered everything below it. These records carry `Origin.SOURCE`, so the origin filter used
for implicit records does not reach them.

The damage was real: deep links into the CLB API broke for GBIF hosted portals such as the Legumes Portal, and
because sectors and decisions in projects like COL are pinned to a source usage id, plant sectors were swapped
onto the wrong taxon.

## Design

`PreviousIds` streams the previous version of the dataset out of Postgres — which is still there for the whole
of `Normalizer.call()`, since `PgImport.call()` deletes it only as its very first statement — and hands those
ids back when the importer is about to generate one.

Records are keyed on **rank, scientific name and authorship**, normalised through
`StringUtils.digitOrAsciiLetters`, the same folding `IdProvider.matchScore` uses for the release id mapping (see
[XRELEASE.md](XRELEASE.md) for that separate mechanism). When several previous records share a key — homonymous
genera in different families, the case behind the swapped plant sectors — the **scientific name of the parent**
picks the right one. The parent is always known in time: `Normalizer.applyClassification()` walks
`Classification.RANKS` top down and `TxtTreeInserter` descends depth first, so a record's parent is stored
before the record itself. Remaining duplicates are handed out smallest previous id first, which is the oldest,
mirroring `ScoreMatrix.ReleaseMatch.NATURAL_ORDER`.

Reuse happens **at generation time**, inside `ImportStore.createNameAndUsage()`, not as a post-pass. That is
possible because every producer of a generated record runs after the complete source record set is already in
the store — the denormalised classification in `Normalizer.normalize()`, the DwC-A implicit usages in the second
pass over the taxon core — so a plain occupancy check against the two MapDB maps is enough. **This ordering is
load-bearing.** If a generated record were ever created before the source pass finished, a reused id could
collide with a source id read later, and it would be the source record that got dropped as not unique.

### Why the reservation set is not optional

The id counter restarts at 0 on every import, so the first minted id is always `x3` (`LATIN29.encode(1)`) —
exactly the id the previous version's first implicit record holds. A genuinely new implicit taxon created before
the old one is revisited would take `x3`, the old record would then find its id occupied, and the ids would keep
churning regardless. `PreviousIds` therefore also holds a flat set of **every** previously generated id, name
and usage ids alike, and `CRUDStore.create()` skips a reserved id when minting. The same set makes a TextTree
line number give way when another record has reclaimed it by name.

This is the least obvious part of the change and the most likely to be "simplified" away later.

### TextTree

`TxtTreeInterpreter` used to throw away the one fact needed here by coalescing the explicit `ID` info item with
the line number. It now records `TxtUsage.generatedId`, and `TxtTreeInserter` passes it to
`ImportStore.createNameAndUsage(nu, generatedId)`. The resulting order of preference for a TextTree usage is:

1. an explicit `ID=` info item, exactly as before;
2. the id the same name had in the previous version;
3. the line number, as before — unless it is already taken or reserved, in which case an id is minted, so a
   record can never displace one that is being reclaimed.

The name id is always derived from the line number in TextTree, even when the usage carries an explicit `ID`.
`TxtTreeInserter` therefore restores it by exact usage id match through `ImportStore.previousNameId()`, and when
a *new* record's line number is one another name is about to reclaim, it drops the placeholder and inherits the
explicit usage id instead - which is stable by construction, so it does not shift again either.

Because a TextTree identifies nothing of its own, its previous ids are read regardless of origin
(`includeSource`), unlike every other format where only `origin != SOURCE` is loaded.

## Non-goals and known gaps

- **Bare names.** A generated bare name has no `name_usage` row, so it cannot appear in the lookup;
  `reusePreviousIds` bails on `isBareName()` rather than silently consuming a candidate.
- **The `~` prefix on DwC-A `VERBATIM_*` records.** They are minted during `insertData()`, before
  `ImportStore.updateIdGenerators()` switches the prefix to `x`, so they reach Postgres with the initial `~`
  prefix. Deliberately left alone: changing the prefix would churn every one of those ids exactly once, which is
  the opposite of what this change is for.
- **Origin flips.** A family that gains an explicit source row loses its generated id, correctly, and the
  reverse gets a fresh one. Both are genuine changes in the source.
- **No index on `name_usage.origin`.** A large dataset with no generated records still scans its partition slice
  on every import. Tracked as [#1569](https://github.com/CatalogueOfLife/backend/issues/1569), with a TODO at the
  query in `NameUsageMapper.xml`; measure before adding a partial index.
- **Memory.** For TextTree every previous usage is loaded, not just the generated ones. Roughly 150-200 MB for a
  million record tree, held for the length of normalization.

## Rejected alternatives

- **A post-normalization re-keying pass**, like `MapStore.updateTmpIds()` does for references. It would have to
  rewrite `parentId`, `nameID`, `proParteAcceptedIDs`, `basionymID`, `usageIDs` and every `RelationData` in the
  MapDB store. Generating the right id in the first place is far cheaper and cannot leave dangling references.
- **Feeding the previous ids into `IdGenerator.smallestNonExistingPrefix`.** That method compares `id.charAt(0)`
  rather than the character after the prefix, so the prefix would grow by one character on every import.
- **Offsetting the shared counter past the previous maximum** instead of reserving. Subtle to get right across
  two live prefixes (`~` and `x`), and it would not help TextTree at all.
- **Passing the lookup through the `Normalizer` constructor.** It creates an ordering contract — load before
  `insertData()`, or the DwC-A and TextTree sites silently miss — of exactly the kind that breaks quietly.
  `ImportStoreFactory` owns it instead, so the store holds it for its whole life.
- **Reusing the usage id when only the name id is taken.** Would keep the API visible id stable slightly more
  often, at the cost of a subtler code path. Both ids are required to be free.
- **A per dataset `Setting` to switch it off.** This is a bug fix, and nothing churns on the first import after
  deploy: reapplying the previous ids reproduces exactly what is already published.

## Outcome

Shipped as designed. Two integration tests in `PgImportStableIdsIT` cover both cases and were both confirmed to
fail without the feature: the DwC-A fixture pair `dwca/59` / `dwca/59b` moved `KINGDOM|Fungi` from `x33` to `xS`
and `GENUS|Aster|Poaceae` from `xZ` to `x3C`, and the TextTree pair `txtree/8` / `txtree/8b` moved
`FAMILY|Asteraceae` from `2` to `5`.

Three integration tests, one per case, all confirmed to fail without the feature:

| test | fixtures | what shifted before |
|---|---|---|
| `implicitTaxaKeepTheirIds` | `dwca/59`, `dwca/59b` | `ORDER\|Agaricales` `x39` -> `xZ` |
| `textTreeKeepsItsIds` | `txtree/8`, `txtree/8b` | `FAMILY\|Asteraceae` `2` -> `5` |
| `textTreeWithExplicitIdsKeepsNameIds` | `txtree/9`, `txtree/9b` | name id of `GENUS\|Aster` `3` -> `6` |

One thing worth recording for anyone writing a similar test: a benign edit to a small DwC-A fixture — dropping
one row and adding two — did **not** reshuffle the MapDB hash order, so the first version of the test passed
against unfixed code. The fixture only became a real reproduction once the source ids were renumbered as well.

The explicit-ID TextTree case also caught a second-order bug in review: restoring a name id is not enough on its
own, because a brand new record sitting on the reclaimed line number takes it first. The same reservation
reasoning as for minted ids applies, one level down.
