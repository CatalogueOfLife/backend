# Comparing authors as persons

Date: 2026-09-23
Status: design agreed on 2026-09-23. Phases 0 and 1 are implemented on branch `fix/author-nomcode` (stacked on
`feat/author-corpus`, not merged); phases 2 and 3 are not implemented. Four phases, each with its own implementation plan.

This is step 2 of "Next" in [2026-09-19-author-comparison-corpus.md](2026-09-19-author-comparison-corpus.md): compare
people, not rewritten strings. It is built as an experiment next to the current comparison, which stays in production
until the corpus ([AUTHOR-CORPUS.md](AUTHOR-CORPUS.md)) shows the person based one is better.

## The problem

`AuthorComparator` decides whether two authorships are the same. It compares strings: fold to ASCII, read initials
and a surname, then a rule cascade. The 60k row `authormap.txt` ([AUTHORMAP-GENERATOR.md](AUTHORMAP-GENERATOR.md))
rewrites an alias into a canonical string that goes through the same cascade again. The corpus showed how that fails:

- A canonical that is a full name erases the initials that told relatives apart: G.O. Sars and M. Sars, the Sowerbys,
  the Adams and Fabricius relatives.
- The map cannot say that two strings name two people. `A.DC.` and `DC.` either collapse or stay apart by accident.
- One person sits in two rows, an IPNI row with the standard form and a Wikidata row with the full name, because rows
  were joined by full name only.
- A key collides: folding `ue` to `u` turns the alias `Hue` into `hu`, which makes `H.H.Hu` / `Hu` DIFFERENT on 277
  names.

In numbers, the map decides only 161 of 52,785 same acts on the corpus: 123 right (2,010 names), 38 wrong (570 names).
So the person model cannot gain much on the corpus as it is. What it is really for is relatives working in one field,
which the corpus under-represents, and a comparison whose verdicts can be explained by persons rather than string
coincidences.

## Goals

- A second implementation of the author comparison that identifies authors as persons, with the current one kept and
  unchanged, both measured on the same corpus.
- A person registry keyed by authoritative identifiers: Wikidata Q-id, IPNI author id (P586), ZooBank author id
  (P2006). A local `clb:` id only where no authority knows the person yet.
- Persons that carry the evidence needed to tell relatives apart: life and active years, the taxon groups an authority
  records, and parent and sibling relations.

## Non-goals

- Switching production to the person matcher. That is decided later, from the numbers of phase 3.
- Better team logic. Both matchers keep the rule that any matching author pair makes two teams EQUAL, so the corpus diff
  isolates person identity.
- Scores. The verdict stays the three valued `Equality`.
- Mining taxon groups from our own data. An author string is not a person, and an ambiguous string would collect the
  groups of everyone who shares it. Groups come from authorities only.
- Retiring `authormap.txt`, or storing resolved persons in the database.
- A ZooBank harvest through its API, which has no bulk access. ZooBank author dumps have been requested; until one
  arrives ZooBank ids come through Wikidata only.

## Design

### 1. The seam

`AuthorComparator` stays the one class every caller uses and keeps every rule about name structure: the year tolerances,
comparing combination, basionym and one against the other, choosing which team counts under which code in
`compareStrict`, and `compareAuthorsFirst`. Only author identity becomes pluggable:

```java
class AuthorComparator {
  AuthorComparator(AuthorshipNormalizer n)   // = new AuthorComparator(new StringAuthorMatcher(n)), unchanged for callers
  AuthorComparator(AuthorMatcher m)
  // compare / compareStrict / compareAuthorsFirst as today, plus overloads taking an AuthorContext
}

interface AuthorMatcher {
  Equality compareTeams(AuthorTeam t1, AuthorTeam t2, AuthorContext ctx, Mode mode);
}

record AuthorTeam(List<String> authors, @Nullable String year)   // normalized, already selected by code
record AuthorContext(@Nullable NomCode code, @Nullable TaxGroup group)
```

- The comparator still selects the team (`AuthorshipNormalizer.normalize(authorship, teamCode)`), so selection and
  normalization exist once. The year travels with the team, because the person matcher needs it.
- `Mode` names the three parameter sets the comparator uses today: lax, a year conflict (stricter prefix and
  Jaro-Winkler) and strict (every author looked up).
- `StringAuthorMatcher` is the current `compareAuthorteam`, `compareNormalizedAuthorteam` and single author cascade,
  moved verbatim with the author map lookup. It exposes the single author comparison for the person matcher's fallback.
- The code comes from the names, as it does now. `TaxGroup` is only known to the callers, so `UsageMatcher` and
  `NameIdentity` get overloads to pass it. The string matcher ignores it.
