# A corpus to measure author comparison

Date: 2026-09-19
Status: implemented on branch `feat/author-corpus`, not merged. A first corpus was mined from prod on the same
day and its sample is committed with the guard test. The labels had a first audit of 60 pairs each by the
author of the tools only, the audit of 100 by a taxonomist is outstanding (see Outcome).
Nothing in here changes how authorships are compared.

How to run the tools is in [AUTHOR-CORPUS.md](AUTHOR-CORPUS.md).

## The problem

`AuthorComparator` and `AuthorshipNormalizer` decide whether two authorships are the same. That verdict gates
homonym separation in `UsageMatcher`, stable ids (`NameIdentity`, see
[2026-09-15](2026-09-15-stable-id-evidence-model.md)), bare name merges in `TreeMergeHandler` and the names diff
(`ChangedMatcher`). Every one of them only ever compares names that already share a canonical names index id.

The method: fold each author to lower case ASCII without punctuation, read it as initials, last word = surname
and an optional suffix, then run a rule cascade (identical, Jaro-Winkler above 90, a common prefix of four
characters) with the initials compared as a multiset. The 60k row `authormap.txt` is a **string rewriter**: an
alias becomes its canonical string, which goes through the very same cascade again. Two teams are equal as soon
as any one author of one matches any one author of the other.

Probing master on 2026-09-19 found misjudgements of every kind, several of them made by the dictionary:

| pair | verdict | why |
|---|---|---|
| `Martin` / `Martius`, `Steinmann` / `Steindachner`, `Peters` / `Petersen` | EQUAL | the four character prefix rule also fires when neither side is an abbreviation - the period was stripped before comparing |
| `Li` / `Liu`, `Wang` / `Wan` (same year) | EQUAL | a short surname equal to the common prefix |
| `Smith & Jones` / `Brown & Jones` | EQUAL | one shared author is enough |
| `Smith Jr.` / `Jones Jr.` | EQUAL | `jr` is read as the surname |
| `John Smith` / `Jane Smith` | EQUAL | spelled out given names never become initials |
| `Brongn.` / `Al.Brongn.`, `A.Juss.` / `Ant.Juss.`, `G.Schneid.` / `Gus.Schneid.` | EQUAL | IPNI's multi letter given name abbreviations exist only to tell these people apart and are dropped |
| `J.E. Gray` / `G.R. Gray`, `Rchb.` / `Rchb.f.` | EQUAL, DIFFERENT without the map | the canonical holds full given names or no filius, erasing what told the relatives apart |
| `Zopf` / `Wisl.`, `Kiggel.` / `F.A.Bauer` | EQUAL, DIFFERENT without the map | canonicals that are a bare given name (`Friedrich`, `Franz`) |
| `A.DC.` / `DC.` | EQUAL | the map knows two people and cannot say so |
| `Sw. 1820` / `Swainson 1820` | DIFFERENT, EQUAL without the map | no `NomCode` reaches `compare()`, so a zoological `Sw.` becomes the botanist Swartz |
| `Wang Wen Tsai` / `W.T. Wang`, `Gómez-Campo` / `Gómez`, `Geoffroy` / `I. Geoffroy Saint-Hilaire` | DIFFERENT | the surname is always the last word |
| `O'Brien` / `J. Brien` | DIFFERENT | `O'` becomes an initial |

Structurally: with authors on both sides the verdict is never `UNKNOWN`; the period, the author order and the
given names are destroyed before anything is compared; the map can only ever add equalities.

