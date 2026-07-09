# bareauthorship

Regression test for the bare-name merge candidate gate in `TreeMergeHandler.acceptNameThrowsNoCatch`.

Since the names index refactor, `NameMapper.listByNidx` returns every authorship variant sharing a
canonical, not just one. The `src` merge sector's usages are converted into bare names (no usage,
name only) via the "bare name" sector-note hack, so they are only processed through
`SectorSync.processBareNames()` -> `TreeMergeHandler.acceptName()`.

Per the merge spec, a bare name is only merged onto a target candidate when ALL of the following hold:

1. the incoming bare name has authorship (an unauthored bare name is skipped outright - it can
   never disambiguate between authorship variants), and
2. exactly one candidate remains after filtering `listByNidx` candidates to those with **identical
   rank** and `AuthorComparator.compare(incoming, candidate) == Equality.EQUAL`.

This is strict: `compare(authored, unauthored)` is `Equality.UNKNOWN`, not `EQUAL`, so an authored
bare name does **not** enrich an unauthored candidate either - that combination is a skip too.

Six scenarios, sharing one target/source pair (genus `Aus`):

- **Unauthored incoming, ambiguous target (`Aus bus`)**: the target carries two authorship variants,
  `Aus bus Mill.` and `Aus bus Linn.`. The incoming bare name `Aus bus` has no authorship, so it is
  skipped before candidates are even filtered (rule 1). Both target variants are left untouched.

- **Authored incoming, no candidate survives (`Aus cus`)**: the target carries an unauthored
  `Aus cus` and an unrelated authored decoy `Aus cus Linn.` - both share the same canonical id
  post-refactor. The incoming bare name `Aus cus Mill.` has authorship, but neither candidate
  survives the filter: `compare(Mill., <unauthored>)` is `UNKNOWN` (not `EQUAL`), and
  `compare(Mill., Linn.)` is `DIFFERENT`. Zero candidates remain, so the merge is skipped and
  `Aus cus` stays unauthored. (Before this gate existed, `listByNidx`'s ambiguity alone made this a
  skip for the wrong reason; under the current owner-decided strict-EQUAL gate it is *still* a skip,
  now precisely because an authored name cannot resolve against an unauthored candidate.)

- **Resolvable merge (`Aus dus`)**: the target carries a single candidate `Aus dus Mill.` with no
  `publishedInId`. The incoming bare name `Aus dus Mill.` has identical rank (species) and authorship
  `Equality.EQUAL`, so exactly one candidate remains and the merge proceeds. The incoming name carries
  a `PUB` reference (`dusRef`, defined in `src.bib`) that the existing candidate lacks, so
  `TreeMergeHandler.updateName` copies the reference into the target dataset and sets
  `Name.publishedInId` - the enrichment is not tree-visible (references aren't printed in the text
  tree), so it is asserted directly against `NameMapper`/`ReferenceMapper` in
  `bareauthorshipValidate()`.

- **Authorship mismatch, single candidate (`Aus eus`)**: the target carries a single candidate
  `Aus eus Linn.`. The incoming bare name `Aus eus Mill.` has the same rank but
  `compare(Mill., Linn.) == DIFFERENT`, so the one candidate is filtered out, zero remain, and the
  merge is skipped. `Aus eus Linn.` is left unchanged. Unlike the first cut of this fixture, the
  incoming `Aus eus Mill.` also carries a `PUB` reference (`eusRef`, defined in `src.bib`) that the
  target lacks, so this case is no longer vacuous: if the `EQUAL`-authorship clause were ever loosened
  (e.g. relaxed to `!= Equality.DIFFERENT`), the candidate would wrongly survive the filter and the
  merge would enrich `Aus eus Linn.` with `eusRef`'s `publishedInId` - `bareauthorshipValidate()`
  asserts that reference is never picked up.

- **Rank mismatch, single candidate (`Aus fus`)**: the target carries a single candidate
  `Aus fus Mill.` at rank `SPECIES`. The incoming bare name is also `Aus fus Mill.` - identical
  genus/specificEpithet, so it shares the same canonical nidx bucket (the canonical form ignores rank,
  see `NameFormatter.canonicalName`) - but is tagged rank `SPECIES_AGGREGATE` (`[aggregate]`, i.e.
  "Aus fus agg."). Authorship is `Equality.EQUAL`, but `c.getRank() == n.getRank()` is
  `SPECIES != SPECIES_AGGREGATE`, so the one candidate is filtered out, zero remain, and the merge is
  skipped. The incoming name carries a `PUB` reference (`fusRef`) that the target lacks; if the
  `c.getRank() == n.getRank()` clause were ever dropped, the merge would wrongly fire and enrich
  `Aus fus Mill.` with `fusRef`'s `publishedInId` - `bareauthorshipValidate()` asserts that never
  happens. This is the only scenario in this fixture that exercises the rank clause: the earlier
  scenarios all pair same-rank candidates, so before this addition the rank check had zero coverage.

See `bareauthorshipValidate()` in `SectorSyncMergeIT` for explicit assertions independent of tree
print ordering.