- These stay in `dao` next to `NameIndexImpl` and `ChangedMatcher`. GBIF's `matching-ws` depends on `dao` and calls
  `new AuthorComparator(AuthorshipNormalizer.INSTANCE)` and `compareAuthorsFirst(ScientificName, ScientificName)`, so
  both keep their signatures.

### 2. The person registry

Code in `core`, package `life.catalogue.matching.person`, files in `core/src/main/resources/authorship/persons/`.
It is not in `api`: only `core` needs it, and every consumer of the `api` jar would carry the data otherwise. Three tab
delimited files, each fact on its own line with its source:

| file | columns |
|---|---|
| `persons.tsv` | `id wikidata ipni zoobank formerIds family given suffix born died activeFrom activeTo groups source` |
| `names.tsv` | `person form kind code source` |
| `relations.tsv` | `person relation other source` |

- `id` is the best identifier of the person with a prefix: `wd:Q…`, else `ipni:…`, else `zb:…`, else `clb:N`. A person
  that gains a better id moves the old key to `formerIds`, so lines referring to it keep resolving.
- `family`, `given` and `suffix` are the structured name, taken from IPNI surname and forename (filius `f.` from the
  standard form), ZooBank family and given names, or Wikidata P734/P735 with a trailing `I`, `II` or `Jr.` from the
  label. They are never split off a full name: the last word is not the surname in `Geoffroy Saint-Hilaire` or
  `Ruiz López`.
- Years only, blank when unknown. `groups` is a pipe list of `TaxGroup` values.
- `kind` is `STANDARD` (IPNI or P428 standard form), `CITATION` (zoological citation, P835), `FULL` or `VARIANT`;
  `code` is `BOT`, `ZOO` or `ANY` with the meaning it has in `authormap.txt`.
- `relation` is `PARENT` or `SIBLING`.

`PersonRegistry` loads lazily, as a singleton, and only for the person matcher. It holds persons by id and every name
form under its `AuthorshipNormalizer.normalize` key, the form citations are folded to. It also derives two forms per
person when it loads: the initials of the given names with family name and suffix (`g b sowerby ii`) and the bare family
name. A key may map to several persons.

A test guards the files: every reference resolves, ids and former ids are unique, every group is a `TaxGroup`, every
person has a name form. The size is to be measured after the first harvest. Some 100k persons and 300k name lines, 15 to
20 MB of plain text, is the expectation. Plain text keeps diffs readable, and a much larger result would reopen that.

### 3. The harvest

Developer tools in `core` test scope, run by hand like `AuthorMapGenerator`. A run reads the three files, fetches, merges,
writes them sorted by id and writes a review report. It reads a list of `PersonSource`s giving `PersonRecord`s (ids,
name forms, years, groups, relations). Sources are joined on shared authoritative ids only, never on names.

- **Wikidata.** Every person with P428, P835, P586 or P2006, fetched as one paged query per property like
  `WikidataSource`. English label as `FULL`, P428 as a botanical `STANDARD`, P835 as a zoological `CITATION`, English
  aliases as `VARIANT`. Years from P569/P570 and P2031/P2032/P1317. Groups from P101 through a small curated table of
  fields of work, unmapped fields counted in the report. Relations from P22/P25 and P3373.
- **IPNI.** No bulk download, but the author search pages with a cursor: `q=author surname:*&f=f_authors` finds 65,018
  authors. It stops after 10,000 records per query, so the query is split by standard form prefix (`author std:A*` holds
  3,636) and a prefix above 10,000 is split further. It takes id, standard form, forename, surname, `dates`,
  `taxonGroups` and `alternativeNames`. `dates` is parsed into the four year columns: `1757-1822`, `fl. 1850`, `b. 1950`.
- **ZooBank.** `ZooBankDumpSource` reads a dump given with `--zoobank <file>` and is skipped without one. The reader is
  written once a dump exists, since its format is not known. What it maps to is fixed: the author UUID as `zb:` id and
  `zoobank` column, the names ZooBank cites the author by as zoological `CITATION`s, other name records of the author as
  `VARIANT`s, the lifespan as years, and any Wikidata, IPNI or ORCID link as a join key. The merge side is built and
  tested now with hand made records.
- **Merging.** A harvest fills empty cells and adds lines. It never overwrites a value and never removes a `curated`
  line. When sources disagree within one run, IPNI wins for a person with an IPNI and no ZooBank id, ZooBank for one with
  a ZooBank and no IPNI id, Wikidata otherwise. An authority is always right about its own id. Every disagreement - years
  more than two apart, differing ids - goes to the report.
- A Q-id that became a redirect is followed and the old key moves to `formerIds`. Anything else that disappears upstream
  is reported, never deleted.
- `authormap.txt` is not imported. The report lists its rows whose aliases resolve to no person, so that hand edits worth
  keeping can be added as `curated` lines.
- Tests run on recorded responses without network: the IPNI date parsing, the group mapping, the merge rules and the
  redirects.

