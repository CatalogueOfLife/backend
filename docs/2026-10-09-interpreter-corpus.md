# Simplifying name interpretation, measured on the ChecklistBank corpus

Date: 2026-10-09
Status: phases 0 (the corpus harness) and 1 (one parse, bug fixes) implemented on `feat/interpreter-corpus`, PR #1615,
measured on both corpora and with name-parser-rust 0.3.1-SNAPSHOT build 5. Phase 2 not yet decided.

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

All confirmed in the code, 5, 6, 7 and 9 also in the fixture run of the harness, 1, 3, 4 and 10 to 13 in the corpus.

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
10. The regular expression that removes a taxonomic note from the authorship string was built from the note without
    quoting it: `Auctorum ({non} Linnaeus, 1767), 1767` threw a `PatternSyntaxException` out of the interpreter, the
    only 3 exceptions of the 67.5 million records of the parser corpus. In the prod export it is 15 records of 15
    datasets, and an exception of the interpreter is not caught per record: `DataCsvInserter.processVerbatim` lets it
    fail the whole import, so none of these datasets could be imported again.
11. Author atoms with a year but no author (`combinationAuthorshipYear` without `combinationAuthorship`) left the
    author list null, where a parse always gives an empty one. Nothing in the interpreter trips over it, the corpus
    harness did for 90,564 ColDP records of the prod export; empty lists since 1e874e1dd.
12. `ExtinctName` took the X of the bacterial genus atom `XBB1006` for a hybrid sign and kept `BB1006`; a letter x in
    a field of its own is a hybrid sign only when a space follows since 98e6a63b8. The shared
    `SciNameNormalizer.removeHybridSignGenus` builds names index keys and stays as it is.
13. Not fixed: `sanitizeEpithet` lower cases an epithet atom of several words as a whole, so
    `sp. Groote Eylandt (D.J.Dixon 1365 & I.D.Cowie)` becomes `sp. groote eylandt (d.j.dixon 1365 & i.d.cowie)`.
    876k export records have such an epithet, but DwC uses its atoms only without a scientific name; 18.6k ColDP
    `Name` records, mostly Australian phrase names, are affected.

