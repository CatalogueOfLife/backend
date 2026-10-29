# A nomenclature module for the pure name logic

Date: 2026-10-29
Status: merged 2026-10-29 in PR [#1683](https://github.com/CatalogueOfLife/backend/pull/1683), not deployed. Issue
[#1682](https://github.com/CatalogueOfLife/backend/issues/1682) stays open for step 3, the homotypic grouping planner.

## Problem

Much of the nomenclatural logic takes names and relations and returns a decision, with no database behind it. But it
lived in `dao` and `core`, next to the jobs that apply it. Reusing it meant depending on `dao`, which brings
Elasticsearch, Chronicle, MyBatis, Hikari, Postgres, MapDB, Kryo, mail and image libraries along. Two projects pay for
that today:

- **matching-ws** (gbif) depends on all of `dao` for four classes: `Equality`, `AuthorComparator`,
  `ScientificNameSimilarity` and `StringSimilarity`. It also uses `parser` (`RankParser`).
- **Blixa**, the ColDP editor, deliberately did not pull these in and reimplemented them:
  - `HomotypyDetector` is a "BasionymSorter-lite" that compares author keys as exact strings.
  - `ConsolidationService` is an analogue of `HomotypicConsolidator`.
  - `HomotypicRelations` copies `NomRelType.isHomotypic`.
  - `NameMatcher` is "upgradeable to AuthorComparator later".
  - 23 validation rules, several of them carrying CLB issue codes.

  Blixa now also wants the name relation cleanup of CatalogueOfLife/data#1815.

## What was done

A module `nomenclature` that depends on `api` and `parser` only. The chain is
`api → parser → nomenclature → dao → core → importer → webservice`. Classes kept their packages, so callers did not
change, and only `dao` declares the new dependency.

**Moved** (with their tests):
- `basgroup`: `NameRelationRules`, `BasionymSorter`, `HomotypicGroup`
- `matching`: `Equality`, `NameValidator`, `TaxGroupAnalyzer`, `NameIdentity`, `RankComparator`, `PublishedInIdentity`
- `matching.authorship`: `AuthorComparator`, `YearComparator`, `LongestCommonSubstring`, `SuffixArray`
- `matching.similarity`: all nine string similarity classes

**New:**
- `basgroup.HomotypicRules` holds the spelling decisions of the homotypic grouping that used to be static helpers of
  `HomotypicConsolidator`: the same name, an orthographic variant, a duplicate.
- `basgroup.NameRelationJudge<R>` is generic over the caller's relation type. `judge(relations, names)` groups the
  relations by pair of names. `judgePair(n1, n2, relations)` collapses exact duplicates, judges a pair that holds
  several distinct relations or a spelling correction, and maps the verdicts back to the caller's relations.
  `NameRelationCleaner` and `Normalizer.flagRelationConflicts` both used to do this by hand.
  - The cleaner keeps loading the candidates, limiting them to a subtree, deleting and flagging.
  - The importer keeps its walk name by name, so it never holds all relations of a dataset at once, and only flags.

**Parser slimmed:** its `kryo` dependency was dead (an unused import in `AreaParser`), and `gbif-api` was used only by
a test. The kryo dependency is gone and gbif-api is test scope, so parser now brings nothing beyond `api` except
name-parser-rust. `dao` declares `gbif-api` itself, for `NameMapper`.

**BasionymSorter:** it took an issue consumer it never called. The parameter is gone.

## Decisions and rejected alternatives

- **On api and parser, not api alone** as #1682 proposed. An import graph of all main classes showed that every
  valuable class an api-only module would leave behind is blocked by `parser` alone: `NameIdentity`
  (parseAuthorship), `TaxGroupAnalyzer` (TaxGroupParser), `NameValidator` (NomCodeParser), and the name
  interpreter. Both consumers carry the native parser already: matching-ws through `parser`, Blixa through
  `NameParserRust` directly.
- **No `NomenclaturalName` view.** #1682 proposed a minimal interface instead of `Name`. It would have extended
  `FormattableName`, about twenty getters with `Rank` and `Authorship` objects. Blixa's own model has an Integer id
  and String ranks and authorships, so it would end up building an api `Name` anyway, and Blixa already uses `Name`.
  The rules keep taking `Name`.
- **The name lookup goes to `judge()`, not the constructor.** The cleaner can only load names once it has narrowed the
  relations to a subtree.
- **Not into `parser`.** That would avoid a module but mix string parsing with nomenclatural decisions.
- **Not into `api`.** Anything that needs parser would make a cycle, and every client of the model would carry the
  logic.
- **Not the `/parser/homotypic` API.** It exists, but would make a remote call per edit for an editor, and covers
  neither the relation rules nor validation.

## Not moved, on purpose

- **The interpretation layer**: `NameInterpreter`, `InterpreterUtils`, `ExtinctName` and `NameAssembler`. They are
  pure apart from the parser and would replace Blixa's `NameParserService`/`ParsedNameMapping` and matching-ws's
  `NameNRank`. They can follow once one of them wants it.
- **`UsageMatcher` and its stores.** They need a names index and a session to load from. They would be a separate
  matching module some day.
- **CLB-only logic**, pure but of no use elsewhere: `IdCandidate`/`ReleasedIds`, `SectorSettingsResolver`,
  `NameBlocklist`, `ParentStack`, `MatchedParentStack`, `ChangedMatcher`.
- **`SyncNameUsageRules`**, applied to every usage by the sector sync's `TreeBaseHandler`, its only caller. It was
  moved at first and moved back. Only two of its rules come from the codes: a botanical autonym has no authorship, and
  a zoological trinomial of indeterminate rank is a subspecies. The rest is sync policy: accepted manuscript names and
  original spellings become provisionally accepted, all-caps uninomials are recapitalised, and local identifiers are
  dropped. If an editor wants the two code rules, they can become a small class of their own in `nomenclature`.
- **The homotypic grouping planner**, step 3 of #1682. It would take the decisions out of `HomotypicConsolidator`
  and leave the consolidator to execute a plan. It is the larger refactoring and stays open.

## Outcome

- All 125 moved tests run and pass in the new module, as many as the `@Test` methods counted before the move. On top
  come 3 tests for `HomotypicRules`, moved out of `HomotypicConsolidatorTest`, and 5 for `NameRelationJudge`.
- The unit tests of the whole reactor pass, and so do `NameRelationCleanerIT`, the `HomotypicConsolidator*IT`s and
  `NormalizerColdpIT`. The full IT suite was not run locally.
- `feat/person-author-comparison` rewrites `AuthorComparator` and adds `AuthorMatcher`, `StringAuthorMatcher`,
  `AuthorContext`, `AuthorTeam` and a `PersonStore` with its memory implementation. All of these are pure and belong
  in `nomenclature/` when that branch next merges master. Only `PgPersonStore`, `PersonTables` and `PersonFiles` stay
  in `dao`.