### 4. How the person matcher decides

`PersonAuthorMatcher` compares the authors of two teams pair by pair and keeps the team rule of the string matcher.

1. **Resolve** a citation to its candidate persons: its normalized key among all name forms, derived ones included, with
   the codes of the forms restricted like the author map does (`BOT` and `ANY`, or `ZOO` and `ANY`). A bare surname
   resolves to everyone of that name. Resolutions are cached per citation and code.
2. **Narrow** the candidates. The year of the name rules out a person when it lies before `born + 10` or after
   `died + N`, N allowing for posthumous publication; active years do the same without life dates. Both margins are
   parameters, set on the corpus. The group of the context rules out a person whose groups are all disparate to it
   (`TaxGroup.isDisparateTo`, as `NameIdentity` uses it). A person without years or groups is never ruled out, and a
   citation whose candidates are all ruled out counts as unresolved.
3. **Decide** the pair:
   - both resolved and the candidates overlap: `EQUAL` (`Sowerby` / `G.B. Sowerby II`, `DC.` / `de Candolle`);
   - both resolved to disjoint, unrelated persons: `DIFFERENT` (`Mill.` / `L.`);
   - both resolved to disjoint persons that are related by `PARENT` or `SIBLING`: the **relatives policy**, `UNKNOWN` by
     default or `DIFFERENT`;
   - one side unresolved: the single author cascade of `StringAuthorMatcher` with the author map. With the other side
     resolved, the citation is compared against each of that person's forms, so `L. R. Cox` still meets `Cox`.
4. **Modes.** Identity does not depend on the mode; the fallback runs in the mode it was called with. An `UNKNOWN`
   passes through the comparator's year and name structure rules as it does today.
5. **Explaining.** `explain(t1, t2, ctx)` returns what each side resolved to and which rule decided, for the corpus
   report.

The relatives policy exists because the matcher cannot see recurrence. `A.DC.` / `DC.` is the same act on 350 names of
the corpus: World Plants and Tropicos cite the same publications once under the son, once under the father. A person
model that made two persons `DIFFERENT` by construction would split those 350 names in an extended release. Identity
has to be evidence that loses against recurrence, not a veto, and `UNKNOWN` does not split what sources confuse.
Whether `DIFFERENT` does better on relatives than it loses on such conflicts is what the evaluation measures.

### 5. Evaluation

