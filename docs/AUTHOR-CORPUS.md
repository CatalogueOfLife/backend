# Measuring author comparison with a corpus

`AuthorComparator` decides whether two authorships are the same. Whether a change to it, to `AuthorshipNormalizer`
or to the author map ([AUTHORMAP-GENERATOR.md](AUTHORMAP-GENERATOR.md)) makes matching better or worse is measured
against a corpus of labelled authorship pairs mined from ChecklistBank itself. Why it is built the way it is, is in
[2026-09-19-author-comparison-corpus.md](2026-09-19-author-comparison-corpus.md).

All of it is test scope code in `dao/src/test/java/life/catalogue/matching/authorship/corpus/`. None of it runs
in the build except the fast guard test on a committed sample.

```
author-corpus-export.sql ──▶ author-corpus.tsv.gz ──CorpusReparser──▶ reparsed export ──AuthorPairMiner──▶ pairs.tsv.gz ──AuthorCorpusReport──▶ report.txt
      (run by hand)            (~200 MB, not in git)                                                                                        └─▶ verdicts.tsv.gz ──AuthorVerdictDiff
                                                                                               └──AuthorPairSampler──▶ author-pairs-sample.tsv.gz ──AuthorCorpusTest
```

## What a label means

**The same nomenclatural act on a name**, not the same person. Every caller of the comparator compares names that
already share a names index id, so this is the question production asks. A truncated team, a missing basionym
author or an ex author left out are all the same act.

| label | where it comes from |
|---|---|
| `SAME` | Two datasets cite one name (names index id + rank) with two different authorships, and they are the only ones the two datasets do not share. That has to recur on at least 3 names, making up at least 5% of the names of the rarer citation, or to come with agreeing years on at least 2 names. |
| `DIFF` | One dataset holds two names with one names index id and rank whose years are more than one apart: homonyms. |
| `DUBIOUS` | One dataset keeps two names apart, but without a year, with the same year, or while other datasets cite them alike. A homonym cannot be told from a second record of the same name. Reported, never measured. |

Citations that differ in punctuation, spacing or case alone make no pair. Diacritics do. A name without a
nomenclatural code takes the one the other names of its names index id agree on, and names of two different
codes are never paired. `LabelRules.DEFAULT` holds the thresholds, `AuthorPairMiner` must not use any of the
code it produces labels for.

The labels are silver: a first audit of 60 hash sampled pairs of each label found about 98% of them right.

## Running it

Every tool runs in a forked JVM, which is why paths are **relative to the `dao` module**. `exec:java` does not work
for these: it runs inside maven's 512 MB JVM and fails on the SAX provider of the dao test classpath.

**1. Export.** Read only and safe on a standby. Give it the keys of curated nomenclators and checklists:

    psql -X -q -v ON_ERROR_STOP=1 -v keys='2006,2004,2232' \
      -f dao/src/test/resources/author-corpus/author-corpus-export.sql "<conninfo>" | gzip > author-corpus.tsv.gz

The corpus was exported from prod with
`2006,2004,2232,310868,1141,1028,2073,304756,2003,2015,2037,2011,2007,2144,2026,1174,2008,2041,2030,312616`:
12.4 million names of 3.1 million names index ids in September 2026, and 12.5 million with their classification on
2026-09-24. `AuthorCorpusExportIT` runs the statement against the test
schema, so a renamed column fails there and not on prod.

The taxonomic group of a name is persisted nowhere, so the export carries what it is derived from instead: the names
of the higher taxa of the name's taxon, or of the accepted taxon of a synonym, from `taxon_metrics`, which imports fill
for external datasets. Only ranks down to a suprageneric name are kept, the ones `TaxGroupAnalyzer` reads, and
`ExportRow.group()` derives the group with `TaxGroupAnalyzer.analyzeNames`. The nomenclators hold bare names without a
taxon and so without a classification: a name without one gets the group of its dataset where the dataset has one -
algae for Index Nominum Algarum (2003), fungi for Species Fungorum Plus (2073) and eukaryotes for ZooBank (2037), which
registers protists besides animals - and else what the analyzer makes of the name and its code. An export from before
the column still reads, without a classification.