Upstream defects, filed on gbif/name-parser-rust on 2026-10-09 (#62-#73, #76, #77) rather than worked around, with the
corpus rows they affect where measured (phase 1, the phase 2 experiment and parser build 5 below):

| defect | rows |
|---|---:|
| #62 no warning when a separate authorship differs from the one in the name string, also the authorship of the species in an infraspecific name string; `apply_authorship` merges instead of replacing (`(Valenciennes, 1826)` + `Achille Valenciennes`) | ~10-20k real conflicts |
| #63 a year suffix letter is dropped from the year (`Schedl, 1964a`) | 51,416 |
| #64 a bracketed year loses its brackets, which say the date was inferred (`Westwood, [1851]`) | 28,779 |
| #65 the homoglyph table merges distinct letters, 18 pairs: `ě`→`ĕ`, `Ő`→`Ö`, pinyin `ǎǐǒǔ`→breve, `ț`→`ţ`, … (`Bechyně`, `Vězda`, `Štěpánek`); shared with name-parser-api's `homoglyphs.txt` | 10,644 |
| #69 `(Approved Lists 1980)` of a bacterial authorship is dropped | 9,780 |
| #66 a leading `ex` is dropped (`(ex Winogradsky, 1929) Blackall et al., 1986`), a second `ex` author too (`Degen ex Nyár. ex Csürös`) | 5,657 |
| #67 `MS` (manuscript) is read as the initials `M.S.` (`Stephens (ex Kirby MS) 1828` → `ex M.S.Kirby`) | 2,673 |
| #68 a capitalised `Et Al.` is read as an author `Al.` | 2,508 |
| #70 a sic with a dot or a corrig. before a comma in a separate authorship is not recognised: `X, 1881 sic.`, `(X, 1883) [sic.]` (read as an author), `corrig.Yoon et al.`, `Smith corrig., 1900` | 92 |
| #72 case normalisation of all capital authors: `MCCORD` → `Mccord`, `DE SAUSSURE` → `DE Saussure` | |
| #73 a soft hyphen splits an epithet (`novae­zelandiae Hirn` → epithet `novae`, author `zelandiae Hirn`) | |
| #71 a partial separate authorship replaces the name's unparsed rest instead of adding to it | |
| #67 `MS` inside basionym brackets makes no manuscript name (`(Kuroda MS in Kira, 1959)`) | |
| #77 since #74 an organism label phrase gets a synthetic `sp.`: `Holophagaceae sp. bacterium UBA692` | 123,129 |
| #76 an informal result loses Candidatus, since #74 also for NCBI placeholders | 11,452 |
| not filed: `und` is no author separator (mostly in references) | 50 |
| not filed: (api) `parseAuthorship` returns a `ParsedAuthorship`, which cannot carry `originalSpelling`; CLB no longer needs it | |

## Outcome

### Phase 0

The parser corpus of 67,471,137 rows runs in 13 to 16 minutes on 14 threads; 278,867 rows have no name (ColDP records
without a scientific name, the parser corpus has no atoms). Markus ran the export on prod on 2026-10-09: 121,928,123
rows for 122,977,682 verbatim records (66.7M `dwc:Taxon`, 13.4M `col:Name`, 41.8M `col:NameUsage`) and the name
settings of 263 datasets, 4.2 GB gzipped; a run takes 28 to 52 minutes. A run built from a copy of the sources names
its code with `-Dcorpus.code`, and comparing parser builds means pinning their jars on the classpath, as `~/.m2`
holds one build only.

### Phase 1

Implemented as planned, with these deviations:

- `NameParser.parse(String)` stays: it implements the `Parser` interface, it is not dead code. `determineType`,
  `parseAuthorshipIntoName`, `removeEpithetIssues`, `removeTrailing` and the sic/corrig recovery are gone.
- An unparsable name keeps its authorship as given, without the old containment check: `Name.getLabel()` already
  leaves out an authorship the name string holds.
- An informal name keeps a separate authorship that is not in its phrase as a cleaned string, without parsed atoms.
- The taxonomic note of the combined parse can come from the name string, the authorship or both, spelled differently
  (`& al.` and `et al.`), or be all the authorship holds (`sensu` + `auct NZ`). `noteInAuthorship` decides what to
  remove from the authorship string; the first corpus run showed each of these cases.
- Bugs 3 and 4 were thought to be theoretical but are not: an informal phrase next to an unparsed rest
  (`Otitesella uluzi complex sp. 1 ex Ficus sp. samfya ag.` lost `sp. 1`), and `in litt.` makes a manuscript name
  without a nomenclatural note that parses to a status (821 corpus rows now MANUSCRIPT).
- Atomised names now get their code inferred from their authorship: ACEF animal subspecies lose their `subsp.` marker,
  as zoological trinomials do (`NormalizerACEFIT`, `AcefInterpreterTest`).

Corpus diff against the baseline, parser corpus: 435,870 of 67,471,137 rows changed (0.65%), 432,688 significantly.
Almost all of it is the parser's combined parse surviving instead of being overwritten by the second parse of the
separate authorship, which most often holds less than the name string - no year (`Dumbletonius Dugdale, 1986` +
`Dugdale`) or no brackets (`Arippara disticha (Turner, 1904)` + `Turner`):

| change | rows |
|---|---:|
| `INCONSISTENT_AUTHORSHIP` no longer raised | 424,601 |
| combination authorship atoms changed / removed / added | 320,317 / 84,853 / 19,032 |
| published in year added (from the year of the name string) / removed / changed | 288,001 / 7,923 / 293 |
| basionym authorship atoms added | 87,685 |
| label changed in more than punctuation and whitespace | 537: 320 a doubled authorship or year gone, most others junk input either way (`(to be filled)`, `Lasiocoma petrophiloides (auct. non DC.) ) H. Bol.; Compton`) |
| exceptions | 3 → 0 |

Two consequences matter for phase 2:

- `INCONSISTENT_AUTHORSHIP` flagged a name string holding *more* than the authorship as often as a real conflict. The
  real conflicts are an authorship of the species next to an infraspecific name string, e.g.
  `Cyprinus carpio Linnaeus, 1758 ssp. murgo Dybowski, 1869` + `Linnaeus, 1758`: the parser keeps the terminal
  authorship of the name string without a word, the flag is gone. It needs a parser warning (upstream 1).
- The atoms and the kept verbatim authorship string now disagree wherever the parser preferred the name string: the
  label still reads `Cyprinus carpio murgo Linnaeus, 1758` while the atoms say Dybowski, 1869, `Typhlosaurus
  <Unspecified Agent>` while they say Wiegmann, 1834. Rebuilding the authorship from the atoms (phase 2) resolves it.

Regressions accepted for upstream fixes: 92 rows lose a malformed sic marker of a separate authorship the old regex
recovered and the parser does not (`(sic) Henneberg 1903`, `sic) Audureau 1940`, `(…) sic.`, `[sic[`); a manuscript
`MS` inside basionym brackets is no longer seen (`(Kuroda MS in Kira, 1959)`).

### Phase 1 on the prod export

The export exercises what the parser corpus cannot: atomised ColDP names, author atoms and dataset settings. Diff of
phase 1 against master (both with the parser build of 2026-10-09 03:43), 122,977,682 records: 852,548 rows change
(0.70%), 845,447 significantly.

| change | rows |
|---|---:|
| code inferred for an atomised name that had none: BOTANICAL 514,943 (510,832 of them LCVP), ZOOLOGICAL 146,633, CULTIVARS 468, VIRUS 23 | 660,909 |
| `INCONSISTENT_AUTHORSHIP` no longer raised | 167,433 |
| combination authorship atoms changed / removed / added | 127,123 / 43,360 / 3,453 |
| basionym authorship atoms added | 40,190 |
| zoological trinomials without `subsp.` now that their code is inferred: `Trichordestra liquida subsp. liquida` → `Trichordestra liquida liquida` | 14,324 |
| exceptions (finding 10) | 15 → 0 |

The inferred codes agree with the datasets: only 3,247 records get a code that is not the most common inferred code of
their dataset, the cases gbif/name-parser-rust#60 is about. Datasets with a code setting are not affected at all.

### name-parser-rust 0.3.1-SNAPSHOT build 5

Build 5 (2026-10-09 10:25) holds the fixes of gbif/name-parser-rust#61 (#74) and of the import diffs (#58, #59). The
version stays 0.3.1-SNAPSHOT, so the backend pom does not change; every parser, core, importer and dao test passes with
it, apart from `ReferenceMapperTest.listOrphans`, which fails in the full dao suite on master too. Diff of phase 1 with
the build of 03:43 against phase 1 with build 5:

| corpus | rows changed | significantly |
|---|---:|---:|
| parser corpus, 67.5M rows | 139,936 (0.21%) | 137,435 |
| prod export, 122M rows | 152,073 (0.12%) | 148,371 |

Most of it is #61 item 1: an NCBI placeholder no longer takes its organism label for the species epithet
(`Holophagaceae bacterium UBA692`, `Wolbachia endosymbiont of Aprostocetus sp.`, 123k parser corpus rows), which makes
it an informal name. Two regressions come with it, filed as #77 - the canonical form gets a synthetic `sp.`,
`Holophagaceae sp. bacterium UBA692`, rendered so by the parser's own `NameFormatter` as well - and #76 - the informal
result has no candidatus flag, 11,452 parser corpus rows (13,867 export rows) lose Candidatus. The rest is
improvements: the Turkish dotless ı stays in surnames (`Arabacı`, `Yıld.`), `m.` is a rank marker
(`Phlaeoba pharaonis m. aterrima`), an unmarked infraspecific epithet after the species author parses
(`Lithospermum arvense L. arvense L.`), a lone lower case epithet is a placeholder (`brachiariae`), and a bracketed
`[non Hampson 1906]` is a taxonomic note.

### Phase 2, measured but not decided

Branch `exp/authorship-from-atoms` (3627f4b4a, off phase 1): a separate authorship is rendered from its parsed atoms
like any other, only a partly parsed one is kept as given. Existing tests that pin verbatim authorship strings fail
there, it is an experiment. Corpus diff against phase 1: 4,561,253 rows change (6.8%), 1,294,362 in more than
whitespace, punctuation, case or diacritics. Only the authorship string and the label change.

What it gains:

| change | rows |
|---|---:|
| whitespace only, initials joined: `P. D. Sell` → `P.D.Sell`, `C. B. Adams` → `C.B.Adams` | 2,702,394 |
| the year or basionym brackets of the name string the authorship column lacks: `Dodd` → `Dodd, 1914`, `Macquart` → `(Macquart, 1851)` | ~381,000 |
| punctuation: a comma before the year, `Rea (1922)` → `Rea, 1922` | 377,209 |
| surname-first initials: `Rehn, J.A.G., 1919` → `J.A.G.Rehn, 1919`, `CHEMSAK J. A.,NOGUERA F. A.,1993` → `J.A.Chemsak & F.A.Noguera, 1993` | ~367,000 |
| all capital authors: `PARKER, 1949` → `Parker, 1949` | 179,305 |
| placeholders gone: `Missing`, `Not specified`, `<Unspecified Agent>` (label `Typhlosaurus Wiegmann, 1834`) | 49,778 |
| the terminal authorship of an infraspecific name string instead of the species' in the column: `Vicia sativa L. var. macrocarpa Moris` + `L.` → `Moris` | part of 96,622 `other` |
| the scientific name repeated in the authorship column: `Rotala densiflora (Roem. & Schult.) Koehne` → `(Roem. & Schult.) Koehne` | ~11,600 |

What it loses, mostly through the parser defects above, which it would carry into every label:

| change | rows |
|---|---:|
| `in` and `apud` citations: `Bentham in Bentham & J.D. Hooker, 1873` → `Bentham, 1873` - a policy question, the atoms deliberately keep the author only | 241,410 |
| year suffix letters | 51,416 |
| bracketed years | 28,779 |
| homoglyph letters | 10,644 |
| `(Approved Lists 1980)` | 9,780 |
| a leading `ex` | 5,657 |
| bacterial authors cut to `et al.` without comma by the formatter's ICNP rule: `Giovannoni, Schabtach & Castenholz, 1995` → `Giovannoni et al. 1995` | 5,394 |
| `MS` as initials | 2,673 |
| `Et Al.` as an author | 2,508 |

Phase 2 is not ready as it is. Options for Markus: wait for the parser fixes and measure again with this branch; or
render from the atoms only where they say more than the authorship string (a year, brackets, other authors, or the
string is a placeholder), which keeps the larger gains and none of the losses but leaves the cosmetic normalisation
undone.