Each of these was found by thinking of it. Every fix so far (#1595, #1597) came from one reported name, and the
only aggregate guards are `AuthorComparatorTest.compareAuthorFile` (`equal < 1300` over 1488 authors) and
`AuthorBucketerTest`. Neither says whether a rule change makes matching better or worse.

## Goals

- A labelled corpus of authorship pairs mined from ChecklistBank itself, so that a rule change is a confusion
  matrix diff and the disagreements, ranked by the number of names they affect, are the worklist.
- Labels that do not depend on the code they measure.
- A frozen sample in the repository with a fast guard test.

## Non-goals

- Changing any comparison behaviour. `git diff master -- '*/src/main/*'` stays empty.
- Database access from java. The corpus is one export a person runs; the tools read a file.
- A gold standard. The labels are silver and the report has to keep their noise visible.

## Decisions

**What a label means.** "The same nomenclatural act on this canonical name", not "the same person". That is the
question production asks, since candidates always share a names index id, and it makes a truncated team
(`Smith` for `Smith & Jones`) a legitimate positive.

**The key.** Four slots - basionym ex authors, basionym authors, combination ex authors, combination authors -
of the raw parsed authors with whitespace collapsed. Year and sanctioning author are left out. Its *loose*
form keeps lower cased letters and digits only; pairs equal on that (`L.` / `L`) are trivial, counted and
dropped. Diacritics stay in, because folding them is the normaliser's job and therefore part of what is measured.
Nothing in the miner may import `AuthorshipNormalizer` or `AuthorComparator`.

**Positives come from co-citation, and only from unambiguous groups.** Names are grouped by names index id and
rank. For two datasets in a group the keys they share are set aside and a pair is counted only when exactly one
key is left on each side. Without that rule two datasets that both list `Aus bus L.` and `Aus bus Mill.` make
`L.` / `Mill.` an "alias" with high support. *Support* is the number of groups, never of datasets - IPNI, WCVP and
WFO share a lineage and would otherwise vote three times.

A pair is `SAME` when its year conflicts stay below a fifth and either support is at least 3 with
`support / min(freqA, freqB) >= 0.05`, or the years agree on at least two names. Botanical authorships mostly
carry no year, so recurrence has to be the signal there: an alias recurs on a large share of the rarer key's
names (ratio 0.3 to 1), what is left of prolific homonym pairs sits around 0.01. The year route is for the
zoological long tail, whose authors never reach a support of 3.

**Negatives come from inside one dataset, and only with conflicting years.** Two different keys in one group of
one dataset are names that dataset keeps apart: `DIFF` when their years are more than one apart, `DUBIOUS` when
the years agree (an isonym or a duplicate record), when a year is missing, or when the pair has any support as a
positive. Dubious pairs are written and reported but never enter the matrix.
A missing year was meant to give a `DIFF` too. The first corpus settled that: 19% of those 233k pairs compared
`EQUAL` against 1.3% of the ones with conflicting years, and the top of that list was nothing but second
records of one name - `Pohl` next to `Pohl ex Benth.`, `Kuntze` next to `(Nees ex DC.) Kuntze`, 31% of IPNI's
and 47% of Dyntaxa's pairs. Without a year a homonym cannot be told from a duplicate.

**An instance** is a key pair with one representative record pair, the first group whose years agree if there is
one. It gets two verdicts: with the delivered years and code, which is what production sees, and with the years
removed, which isolates the author logic the label speaks about. Matrices are shown per pair and weighted by
support, because the labelled positives lean towards prolific authors.

**The sample** is drawn without asking the comparator - per label and code the pairs with the highest support
plus a hash selected tail - so it cannot overfit today's failures and a refresh stays a small diff.

**Datasets.** Twenty hand picked nomenclators and curated checklists: IPNI 2006, WFO 2004, WCVP 2232, Tropicos
310868, World Plants 1141, Index Fungorum 1028, Species Fungorum Plus 2073, AlgaeBase 304756, INA 2003, LPSN
2015, ZooBank 2037, WoRMS 2011, IRMNG 2007, ITIS 2144, Fauna Europaea 2026, PBDB 1174, TAXREF 2008, Dyntaxa
2041, Artsnavnebasen 2030, COL China 312616.

## Rejected

- **A command inside the webservice** that streams the aggregate itself. More code, and it would run in the
  app's deploy for something needed a few times a year.
- **All matched datasets.** The widest variance, but a scan of the whole `name` table and labels from checklists
  nobody curates.
- **`\copy`.** psql interpolates no variables into it and it has to fit one line; a temp table to work around
  that is refused on a standby. Hence `COPY ... TO STDOUT` in a `-f` script and gzip in the shell.
- **Joining the usage status.** No rule uses it, a name with several usages multiplies and it adds a third
  partitioned table. `nom_status` rides along instead, it is free.
- **Committing the corpus.** About 250 MB. The sample is enough to guard, the corpus is reproducible.
- **A learned string similarity or an LLM as the comparator.** Stable ids hang on where `EQUAL` ends, so the
  verdict has to be deterministic and explainable. Either may help curate the map offline.

## Next

The direction this is meant to make measurable:

1. The unambiguous defects first: pass `NomCode` through `compare()` and from `UsageMatcher.parseSciName`, keep
   initials and suffix when an alias expands, read multi letter given name abbreviations, `jr`/`sr`, `O'`/`d'`.
2. **Compare people, not rewritten strings.** A row of the map is a person. Two citations that both identify a
   person are `EQUAL` on the same one and `DIFFERENT` on two, which settles relatives by construction; a bare
   surname only ever proposes a candidate. This needs an id column in `authormap.txt`, and that column should
   hold the authoritative identifiers - **Wikidata Q-id, IPNI author id, ZooBank author id** - rather than an
   invented one. `WikidataSource` already selects `?person` and throws it away, P586 (IPNI) and P2006 (ZooBank)
   are one `OPTIONAL` each in the same query, and IPNI needs one new harvest because `IpniAuthorLookup` kept
   only the standard form, forename and surname.
3. Parse an author once into a structure that keeps the period, the order and the given names, and turn the
   cascade into evidence that can also say `UNKNOWN` and carry a score for the callers that rank candidates.
   Where `UNKNOWN` may begin has to be read off the corpus: `UsageMatcher` merges only on `EQUAL`.

## Outcome

**The first corpus**, exported from prod on 2026-09-19: 12,373,803 names of 3,118,435 groups and 732,438 author
keys. Mining takes a minute. 442,433 key pairs were seen across datasets and 385,604 inside one; 52,785 became
`SAME` (41,811 by recurrence, 10,974 by agreeing years), 97,328 `DIFF`, 278,018 `DUBIOUS` and 349,214 stayed
unlabelled, 304,374 of them seen on a single name. 1.28 million dataset pairs of a group were ambiguous and
gave nothing, which is what the one-key-left-on-each-side rule costs.

**Label precision.** Of 60 hash sampled pairs per label about 59 held up on both sides. The doubtful `SAME` was
`Christ` / `(Christ) Brick`, the doubtful `DIFF` `Gave, 1891` / `Gave ex Rouy & Foucaud, 1893`, a name validated
later rather than a homonym. This was read by the author of the tools, not by a taxonomist. The thresholds
were left at their defaults; the one rule that changed is the missing year above.

**What master does**, per pair:

| | EQUAL | DIFFERENT | UNKNOWN | pairs |
|---|---|---|---|---|
| `SAME` | 94.1% | 5.6% | 0.3% | 52,785 |
| `DIFF` | 1.3% | 92.1% | 6.6% | 97,328 |
| `DIFF` without years | 2.4% | 91.1% | 6.5% | 97,328 |

Botany and zoology differ little on `SAME` (94.6% and 93.1% `EQUAL`). So the comparator loses far more by keeping
one act apart - 2,934 pairs standing for 23,630 names - than by taking homonyms for one, and the homonyms it
does miss are mostly arguable (`G. Lodd., 1829` / `G.Lodd. ex Sweet, 1831`). The relatives this work started
from hardly show up as `DIFF`, because a dataset rarely lists both with years.

**What the worklist shows that nobody had thought of:**
- `X in Y` citations are the largest class by far: `Grunow, 1883` / `Grunow in Van Heurck, 1883` (416 names),
  `Hustedt` / `Hustedt in Schmidt et al.`, `Cassini in F. Cuvier`, `Roewer in Bronn`, `Chevrolat in Dejean`.
- The author map makes `H.H.Hu` / `Hu` `DIFFERENT` on 277 names: keys shorter than four characters are looked up
  first, and `hu` is the botanist Hue.
- Married and compound names without a particle: `A.Cleve` / `Cleve-Euler` (1,040 names), `Rojas` / `Rojas Acosta`.
- One person under two sets of initials: `C.Presl` / `K.B. Presl` (210), `C. Koch` / `K.Koch`.
- Generational suffixes the parser does not know: `G. B. Sowerby II` / `Sowerby`, `Crouan fr.` / `P.Crouan & H.Crouan`.
- `Yıld.` / `Yıld.` (161 names) differ in a dotless i only and compare `DIFFERENT`.
- Label noise that stays in: `Anon.` / `hort.` (948 names) and datasets that simply disagree on the author,
  `Perkins, 1900` / `Sharp, 1900`.

**A warning for the person model in Next.** `A.DC.` / `DC.` is a `SAME` backed by 350 names: World Plants and
Tropicos cite the same publications once under the son and once under the father. Two people being `DIFFERENT`
by construction would split those 350 names in an extended release. Identity has to be evidence that loses
against recurrence in the data, not a veto.

**Deviations from the plan.** The tools run through `exec:exec` in a forked JVM, not `exec:java`, which fails on
the SAX provider of the dao test classpath and would run in maven's 512 MB heap. The pairs file carries all
counters and both keys. The verdict of a pair is identified by code and both keys. The sample holds 16,679
pairs in 1.2 MB and the guard test runs in 0.6 seconds.