**2. Re-parse.** The export holds the parse each dataset got when it was imported, including parser defects fixed
since. `CorpusReparser` runs every name through the import's parse again, name and authorship separately, and rewrites
the parsed author columns. A name the export script would not export with today's parse is dropped. It writes
`reparse-stats.txt` with the parser version and, per dataset, the rows read, changed and dropped, and names the parser
in a `.parser` file next to the re-parsed export. The miner passes that file on to the pairs, and the report header
prints it as `corpus parsed by`, or `the imports, not re-parsed` for pairs mined from a raw export. Mine the re-parsed
export, never the raw one: two reports are only comparable when their corpus was parsed by the same parser.

`-pl dao` takes the `parser` and `api` modules from `~/.m2` and not from the checkout, so install first - here and
before every report - or the re-parse runs an old `NameParser` and the report an old `AuthorshipNormalizer` and
author map:

    mvn -q -pl dao -am install -DskipTests
    mvn -q -pl dao test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
      -Dexec.args="-Xmx4g --enable-native-access=ALL-UNNAMED -cp %classpath life.catalogue.matching.authorship.corpus.CorpusReparser /path/to/author-corpus.tsv.gz target/author-corpus/reparsed/author-corpus.tsv.gz"

**3. Mine** the labelled pairs, which takes about a minute. It prints how many pairs got which label and writes
the same to `miner-stats.txt`:

    mvn -q -pl dao test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
      -Dexec.args="-Xmx4g -cp %classpath life.catalogue.matching.authorship.corpus.AuthorPairMiner target/author-corpus/reparsed/author-corpus.tsv.gz target/author-corpus/pairs.tsv.gz"

**4. Report.** Install first, as for the re-parse, or the report measures the old code. Its header says how many rows
the author map had and which parser the corpus was parsed by.

    mvn -q -pl dao -am install -DskipTests
    mvn -q -pl dao test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
      -Dexec.args="-Xmx4g -cp %classpath life.catalogue.matching.authorship.corpus.AuthorCorpusReport target/author-corpus/pairs.tsv.gz target/author-corpus"

`dao/target/author-corpus/report.txt` holds:
- the confusion matrix, label against verdict, per pair and weighted by the number of names behind a pair, for all
  pairs and per nomenclatural code. Right is `SAME` = `EQUAL` and `DIFF` = `DIFFERENT`;
- the same without years. The labels speak about authors, and this is the author logic alone: for `DIFF` it says
  how many true homonyms the authors do not tell apart once the years are gone;
- the worklists, each sorted by the number of names affected: same acts judged `DIFFERENT` by their authors or by
  their years only, different names judged `EQUAL`, same acts judged `UNKNOWN`, alias candidates the author map
  lacks, and the dubious pairs - judged `EQUAL` those are mostly duplicate records in the dataset they come from;
- different names per dataset. A dataset far above the others holds duplicates rather than homonyms.

**The person matcher** is measured with `PersonCorpusReport` in `core` test scope, on the same pairs and against the
string verdicts of step 4. It writes the report of step 4 twice, once per relatives policy, into `unknown/` and
`different/` of its output directory, each with a `diff.txt` against the string verdicts. The report adds these
sections:
- what decided the verdicts: the rules every name pair needed and the verdict;
- the share of citations resolved to a person;
- the citations no person resolves, ranked by names: the worklist of missing persons and forms;
- every pair the relatives policy decided;
- the run time.

Its header names the registry size, its load time and the heap in use after loading, and the margins. Install dao
first, as `core` takes its test classes from `~/.m2`:

    mvn -q -pl dao -am install -DskipTests
    cd core
    mvn -q test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
      -Dexec.args="-Xmx6g -cp %classpath life.catalogue.matching.person.PersonCorpusReport ../dao/target/author-corpus/pairs.tsv.gz ../dao/target/author-corpus/verdicts.tsv.gz target/person-corpus [--min-age N] [--posthumous N] [--active-slack N]"