- **Re-parse the corpus first.** The prod export holds the parse of whenever a dataset was imported, including defects
  name-parser-rust has since fixed: `in` citations inside an author, `Lam. & DC.` as `D.C.Lam.`, `Sowerby I` as an
  initial ([gbif/name-parser-rust#20](https://github.com/gbif/name-parser-rust/issues/20),
  [#21](https://github.com/gbif/name-parser-rust/issues/21), [#22](https://github.com/gbif/name-parser-rust/issues/22),
  fixed in 0.2.2). `CorpusReparser` runs every export row through the import's
  `parse(name, authorship, rank, code)` with the current parser and rewrites the parsed columns, and the pairs are mined
  again. The string matcher on that corpus is the baseline. Every report records the parser version.
- **Reports.** `AuthorCorpusReport` takes the comparator to measure instead of building one. `PersonCorpusReport` in
  `core` test scope runs it with the person matcher under both relatives policies, and `AuthorVerdictDiff` lists what
  flipped against the string baseline. From `explain()` the report adds the share of citations resolved, the verdicts
  decided by identity, by the fallback and by the relatives policy, the unresolved citations ranked by names - the
  worklist of missing persons - and every pair the relatives policy decided.
- **Relatives fixture.** The corpus hardly holds relatives as `DIFF`, since a dataset rarely lists both with years. A
  hand curated test set of 50 to 100 pairs covers them, each relative with a name they authored, taken from IPNI and
  WoRMS records and not from either matcher: Sowerby I, II and III, Rchb./Rchb.f., DC./A.DC./C.DC., Hook./Hook.f.,
  L./L.f., the Presl, Crouan and Adams brothers, Sars and Geoffroy father and son. Like
  `CorpusEvaluatorTest.knownMisjudgements` it pins what both matchers do.
- **Better** means: weighted by names, no worse than the string matcher on `SAME` judged `EQUAL` and on `DIFF` judged
  `DIFFERENT` of the re-parsed corpus, fewer pairs of the relatives fixture judged `EQUAL` than by the string matcher
  (reported per relatives policy, as `UNKNOWN` does not merge in `UsageMatcher` but does not rule out a pairing in
  `NameIdentity`), and every regressed pair backed by 10 or more names reviewed by hand. The report also prints run time and heap with the registry loaded.

Switching production afterwards means injecting the comparator into `UsageMatcher` and `TreeMergeHandler` from `core`,
which today take the string one from `NameIndexImpl.getAuthComp()` in `dao`, and passing the `TaxGroup` from
`NameIdentity`.

## Phases

| phase | what | where | done when |
|---|---|---|---|
| 0 | `CorpusReparser`, re-mined pairs, new baseline | `dao` test | the baseline report of the re-parsed corpus exists |
| 1 | the seam: `AuthorMatcher`, `StringAuthorMatcher`, `AuthorTeam`, `AuthorContext`, `Mode` | `dao` | all tests pass unchanged and `AuthorVerdictDiff` shows 0 changed verdicts |
| 2 | registry format, `PersonRegistry`, integrity test, harvest with its three sources, first harvest committed | `core` | the files load and pass the integrity test, the review report is read |
| 3 | `PersonAuthorMatcher`, `PersonCorpusReport`, relatives fixture, evaluation | `core` | the numbers are in this record's Outcome |

Phases 0, 1 and 2 are independent of each other; phase 3 needs all three.

## Rejected

- **An abstract base class** with the team comparison as template method. The person matcher would extend the string
  one to reach its fallback through `super`, an inheritance chain for what is composition.
- **An interface for the whole comparator** with two implementations. The year and name structure rules would exist
  twice or in a helper both must remember, and a corpus diff would no longer measure identity alone.
- **Registry rows as one line per person** with pipe lists, or **JSON lines**. Both change a whole line for any edit, and
  JSON is error prone by hand.
- **The registry in `api`**, next to `authormap.txt`: every consumer of the `api` jar would carry the data.
- **Author map rows as interim person ids**, to measure quickly. A person is identified by an authority, not by a row
  of a string map.
- **Joining sources by name.** Joining on a shared full name is how one person came to sit in two rows of the author map.
- **Relatives as a hard `DIFFERENT`**, which would split the 350 `A.DC.` / `DC.` names. It is a policy measured both ways.
- **Working around parser defects in the normalizer**, e.g. cutting `in` citations off an author string. They were fixed
  in the parser (name-parser-rust#20 to #22) and the corpus is re-parsed instead.

## Outcome

**Phase 0, the re-parsed baseline** (2026-09-23, name parser 0.2.2-SNAPSHOT built from name-parser-rust `e38bf70`,
the native jar of 2026-09-23T17:31Z). The fixed parser was on neither Nexus nor in the local repository yet and had to
be built locally. Of 12,373,803 names 76,231 changed their parse and 1,378 were dropped - IRMNG 21,628, WoRMS 15,354,
ITIS 8,496, IPNI 6,931 and ZooBank 6,254 changed most, the datasets with the `in` citations. Mining gave 50,004 `SAME`,
95,541 `DIFF` and 273,967 `DUBIOUS` pairs over 714,553 author keys, 18k keys fewer than before as `X in Y` now reduces
to its author. The string matcher on it:

| | EQUAL | DIFFERENT | UNKNOWN | pairs |
|---|---|---|---|---|
| `SAME` | 95.9% | 3.9% | 0.2% | 50,004 |
| `DIFF` | 1.3% | 92.0% | 6.7% | 95,541 |
| `SAME` without years | 96.0% | 3.8% | 0.2% | |
| `DIFF` without years | 2.3% | 91.1% | 6.6% | |

Weighted by names 96.5% of the same acts compare `EQUAL`, against 95.0% on the export as it was. Same acts judged
`DIFFERENT` by their authors went from 2,912 pairs backed by 23,349 names to 1,915 pairs backed by 15,354. The whole
`X in Y` class left the worklist (`Grunow in Van Heurck` alone stood for 416 names), and so did `Yıld.` / `Yıld.`, which
was a stale parse of WFO folding the dotless i rather than a comparison defect. The top of the list is now `Anon.` /
`hort.`, `A.Cleve` / `Cleve-Euler`, `H.H.Hu` / `Hu`, `C.Presl` / `K.B. Presl` and `G. B. Sowerby II` / `Sowerby` -
relatives and aliases, what the person model is meant for. The frozen sample was refreshed from this corpus: 16,641
pairs.

**Phase 1, the seam** (2026-09-23). `AuthorMatcher`, `StringAuthorMatcher`, `AuthorTeam` and `AuthorContext` as designed;
`Mode` carries the parameters the comparator used to pass (`LAX` 4/4/90, `YEAR_CONFLICT` 12/4/99, `STRICT` 4/all/100).
On the re-parsed corpus `AuthorVerdictDiff` found 0 of 419,512 verdicts changed. A matcher's `UNKNOWN` in a year conflict
still becomes `DIFFERENT`, the rule the comparator already had, and phase 3's relatives policy meets it there.
Deviation: the comparator's new overloads take a `TaxGroup` next to the code rather than an `AuthorContext`, since a
public `compare(Authorship, Authorship, AuthorContext)` would make every existing call with a `null` code ambiguous.
