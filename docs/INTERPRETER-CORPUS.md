# Measuring name interpretation with a corpus

The name interpretation of an import - `NameInterpreter` with the `NameParser` wrapper around the rust name parser -
turns a verbatim record into a `Name`. Whether a change to it, or a new name parser build, makes names better or
worse is measured by running it over the names ChecklistBank holds and comparing two runs row by row. Why it is built
the way it is, is in [2026-10-09-interpreter-corpus.md](2026-10-09-interpreter-corpus.md).

All of it is test scope code in `importer/src/test/java/life/catalogue/importer/corpus/`. None of it runs in the build
except the tests on the committed fixtures and `InterpreterCorpusExportIT`.

```
clb-verbatim-names.tsv ─┐
                        ├─InterpreterCorpusRunner─▶ <run>/interpreted.tsv.gz ─┐
interpreter-corpus-     │                          (one per code version)    ├─InterpreterCorpusDiff─▶ report.txt
  export.sql ──▶ export ┘                                                     ┘                         changes.tsv.gz
 (run by hand)
```

## Inputs

The runner reads two kinds of input and tells them apart by their header.

| input | columns | what it exercises |
|---|---|---|
| the name parser corpus, `name-parser-rust/testdata/clb-verbatim-names.tsv` | `rowType, scientificName, authorship, rank, code` | names given as one string or as name plus authorship. 67.5 million verbatim rows of DwC and ColDP records, no atoms, no dataset |
| the export of `interpreter-corpus-export.sql` | `dataset_key, type, terms, n` | everything the importers read: atoms, author atoms, notho, original spelling, name status, published in year, and the name settings of each dataset |

ColDP prefers name atoms over the scientific name by default, and author atoms override the authorship string, so only
the export exercises how most ColDP records are actually interpreted. ACEF is in neither: its infraspecific records
take their species from another record.

## Running it

Every tool runs in a forked JVM with the test classpath, so paths are **relative to the `importer` module**. Install
first, here and after every change of `api`, `parser`, `core` or `dao`, or `-pl importer` runs the old jars from
`~/.m2`:

    mvn -q -pl importer -am install -DskipTests

**1. Export** (optional, for the atomised names). Read only and safe on a standby. `keys` restricts it to some
datasets, `sample` takes a repeatable percentage of the verbatim records:

    psql -X -q -v ON_ERROR_STOP=1 [-v keys=1010,2041] [-v sample=5] \
      -f importer/src/test/resources/interpreter-corpus/interpreter-corpus-export.sql "<conninfo>" | gzip > interpreter-corpus.tsv.gz

It writes the name settings of the datasets first, as rows of type `dataset`, then the name terms of the DwC and
ColDP records, with identical terms of one dataset and row type collapsed into one row that counts them in `n`.
`InterpreterCorpusExportIT` runs its statements against the test schema, so a renamed column fails there and not on
prod.

**2. Run** the interpretation, once per code version to compare:

    mvn -q -pl importer test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
      -Dexec.args="-Xmx4g --enable-native-access=ALL-UNNAMED -cp %classpath life.catalogue.importer.corpus.InterpreterCorpusRunner \
        /path/to/clb-verbatim-names.tsv target/interpreter-corpus/baseline [--fraction 0.01] [--seed 42] [--threads 8] [--limit 1000]"

`--fraction` keeps a share of the rows chosen by their line number and the seed, so two runs over the same input
always read the same rows. A 1% sample of the parser corpus takes half a minute on 14 threads, the whole of it about
45 minutes. The run writes `interpreted.tsv.gz`, one row per record in input order, and `meta.txt` with the input,
the sample, the git commit (and whether the working tree had changes) and the path of the name parser jar it ran with -
set `-Dcorpus.code=...` instead to describe a run built from a copy of the sources outside git. A record
the interpreter throws on is a row with status `ERROR` and the exception, not the end of the run.

**3. Diff** two runs over the same input:

    mvn -q -pl importer test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
      -Dexec.args="-cp %classpath life.catalogue.importer.corpus.InterpreterCorpusDiff \
        target/interpreter-corpus/baseline target/interpreter-corpus/after target/interpreter-corpus/diff [--examples 20]"

## Reading the report

`report.txt` counts every change by field, in rows and in records (a row of the export stands for `n` records):

- the **text** fields - label, scientific name, authorship - and the notes - unparsed rest, nomenclatural and
  taxonomic note, published in - by the most lenient comparison that still tells them apart: `WHITESPACE`,
  `PUNCTUATION`, `CASE`, `DIACRITICS`, or a value `ADDED`, `REMOVED` or its letters and digits changed, `TEXT`.
  A `TEXT` change carries its likely cause: `separator` (and, et, und, y), `et-al`, `initials`, `year`,
  `rank-marker`, `in-citation`, `ex-author`, `note`, `order`, `abbreviation`, `tail-`/`head-dropped`/`-added`,
  `words-dropped`/`-added`, or `other`;
- the **parsed properties** - rank, code, name type, the six atoms, notho, candidatus, original spelling, nomenclatural
  status, extinct, doubtful - and the **authorship atoms** whenever they differ;
- the **status** of a row, `OK`, `NONE` (no name) or `ERROR`;
- **issues** one by one, added or removed.

A row changed **significantly** when a parsed property, an authorship atom or the status changed, or a text differs in
more than whitespace, punctuation, case or diacritics. Issue changes alone do not count. The report lists the datasets
with the most significantly changed records, and for every kind of change up to `--examples` examples: the same hash
sample in every run, with the input, the value before and after and the label. `changes.tsv.gz` holds every changed
field of every row, for drilling down.

## Changing the interpretation

1. Run the baseline on the code before the change and keep its directory.
2. Change the code, install, run again into a new directory.
3. Diff, and read every bucket of significant changes: an intended change, a fix, or a regression - and if a parser
   defect, an issue for gbif/name-parser-rust rather than a workaround.
4. `InterpreterCorpusTest` pins the outcome of the committed fixtures, which hold a few records of every input flavour.