`RelativesFixtureTest` pins both matchers on `core/src/test/resources/author-corpus/relatives-pairs.tsv`, 51 hand
curated pairs: 30 pairs of relatives - Hookers, Reichenbachs, de Candolles, Linnaei, Presl, Nees, Sowerbys, Adams,
Sars and Geoffroys - each a name of one against a name of the other, from IPNI, WoRMS and ITIS, and 21 pairs citing one
of them two ways, from the corpus. Its `main` prints every verdict of all three.

**What the author map is worth** shows in a second report without it, diffed against the first. `fixed` is then
what the map gets right and `regressed` what it breaks:

    mvn -q -pl dao test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
      -Dexec.args="-Xmx4g -cp %classpath life.catalogue.matching.authorship.corpus.AuthorCorpusReport target/author-corpus/pairs.tsv.gz target/author-corpus/nomap nomap"
    mvn -q -pl dao test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
      -Dexec.args="-cp %classpath life.catalogue.matching.authorship.corpus.AuthorVerdictDiff target/author-corpus/nomap/verdicts.tsv.gz target/author-corpus/verdicts.tsv.gz"

## Changing the author comparison

1. Keep the verdicts of master: `cp dao/target/author-corpus/verdicts.tsv.gz dao/target/author-corpus/verdicts-before.tsv.gz`
2. Change the code, install, run the report again.
3. Look at every verdict that flipped, grouped into fixed, regressed and changed:

       mvn -q -pl dao test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
         -Dexec.args="-cp %classpath life.catalogue.matching.authorship.corpus.AuthorVerdictDiff target/author-corpus/verdicts-before.tsv.gz target/author-corpus/verdicts.tsv.gz"

4. `AuthorCorpusTest` pins what master did on the committed sample, with 5% headroom in the wrong direction.
   A change for the better moves the numbers the right way and the test keeps passing - set the new numbers
   anyway, so the next change is held to them. It prints the matrices to
   `dao/target/surefire-reports/life.catalogue.matching.authorship.corpus.AuthorCorpusTest-output.txt`.

`CorpusEvaluatorTest.knownMisjudgements` is a characterisation of misjudgements found by hand. Fixing one of them
fails it, which is the point: update its numbers.

## Refreshing the sample

Only with a new export or a new parser, after a re-parse. The sample takes, per label and code, the 1000 pairs with most names behind them plus
about 2000 of the rest selected by a hash, never by what the comparator says about them:

    mvn -q -pl dao test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
      -Dexec.args="-Xmx4g -cp %classpath life.catalogue.matching.authorship.corpus.AuthorPairSampler target/author-corpus/pairs.tsv.gz src/test/resources/author-corpus/author-pairs-sample.tsv.gz"

Then set the row count and the pins in `AuthorCorpusTest` - run it once, the failure message holds the numbers.

## Files

Tab delimited with a header that is verified on reading, gzipped if the name ends in `.gz`.

| file | columns |
|---|---|
| export | `index_id dataset_key name_id rank code nom_status scientific_name authorship combination_authors combination_ex_authors combination_year basionym_authors basionym_ex_authors basionym_year sanctioning_author classification`, the authors of a team and the higher taxa, root first, separated by a pipe, sorted by `index_id, rank` |
| pairs | `label source weight support yearAgree yearConflict freqA freqB intraYearDiff intraNoYear intraYearAgree code rank nidx scientificName keyA keyB`, then `datasetKey nameId authorship combAuthors combEx combYear basAuthors basEx basYear sanctioning` once with suffix `A` and once with `B`, then `group`. One pair of names that shows the pair of keys, side A being the key that sorts first. `group` is the taxonomic group both names belong to, derived from their classification (`AuthorPair.commonGroup`: the broader of two nested groups), empty when unknown or disparate; a pairs file without it still reads |
| verdicts | `code keyA keyB label source weight verdict verdictNoYear authorshipA authorshipB`, a pair being identified by the first three |
| `<file>.parser` | one line naming the parser the authors of `<file>` were parsed with, next to a re-parsed export and the pairs mined from it |

`weight` is the number of names backing a label. All numbers count names, never datasets: IPNI, WCVP and WFO
share a lineage and would otherwise vote three times.
