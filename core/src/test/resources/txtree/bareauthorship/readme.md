# bareauthorship

Regression test for the bare-name merge candidate filter in `TreeMergeHandler.acceptNameThrowsNoCatch`.

Since the names index refactor, `NameMapper.listByNidx` returns every authorship variant sharing a
canonical, not just one. The `src` merge sector's usages are converted into bare names (no usage,
name only) via the "bare name" sector-note hack, so they are only processed through
`SectorSync.processBareNames()` -> `TreeMergeHandler.acceptName()`.

Two scenarios, sharing one target/source pair:

- **Ambiguous** (canonical `Aus bus`): the target already carries two authorship variants,
  `Aus bus Mill.` and `Aus bus Linn.`. The incoming bare name `Aus bus` (no authorship) cannot be
  used to disambiguate, so both variants must be left untouched (skipped, not merged).
- **Resolvable** (canonical `Aus cus`): the target carries an unauthored `Aus cus` and an unrelated
  authored decoy `Aus cus Linn.` - both share the same canonical id post-refactor. The incoming bare
  name `Aus cus Mill.` carries authorship that matches the first candidate and clearly differs from
  the decoy, so after filtering candidates by authorship exactly one remains and the merge proceeds,
  adding `Mill.` authorship to the previously unauthored usage.

Before the authorship filter was added, `listByNidx` returned both `Aus cus` and `Aus cus Linn.` as
candidates for the `Aus cus Mill.` bare name, so the `candidates.size() == 1` gate incorrectly
treated this as ambiguous too and skipped the merge.

See `bareauthorshipValidate()` in `SectorSyncMergeIT` for explicit authorship assertions independent
of tree print ordering.
