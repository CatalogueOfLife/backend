# Measuring author comparison with a corpus

`AuthorComparator` decides whether two authorships are the same. Whether a change to it, to `AuthorshipNormalizer`
or to the author map ([AUTHORMAP-GENERATOR.md](AUTHORMAP-GENERATOR.md)) makes matching better or worse is measured
against a corpus of labelled authorship pairs mined from ChecklistBank itself. Why it is built the way it is, is in
[2026-09-19-author-comparison-corpus.md](2026-09-19-author-comparison-corpus.md).

All of it is test scope code in `dao/src/test/java/life/catalogue/matching/authorship/corpus/`. None of it runs
in the build except the fast guard test on a committed sample.

```
author-corpus-export.sql ──▶ author-corpus.tsv.gz ──AuthorPairMiner──▶ pairs.tsv.gz ──AuthorCorpusReport──▶ report.txt
      (run by hand)            (~200 MB, not in git)                                                     └─▶ verdicts.tsv.gz ──AuthorVerdictDiff
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

The corpus of September 2026 was exported from prod with
`2006,2004,2232,310868,1141,1028,2073,304756,2003,2015,2037,2011,2007,2144,2026,1174,2008,2041,2030,312616`:
12.4 million names of 3.1 million names index ids. `AuthorCorpusExportIT` runs the statement against the test
schema, so a renamed column fails there and not on prod.

**2. Mine** the labelled pairs, which takes about a minute. It prints how many pairs got which label and writes
the same to `miner-stats.txt`:

    mvn -q -pl dao test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
      -Dexec.args="-Xmx4g -cp %classpath life.catalogue.matching.authorship.corpus.AuthorPairMiner /path/to/author-corpus.tsv.gz target/author-corpus/pairs.tsv.gz"

**3. Report.** `-pl dao` takes the `api` module, and with it `AuthorshipNormalizer` and the author map, from
`~/.m2` and not from the checkout - install first or the report measures the old code. Its header says how
many rows the author map had.

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

Only with a new export. The sample takes, per label and code, the 1000 pairs with most names behind them plus
about 2000 of the rest selected by a hash, never by what the comparator says about them:

    mvn -q -pl dao test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
      -Dexec.args="-Xmx4g -cp %classpath life.catalogue.matching.authorship.corpus.AuthorPairSampler target/author-corpus/pairs.tsv.gz src/test/resources/author-corpus/author-pairs-sample.tsv.gz"

Then set the row count and the pins in `AuthorCorpusTest` - run it once, the failure message holds the numbers.

## Files

Tab delimited with a header that is verified on reading, gzipped if the name ends in `.gz`.

| file | columns |
|---|---|
| export | `index_id dataset_key name_id rank code nom_status scientific_name authorship combination_authors combination_ex_authors combination_year basionym_authors basionym_ex_authors basionym_year sanctioning_author`, the authors of a team separated by a pipe, sorted by `index_id, rank` |
| pairs | `label source weight support yearAgree yearConflict freqA freqB intraYearDiff intraNoYear intraYearAgree code rank nidx scientificName keyA keyB`, then `datasetKey nameId authorship combAuthors combEx combYear basAuthors basEx basYear sanctioning` once with suffix `A` and once with `B`. One pair of names that shows the pair of keys, side A being the key that sorts first |
| verdicts | `code keyA keyB label source weight verdict verdictNoYear authorshipA authorshipB`, a pair being identified by the first three |

`weight` is the number of names backing a label. All numbers count names, never datasets: IPNI, WCVP and WFO
share a lineage and would otherwise vote three times.
