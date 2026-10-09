# Simplifying name interpretation, measured on the ChecklistBank corpus

Date: 2026-10-09
Status: phase 0 (the corpus harness) implemented on `feat/interpreter-corpus`. Phases 1 and 2 not yet implemented.

## Why

`NameParser` (parser module) wraps the rust name parser and turns its `ParseResult` into a `ParsedNameUsage`.
`NameInterpreter` (core) turns the three ways a name comes in - fully atomised, name plus authorship, or a single
string - into a `Name`. Since the one-go name and authorship parse (026d4849d, 93f0e2cb0) and the 5.x rust binding, the
wrapper repeats much of what the parser does by now, and some of it works against it:

- `NameParser.parse(Name)` hands name **and** authorship to the parser, which already parses the separate authorship,
  decides between it and an authorship inside the name string (the separate one wins), moves sic/corrig into
  `originalSpelling`, splits off notes and appends the authorship to an informal name's phrase. The wrapper then
  parses the authorship a second time (`parseAuthorshipIntoName` → `parseAuthorship`, a full parse of
  `"Abies alba " + authorship`), overwrites the parsed authorship with it, detects sic/corrig again with a regex and
  strips the epithet issues the dummy name raised.
- An atomised name is parsed without its authorship and the authorship separately, through the same
  `parseAuthorshipIntoName`, so it never gets its nomenclatural code inferred from its authorship.
- **The authorship string depends on how a name came in.** An authorship inside the scientific name is rendered from
  the parsed atoms (`P.D.Sell`), a separate one keeps its verbatim text, cleaned by the regexes of
  `setNormalizeAuthorship` (`P. D. Sell`).

The goal is to rely on the parsed data and rebuild name and authorship from it. The parser still has quirks, so
**measure first**: interpret the names ChecklistBank holds before and after each change and read the differences.

## Decisions

- **Two corpus inputs.** The 67.5 million row name parser corpus (`clb-verbatim-names.tsv`, rowType, scientificName,
  authorship, rank, code) covers names given as a string, all of DwC and the matching API. It cannot exercise the
  atomised names, which ColDP prefers by default, nor author atoms, the other name columns or dataset settings. Rather
  than one file per format, a second export carries whole verbatim records, reduced to their name terms and
  deduplicated per dataset with a count, plus the name settings of each dataset (Markus, 2026-10-09).
- **ACEF is left out**: legacy, and its infraspecific records take their species from another record.
- **The harness interprets exactly as the importers do.** The term mapping of `DwcInterpreter` and
  `ColdpInterpreter` moved into static, store free `interpretName` methods that both the importers and the harness
  call. That is the only production change of phase 0.
- **Rows are compared by line number**, so two runs over the same input - and the same `--fraction` sample of it -
  join without a key. A sample is chosen by hashing the line number, the same rows in every run.
- **Significance**: whitespace, punctuation, case and diacritics differences of a text are counted but not
  significant, nor are issue changes alone. Everything structural is.
- **Phase 2 is decided by the corpus diff**, not committed blind.

## Plan

0. The harness and a baseline of the unchanged code (done, see [INTERPRETER-CORPUS.md](INTERPRETER-CORPUS.md)).
1. One parse per name and bug fixes, expected to change only what the fixes intend:
   - `NameParser.parse(Name)` takes everything from the combined parse: no second authorship parse, no
     `removeEpithetIssues`, no sic/corrig regex. The cleaned verbatim authorship string stays for now.
   - The atom path parses its label together with the authorship in one call; the atoms still win over the parsed
     epithets.
   - The bugs below.
2. Rebuild the authorship from the parsed atoms whenever it parsed completely, keeping the cleaned verbatim string
   only for partial parses. Decided per cause in the diff of phase 1 against phase 2: accepted as normalisation, fixed
   upstream, or kept verbatim for that shape.

## Findings of the code review

All confirmed in the code, 5, 6, 7 and 9 also in the fixture run of the harness.

1. `INCONSISTENT_AUTHORSHIP` is dead since 026d4849d: it compares the combined parse, in which the separate
   authorship already won, with a parse of that same authorship. `"Aus bus Smith, 1900"` + `"(L.) Mill."` raises
   nothing, only the mixed case `"Aus bus (L.) Smith"` + `"Mill."` (which the parser merges) still does.
2. A partly or not parsable separate authorship adds `PARTIALLY_PARSABLE_NAME`/`UNPARSABLE_NAME`, and an unparsable
   one replaces the parsed authorship by a single fake author.
3. A name with a phrase and an unparsed rest loses the phrase.
4. A `MANUSCRIPT` status the parser set is overwritten when no nomenclatural note parses to a status.
5. With `EPITHET_ADD_HYPHEN`, `MULTI_WORD_EPITHET` is flagged on every epithet.
6. A hybrid uninomial atom (`× Agropogon`) loses its notho.
7. A dagger as the only value of an epithet throws a NullPointerException in `ExtinctName`.
8. `TreeBaseHandler` glues a decision's name and authorship into one string, parses the authorship again, and its
   fallback would throw a NullPointerException.
9. A ColDP record whose atoms are preferred loses an authorship and a sic/corrig given only inside its scientific name
   (`Bacteroides corrig. Castellani & Chalmers, 1919` with genericName `Bacteroides` keeps neither).

Upstream defects for gbif/name-parser-rust, to be filed rather than worked around: no warning when a separate
authorship differs from the one in the name string, and `apply_authorship` merges instead of replacing; a partial
separate authorship replaces the name's unparsed rest; `und` is no author separator; `"Smith corrig., 1900"` is not
recognised; `parseAuthorship` returns a `ParsedAuthorship`, which cannot carry `originalSpelling`.

## Outcome

To be filled in as the phases land.
